/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.baidu.titan.dexlib.dex;

import com.baidu.titan.dexlib.dex.util.ByteInput;
import com.baidu.titan.dexlib.dex.util.ByteOutput;
import com.baidu.titan.dexlib.dex.util.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The bytes of a dex file in memory for reading and writing. All int offsets
 * are unsigned.
 */
public final class Dex {
    private static final int CHECKSUM_OFFSET = 8;
    private static final int CHECKSUM_SIZE = 4;
    private static final int SIGNATURE_OFFSET = CHECKSUM_OFFSET + CHECKSUM_SIZE;
    private static final int SIGNATURE_SIZE = 20;
    // Provided as a convenience to avoid a memory allocation to benefit Dalvik.
    // Note: libcore.util.EmptyArray cannot be accessed when this code isn't run on Dalvik.
    static final short[] EMPTY_SHORT_ARRAY = new short[0];

    private ByteBuffer data;
    private final TableOfContents tableOfContents = new TableOfContents();
    private int nextSectionStart = 0;
    private final StringTable strings = new StringTable();
    private final TypeIndexToDescriptorIndexTable typeIds = new TypeIndexToDescriptorIndexTable();
    private final TypeIndexToDescriptorTable typeNames = new TypeIndexToDescriptorTable();
    private final ProtoIdTable protoIds = new ProtoIdTable();
    private final FieldIdTable fieldIds = new FieldIdTable();
    private final MethodIdTable methodIds = new MethodIdTable();

    /**
     * Creates a new dex that reads from {@code data}. It is an error to modify
     * {@code data} after using it to create a dex buffer.
     */
    public Dex(byte[] data) throws IOException {
        this(ByteBuffer.wrap(data));
    }

    private Dex(ByteBuffer data) throws IOException {
        this.data = data;
        this.data.order(ByteOrder.LITTLE_ENDIAN);
        this.tableOfContents.readFrom(this);
    }

    /**
     * Creates a new empty dex of the specified size.
     */
    public Dex(int byteCount) throws IOException {
        this.data = ByteBuffer.wrap(new byte[byteCount]);
        this.data.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Creates a new dex buffer of the dex in {@code in}, and closes {@code in}.
     */
    public Dex(InputStream in) throws IOException {
        loadFrom(in);
    }

    /**
     * Creates a new dex buffer from the dex file {@code file}.
     */
    public Dex(File file) throws IOException {
        if (FileUtils.hasArchiveSuffix(file.getName())) {
            try (ZipFile zipFile = new ZipFile(file)) {
                ZipEntry entry = zipFile.getEntry(DexFormat.DEX_IN_JAR_NAME);
                if (entry != null) {
                    loadFrom(zipFile.getInputStream(entry));
                } else {
                    throw new DexException("Expected " + DexFormat.DEX_IN_JAR_NAME + " in " + file);
                }
            }
        } else if (file.getName().endsWith(".dex")) {
            try (InputStream in = new FileInputStream(file)) {
                loadFrom(in);
            }
        } else {
            throw new DexException("unknown output extension: " + file);
        }
    }

    /**
     * Creates a new dex from the contents of {@code bytes}. This API supports
     * both {@code .dex} and {@code .odex} input. Calling this constructor
     * transfers ownership of {@code bytes} to the returned Dex: it is an error
     * to access the buffer after calling this method.
     */
    public static Dex create(ByteBuffer data) throws IOException {
        data.order(ByteOrder.LITTLE_ENDIAN);

        // if it's an .odex file, set position and limit to the .dex section
        if (data.get(0) == 'd'
                && data.get(1) == 'e'
                && data.get(2) == 'y'
                && data.get(3) == '\n') {
            data.position(8);
            int offset = data.getInt();
            int length = data.getInt();
            data.position(offset);
            data.limit(offset + length);
            data = data.slice();
        }

        return new Dex(data);
    }

    private void loadFrom(InputStream in) throws IOException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        int count;
        while ((count = in.read(buffer)) != -1) {
            bytesOut.write(buffer, 0, count);
        }

        this.data = ByteBuffer.wrap(bytesOut.toByteArray());
        this.data.order(ByteOrder.LITTLE_ENDIAN);
        this.tableOfContents.readFrom(this);
    }

    private static void checkBounds(int index, int length) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("index:" + index + ", length=" + length);
        }
    }

    public void writeTo(OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        ByteBuffer data = this.data.duplicate(); // positioned ByteBuffers aren't thread safe
        data.clear();
        while (data.hasRemaining()) {
            int count = Math.min(buffer.length, data.remaining());
            data.get(buffer, 0, count);
            out.write(buffer, 0, count);
        }
    }

    public void writeTo(File dexOut) throws IOException {
        OutputStream out = new FileOutputStream(dexOut);
        writeTo(out);
        out.close();
    }

    public TableOfContents getTableOfContents() {
        return tableOfContents;
    }

    public Section open(int position) {
        if (position < 0 || position >= data.capacity()) {
            throw new IllegalArgumentException("position=" + position
                    + " length=" + data.capacity());
        }
        ByteBuffer sectionData = data.duplicate();
        sectionData.order(ByteOrder.LITTLE_ENDIAN); // necessary?
        sectionData.position(position);
        sectionData.limit(data.capacity());
        return new Section("section", sectionData);
    }

    public Section appendSection(int maxByteCount, String name) {
        if ((maxByteCount & 3) != 0) {
            throw new IllegalStateException("Not four byte aligned!");
        }
        int limit = nextSectionStart + maxByteCount;
        ByteBuffer sectionData = data.duplicate();
        sectionData.order(ByteOrder.LITTLE_ENDIAN); // necessary?
        sectionData.position(nextSectionStart);
        sectionData.limit(limit);
        Section result = new Section(name, sectionData);
        nextSectionStart = limit;
        return result;
    }

    public int getLength() {
        return data.capacity();
    }

    public int getNextSectionStart() {
        return nextSectionStart;
    }

    /**
     * Returns a copy of the the bytes of this dex.
     */
    public byte[] getBytes() {
        ByteBuffer data = this.data.duplicate(); // positioned ByteBuffers aren't thread safe
        byte[] result = new byte[data.capacity()];
        data.position(0);
        data.get(result);
        return result;
    }

    public List<String> strings() {
        return strings;
    }

    public List<Integer> typeIds() {
        return typeIds;
    }

    public List<String> typeNames() {
        return typeNames;
    }

    public List<ProtoId> protoIds() {
        return protoIds;
    }

    public List<FieldId> fieldIds() {
        return fieldIds;
    }

    public List<MethodId> methodIds() {
        return methodIds;
    }

    public Iterable<ClassDef> classDefs() {
        return new ClassDefIterable();
    }

    public TypeList readTypeList(int offset) {
        if (offset == 0) {
            return TypeList.EMPTY;
        }
        return open(offset).readTypeList();
    }

    public ClassData readClassData(ClassDef classDef) {
        int offset = classDef.getClassDataOffset();
        if (offset == 0) {
            throw new IllegalArgumentException("offset == 0");
        }
        return open(offset).readClassData();
    }

    public Code readCode(ClassData.Method method) {
        int offset = method.getCodeOffset();
        if (offset == 0) {
            throw new IllegalArgumentException("offset == 0");
        }
        return open(offset).readCode();
    }

    /**
     * Returns the signature of all but the first 32 bytes of this dex. The
     * first 32 bytes of dex files are not specified to be included in the
     * signature.
     */
    public byte[] computeSignature() throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError();
        }
        byte[] buffer = new byte[8192];
        ByteBuffer data = this.data.duplicate(); // positioned ByteBuffers aren't thread safe
        data.limit(data.capacity());
        data.position(SIGNATURE_OFFSET + SIGNATURE_SIZE);
        while (data.hasRemaining()) {
            int count = Math.min(buffer.length, data.remaining());
            data.get(buffer, 0, count);
            digest.update(buffer, 0, count);
        }
        return digest.digest();
    }

    /**
     * Returns the checksum of all but the first 12 bytes of {@code dex}.
     */
    public int computeChecksum() throws IOException {
        Adler32 adler32 = new Adler32();
        byte[] buffer = new byte[8192];
        ByteBuffer data = this.data.duplicate(); // positioned ByteBuffers aren't thread safe
        data.limit(data.capacity());
        data.position(CHECKSUM_OFFSET + CHECKSUM_SIZE);
        while (data.hasRemaining()) {
            int count = Math.min(buffer.length, data.remaining());
            data.get(buffer, 0, count);
            adler32.update(buffer, 0, count);
        }
        return (int) adler32.getValue();
    }

    /**
     * Generates the signature and checksum of the dex file {@code out} and
     * writes them to the file.
     */
    public void writeHashes() throws IOException {
        open(SIGNATURE_OFFSET).write(computeSignature());
        open(CHECKSUM_OFFSET).writeInt(computeChecksum());
    }

    /**
     * Look up a field id name index from a field index. Cheaper than:
     * {@code fieldIds().get(fieldDexIndex).getNameIndex();}
     */
    public int nameIndexFromFieldIndex(int fieldIndex) {
        checkBounds(fieldIndex, tableOfContents.fieldIds.size);
        int position = tableOfContents.fieldIds.off + (SizeOf.MEMBER_ID_ITEM * fieldIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        position += SizeOf.USHORT;  // typeIndex
        return data.getInt(position);  // nameIndex
    }

    public int findStringIndex(String s) {
        return Collections.binarySearch(strings, s);
    }

    public int findTypeIndex(String descriptor) {
        return Collections.binarySearch(typeNames, descriptor);
    }

    public int findFieldIndex(FieldId fieldId) {
        return Collections.binarySearch(fieldIds, fieldId);
    }

    public int findMethodIndex(MethodId methodId) {
        return Collections.binarySearch(methodIds, methodId);
    }

    public int findClassDefIndexFromTypeIndex(int typeIndex) {
        checkBounds(typeIndex, tableOfContents.typeIds.size);
        if (!tableOfContents.classDefs.exists()) {
            return -1;
        }
        for (int i = 0; i < tableOfContents.classDefs.size; i++) {
            if (typeIndexFromClassDefIndex(i) == typeIndex) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Look up a field id type index from a field index. Cheaper than:
     * {@code fieldIds().get(fieldDexIndex).getTypeIndex();}
     */
    public int typeIndexFromFieldIndex(int fieldIndex) {
        checkBounds(fieldIndex, tableOfContents.fieldIds.size);
        int position = tableOfContents.fieldIds.off + (SizeOf.MEMBER_ID_ITEM * fieldIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        return data.getShort(position) & 0xFFFF;  // typeIndex
    }

    /**
     * Look up a method id declaring class index from a method index. Cheaper than:
     * {@code methodIds().get(methodIndex).getDeclaringClassIndex();}
     */
    public int declaringClassIndexFromMethodIndex(int methodIndex) {
        checkBounds(methodIndex, tableOfContents.methodIds.size);
        int position = tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * methodIndex);
        return data.getShort(position) & 0xFFFF;  // declaringClassIndex
    }

    /**
     * Look up a method id name index from a method index. Cheaper than:
     * {@code methodIds().get(methodIndex).getNameIndex();}
     */
    public int nameIndexFromMethodIndex(int methodIndex) {
        checkBounds(methodIndex, tableOfContents.methodIds.size);
        int position = tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * methodIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        position += SizeOf.USHORT;  // protoIndex
        return data.getInt(position);  // nameIndex
    }

    /**
     * Look up a parameter type ids from a method index. Cheaper than:
     * {@code readTypeList(protoIds.get(methodIds().get(methodDexIndex).getProtoIndex()).getParametersOffset()).getTypes();}
     */
    public short[] parameterTypeIndicesFromMethodIndex(int methodIndex) {
        checkBounds(methodIndex, tableOfContents.methodIds.size);
        int position = tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * methodIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        int protoIndex = data.getShort(position) & 0xFFFF;
        checkBounds(protoIndex, tableOfContents.protoIds.size);
        position = tableOfContents.protoIds.off + (SizeOf.PROTO_ID_ITEM * protoIndex);
        position += SizeOf.UINT;  // shortyIndex
        position += SizeOf.UINT;  // returnTypeIndex
        int parametersOffset = data.getInt(position);
        if (parametersOffset == 0) {
            return EMPTY_SHORT_ARRAY;
        }
        position = parametersOffset;
        int size = data.getInt(position);
        if (size <= 0) {
            throw new AssertionError("Unexpected parameter type list size: " + size);
        }
        position += SizeOf.UINT;
        short[] types = new short[size];
        for (int i = 0; i < size; i++) {
            types[i] = data.getShort(position);
            position += SizeOf.USHORT;
        }
        return types;
    }

    /**
     * Look up a method id return type index from a method index. Cheaper than:
     * {@code protoIds().get(methodIds().get(methodDexIndex).getProtoIndex()).getReturnTypeIndex();}
     */
    public int returnTypeIndexFromMethodIndex(int methodIndex) {
        checkBounds(methodIndex, tableOfContents.methodIds.size);
        int position = tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * methodIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        int protoIndex = data.getShort(position) & 0xFFFF;
        checkBounds(protoIndex, tableOfContents.protoIds.size);
        position = tableOfContents.protoIds.off + (SizeOf.PROTO_ID_ITEM * protoIndex);
        position += SizeOf.UINT;  // shortyIndex
        return data.getInt(position);  // returnTypeIndex
    }

    /**
     * Look up a descriptor index from a type index. Cheaper than:
     * {@code open(tableOfContents.typeIds.off + (index * SizeOf.TYPE_ID_ITEM)).readInt();}
     */
    public int descriptorIndexFromTypeIndex(int typeIndex) {
       checkBounds(typeIndex, tableOfContents.typeIds.size);
       int position = tableOfContents.typeIds.off + (SizeOf.TYPE_ID_ITEM * typeIndex);
       return data.getInt(position);
    }

    /**
     * Look up a type index index from a class def index.
     */
    public int typeIndexFromClassDefIndex(int classDefIndex) {
        checkBounds(classDefIndex, tableOfContents.classDefs.size);
        int position = tableOfContents.classDefs.off + (SizeOf.CLASS_DEF_ITEM * classDefIndex);
        return data.getInt(position);
    }

    /**
     * Look up an annotation directory offset from a class def index.
     */
    public int annotationDirectoryOffsetFromClassDefIndex(int classDefIndex) {
        checkBounds(classDefIndex, tableOfContents.classDefs.size);
        int position = tableOfContents.classDefs.off + (SizeOf.CLASS_DEF_ITEM * classDefIndex);
        position += SizeOf.UINT;  // type
        position += SizeOf.UINT;  // accessFlags
        position += SizeOf.UINT;  // superType
        position += SizeOf.UINT;  // interfacesOffset
        position += SizeOf.UINT;  // sourceFileIndex
        return data.getInt(position);
    }

    /**
     * Look up interface types indices from a  return type index from a method index. Cheaper than:
     * {@code ...getClassDef(classDefIndex).getInterfaces();}
     */
    public short[] interfaceTypeIndicesFromClassDefIndex(int classDefIndex) {
        checkBounds(classDefIndex, tableOfContents.classDefs.size);
        int position = tableOfContents.classDefs.off + (SizeOf.CLASS_DEF_ITEM * classDefIndex);
        position += SizeOf.UINT;  // type
        position += SizeOf.UINT;  // accessFlags
        position += SizeOf.UINT;  // superType
        int interfacesOffset = data.getInt(position);
        if (interfacesOffset == 0) {
            return EMPTY_SHORT_ARRAY;
        }
        position = interfacesOffset;
        int size = data.getInt(position);
        if (size <= 0) {
            throw new AssertionError("Unexpected interfaces list size: " + size);
        }
        position += SizeOf.UINT;
        short[] types = new short[size];
        for (int i = 0; i < size; i++) {
            types[i] = data.getShort(position);
            position += SizeOf.USHORT;
        }
        return types;
    }

    public final class Section implements ByteInput, ByteOutput {
        private final String name;
        private final ByteBuffer data;
        private final int initialPosition;

        private Section(String name, ByteBuffer data) {
            this.name = name;
            this.data = data;
            this.initialPosition = data.position();
        }

        public int getPosition() {
            return data.position();
        }

        public int readInt() {
            return data.getInt();
        }

        public short readShort() {
            return data.getShort();
        }

        public int readUnsignedShort() {
            return readShort() & 0xffff;
        }

        public byte readByte() {
            return data.get();
        }

        public byte[] readByteArray(int length) {
            byte[] result = new byte[length];
            data.get(result);
            return result;
        }

        public short[] readShortArray(int length) {
            if (length == 0) {
                return EMPTY_SHORT_ARRAY;
            }
            short[] result = new short[length];
            for (int i = 0; i < length; i++) {
                result[i] = readShort();
            }
            return result;
        }

        public int[] readIntArray(int length) {
            if (length == 0) {
                return new int[0];
            }
            int[] result = new int[length];
            for (int i = 0; i < length; i++) {
                result[i] = readInt();
            }
            return result;
        }

        public int readUleb128() {
            return Leb128.readUnsignedLeb128(this);
        }

        public int readUleb128p1() {
            return Leb128.readUnsignedLeb128(this) - 1;
        }

        public int readSleb128() {
            return Leb128.readSignedLeb128(this);
        }

        public void writeUleb128p1(int i) {
            writeUleb128(i + 1);
        }

        public TypeList readTypeList() {
            int size = readInt();
            short[] types = readShortArray(size);
            alignToFourBytes();
            return new TypeList(Dex.this, types);
        }

        public String readString() {
            int offset = readInt();
            int savedPosition = data.position();
            int savedLimit = data.limit();
            data.position(offset);
            data.limit(data.capacity());
            try {
                int expectedLength = readUleb128();
                String result = Mutf8.decode(this, new char[expectedLength]);
                if (result.length() != expectedLength) {
                    throw new DexException("Declared length " + expectedLength
                            + " doesn't match decoded length of " + result.length());
                }
                return result;
            } catch (UTFDataFormatException e) {
                throw new DexException(e);
            } finally {
                data.position(savedPosition);
                data.limit(savedLimit);
            }
        }

        public FieldId readFieldId() {
            int declaringClassIndex = readUnsignedShort();
            int typeIndex = readUnsignedShort();
            int nameIndex = readInt();
            return new FieldId(Dex.this, declaringClassIndex, typeIndex, nameIndex);
        }

        public MethodId readMethodId() {
            int declaringClassIndex = readUnsignedShort();
            int protoIndex = readUnsignedShort();
            int nameIndex = readInt();
            return new MethodId(Dex.this, declaringClassIndex, protoIndex, nameIndex);
        }

        public ProtoId readProtoId() {
            int shortyIndex = readInt();
            int returnTypeIndex = readInt();
            int parametersOffset = readInt();
            return new ProtoId(Dex.this, shortyIndex, returnTypeIndex, parametersOffset);
        }

        public ClassDef readClassDef() {
            int offset = getPosition();
            int type = readInt();
            int accessFlags = readInt();
            int supertype = readInt();
            int interfacesOffset = readInt();
            int sourceFileIndex = readInt();
            int annotationsOffset = readInt();
            int classDataOffset = readInt();
            int staticValuesOffset = readInt();
            return new ClassDef(Dex.this, offset, type, accessFlags, supertype,
                    interfacesOffset, sourceFileIndex, annotationsOffset, classDataOffset,
                    staticValuesOffset);
        }

        private Code readCode() {
            int registersSize = readUnsignedShort();
            int insSize = readUnsignedShort();
            int outsSize = readUnsignedShort();
            int triesSize = readUnsignedShort();
            int debugInfoOffset = readInt();
            int instructionsSize = readInt();
            short[] instructions = readShortArray(instructionsSize);
            Code.Try[] tries;
            Code.CatchHandler[] catchHandlers;
            if (triesSize > 0) {
                if (instructions.length % 2 == 1) {
                    readShort(); // padding
                }

                /*
                 * We can't read the tries until we've read the catch handlers.
                 * Unfortunately they're in the opposite order in the dex file
                 * so we need to read them out-of-order.
                 */
                Section triesSection = open(data.position());
                skip(triesSize * SizeOf.TRY_ITEM);
                catchHandlers = readCatchHandlers();
                tries = triesSection.readTries(triesSize, catchHandlers);
            } else {
                tries = new Code.Try[0];
                catchHandlers = new Code.CatchHandler[0];
            }
            return new Code(registersSize, insSize, outsSize, debugInfoOffset, instructions,
                            tries, catchHandlers);
        }

        private Code.CatchHandler[] readCatchHandlers() {
            int baseOffset = data.position();
            int catchHandlersSize = readUleb128();
            Code.CatchHandler[] result = new Code.CatchHandler[catchHandlersSize];
            for (int i = 0; i < catchHandlersSize; i++) {
                int offset = data.position() - baseOffset;
                result[i] = readCatchHandler(offset);
            }
            return result;
        }

        private Code.Try[] readTries(int triesSize, Code.CatchHandler[] catchHandlers) {
            Code.Try[] result = new Code.Try[triesSize];
            for (int i = 0; i < triesSize; i++) {
                int startAddress = readInt();
                int instructionCount = readUnsignedShort();
                int handlerOffset = readUnsignedShort();
                int catchHandlerIndex = findCatchHandlerIndex(catchHandlers, handlerOffset);
                result[i] = new Code.Try(startAddress, instructionCount, catchHandlerIndex);
            }
            return result;
        }

        private int findCatchHandlerIndex(Code.CatchHandler[] catchHandlers, int offset) {
            for (int i = 0; i < catchHandlers.length; i++) {
                Code.CatchHandler catchHandler = catchHandlers[i];
                if (catchHandler.getOffset() == offset) {
                    return i;
                }
            }
            throw new IllegalArgumentException();
        }

        private Code.CatchHandler readCatchHandler(int offset) {
            int size = readSleb128();
            int handlersCount = Math.abs(size);
            int[] typeIndexes = new int[handlersCount];
            int[] addresses = new int[handlersCount];
            for (int i = 0; i < handlersCount; i++) {
                typeIndexes[i] = readUleb128();
                addresses[i] = readUleb128();
            }
            int catchAllAddress = size <= 0 ? readUleb128() : -1;
            return new Code.CatchHandler(typeIndexes, addresses, catchAllAddress, offset);
        }

        private ClassData readClassData() {
            int staticFieldsSize = readUleb128();
            int instanceFieldsSize = readUleb128();
            int directMethodsSize = readUleb128();
            int virtualMethodsSize = readUleb128();
            ClassData.Field[] staticFields = readFields(staticFieldsSize);
            ClassData.Field[] instanceFields = readFields(instanceFieldsSize);
            ClassData.Method[] directMethods = readMethods(directMethodsSize);
            ClassData.Method[] virtualMethods = readMethods(virtualMethodsSize);
            return new ClassData(staticFields, instanceFields, directMethods, virtualMethods);
        }

        private ClassData.Field[] readFields(int count) {
            ClassData.Field[] result = new ClassData.Field[count];
            int fieldIndex = 0;
            for (int i = 0; i < count; i++) {
                fieldIndex += readUleb128(); // field index diff
                int accessFlags = readUleb128();
                result[i] = new ClassData.Field(fieldIndex, accessFlags);
            }
            return result;
        }

        private ClassData.Method[] readMethods(int count) {
            ClassData.Method[] result = new ClassData.Method[count];
            int methodIndex = 0;
            for (int i = 0; i < count; i++) {
                methodIndex += readUleb128(); // method index diff
                int accessFlags = readUleb128();
                int codeOff = readUleb128();
                result[i] = new ClassData.Method(methodIndex, accessFlags, codeOff);
            }
            return result;
        }

        /**
         * Returns a byte array containing the bytes from {@code start} to this
         * section's current position.
         */
        private byte[] getBytesFrom(int start) {
            int end = data.position();
            byte[] result = new byte[end - start];
            data.position(start);
            data.get(result);
            return result;
        }

        public AnnotationsDirectoryItem readAnnotationsDirectoryItem() {
            return AnnotationsDirectoryItem.createFromSection(this);
        }

        public AnnotationSetItem readAnnotationSetItem() {
            int size = readInt();
            int[] entries = readIntArray(size);
            return new AnnotationSetItem(entries);
        }

        public Annotation readAnnotation() {
            byte visibility = readByte();
            int start = data.position();
            new EncodedValueReader(this, EncodedValueReader.ENCODED_ANNOTATION).skipValue();
            return new Annotation(Dex.this, visibility, new EncodedValue(getBytesFrom(start)));
        }

        public EncodedValue readEncodedArray() {
            int start = data.position();
            new EncodedValueReader(this, EncodedValueReader.ENCODED_ARRAY).skipValue();
            return new EncodedValue(getBytesFrom(start));
        }

        public void skip(int count) {
            if (count < 0) {
                throw new IllegalArgumentException();
            }
            data.position(data.position() + count);
        }

        /**
         * Skips bytes until the position is aligned to a multiple of 4.
         */
        public void alignToFourBytes() {
            data.position((data.position() + 3) & ~3);
        }

        /**
         * Writes 0x00 until the position is aligned to a multiple of 4.
         */
        public void alignToFourBytesWithZeroFill() {
            while ((data.position() & 3) != 0) {
                data.put((byte) 0);
            }
        }

        public void assertFourByteAligned() {
            if ((data.position() & 3) != 0) {
                throw new IllegalStateException("Not four byte aligned!");
            }
        }

        public void write(byte[] bytes) {
            this.data.put(bytes);
        }

        public void writeByte(int b) {
            data.put((byte) b);
        }

        public void writeShort(short i) {
            data.putShort(i);
        }

        public void writeUnsignedShort(int i) {
            short s = (short) i;
            if (i != (s & 0xffff)) {
                throw new IllegalArgumentException("Expected an unsigned short: " + i);
            }
            writeShort(s);
        }

        public void write(short[] shorts) {
            for (short s : shorts) {
                writeShort(s);
            }
        }

        public void writeInt(int i) {
            data.putInt(i);
        }

        public void writeUleb128(int i) {
            try {
                Leb128.writeUnsignedLeb128(this, i);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new DexException("Section limit " + data.limit() + " exceeded by " + name);
            }
        }

        public void writeSleb128(int i) {
            try {
                Leb128.writeSignedLeb128(this, i);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new DexException("Section limit " + data.limit() + " exceeded by " + name);
            }
        }

        public void writeStringData(String value) {
            try {
                int length = value.length();
                writeUleb128(length);
                write(Mutf8.encode(value));
                writeByte(0);
            } catch (UTFDataFormatException e) {
                throw new AssertionError();
            }
        }

        public void writeTypeList(TypeList typeList) {
            short[] types = typeList.getTypes();
            writeInt(types.length);
            for (short type : types) {
                writeShort(type);
            }
            alignToFourBytesWithZeroFill();
        }

        /**
         * Returns the number of bytes remaining in this section.
         */
        public int remaining() {
            return data.remaining();
        }

        /**
         * Returns the number of bytes used by this section.
         */
        public int used() {
            return data.position() - initialPosition;
        }
    }

    private final class StringTable extends AbstractList<String> implements RandomAccess {
        @Override public String get(int index) {
            checkBounds(index, tableOfContents.stringIds.size);
            return open(tableOfContents.stringIds.off + (index * SizeOf.STRING_ID_ITEM))
                    .readString();
        }
        @Override public int size() {
            return tableOfContents.stringIds.size;
        }
    }

    private final class TypeIndexToDescriptorIndexTable extends AbstractList<Integer>
            implements RandomAccess {
        @Override public Integer get(int index) {
            return descriptorIndexFromTypeIndex(index);
        }
        @Override public int size() {
            return tableOfContents.typeIds.size;
        }
    }

    private final class TypeIndexToDescriptorTable extends AbstractList<String>
            implements RandomAccess {
        @Override public String get(int index) {
            return strings.get(descriptorIndexFromTypeIndex(index));
        }
        @Override public int size() {
            return tableOfContents.typeIds.size;
        }
    }

    private final class ProtoIdTable extends AbstractList<ProtoId> implements RandomAccess {
        @Override public ProtoId get(int index) {
            checkBounds(index, tableOfContents.protoIds.size);
            return open(tableOfContents.protoIds.off + (SizeOf.PROTO_ID_ITEM * index))
                    .readProtoId();
        }
        @Override public int size() {
            return tableOfContents.protoIds.size;
        }
    }

    private final class FieldIdTable extends AbstractList<FieldId> implements RandomAccess {
        @Override public FieldId get(int index) {
            checkBounds(index, tableOfContents.fieldIds.size);
            return open(tableOfContents.fieldIds.off + (SizeOf.MEMBER_ID_ITEM * index))
                    .readFieldId();
        }
        @Override public int size() {
            return tableOfContents.fieldIds.size;
        }
    }

    private final class MethodIdTable extends AbstractList<MethodId> implements RandomAccess {
        @Override public MethodId get(int index) {
            checkBounds(index, tableOfContents.methodIds.size);
            return open(tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * index))
                    .readMethodId();
        }
        @Override public int size() {
            return tableOfContents.methodIds.size;
        }
    }

    private final class ClassDefIterator implements Iterator<ClassDef> {
        private final Dex.Section in = open(tableOfContents.classDefs.off);
        private int count = 0;

        @Override
        public boolean hasNext() {
            return count < tableOfContents.classDefs.size;
        }
        @Override
        public ClassDef next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            count++;
            return in.readClassDef();
        }
        @Override
            public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final class ClassDefIterable implements Iterable<ClassDef> {
        public Iterator<ClassDef> iterator() {
            return !tableOfContents.classDefs.exists()
               ? Collections.<ClassDef>emptySet().iterator()
               : new ClassDefIterator();
        }
    }
}
