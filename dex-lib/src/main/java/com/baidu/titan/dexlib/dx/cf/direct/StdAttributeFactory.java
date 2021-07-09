/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.baidu.titan.dexlib.dx.cf.direct;

import com.baidu.titan.dexlib.dx.cf.attrib.AttAnnotationDefault;
import com.baidu.titan.dexlib.dx.cf.attrib.AttCode;
import com.baidu.titan.dexlib.dx.cf.attrib.AttConstantValue;
import com.baidu.titan.dexlib.dx.cf.attrib.AttDeprecated;
import com.baidu.titan.dexlib.dx.cf.attrib.AttEnclosingMethod;
import com.baidu.titan.dexlib.dx.cf.attrib.AttExceptions;
import com.baidu.titan.dexlib.dx.cf.attrib.AttInnerClasses;
import com.baidu.titan.dexlib.dx.cf.attrib.AttLineNumberTable;
import com.baidu.titan.dexlib.dx.cf.attrib.AttLocalVariableTable;
import com.baidu.titan.dexlib.dx.cf.attrib.AttLocalVariableTypeTable;
import com.baidu.titan.dexlib.dx.cf.attrib.AttRuntimeInvisibleAnnotations;
import com.baidu.titan.dexlib.dx.cf.attrib.AttRuntimeInvisibleParameterAnnotations;
import com.baidu.titan.dexlib.dx.cf.attrib.AttRuntimeVisibleAnnotations;
import com.baidu.titan.dexlib.dx.cf.attrib.AttRuntimeVisibleParameterAnnotations;
import com.baidu.titan.dexlib.dx.cf.attrib.AttSignature;
import com.baidu.titan.dexlib.dx.cf.attrib.AttSourceFile;
import com.baidu.titan.dexlib.dx.cf.attrib.AttSynthetic;
import com.baidu.titan.dexlib.dx.cf.attrib.InnerClassList;
import com.baidu.titan.dexlib.dx.cf.code.ByteCatchList;
import com.baidu.titan.dexlib.dx.cf.code.BytecodeArray;
import com.baidu.titan.dexlib.dx.cf.code.LineNumberList;
import com.baidu.titan.dexlib.dx.cf.code.LocalVariableList;
import com.baidu.titan.dexlib.dx.cf.iface.Attribute;
import com.baidu.titan.dexlib.dx.cf.iface.ParseException;
import com.baidu.titan.dexlib.dx.cf.iface.ParseObserver;
import com.baidu.titan.dexlib.dx.cf.iface.StdAttributeList;
import com.baidu.titan.dexlib.dx.rop.annotation.AnnotationVisibility;
import com.baidu.titan.dexlib.dx.rop.annotation.Annotations;
import com.baidu.titan.dexlib.dx.rop.annotation.AnnotationsList;
import com.baidu.titan.dexlib.dx.rop.code.AccessFlags;
import com.baidu.titan.dexlib.dx.rop.cst.Constant;
import com.baidu.titan.dexlib.dx.rop.cst.ConstantPool;
import com.baidu.titan.dexlib.dx.rop.cst.CstNat;
import com.baidu.titan.dexlib.dx.rop.cst.CstString;
import com.baidu.titan.dexlib.dx.rop.cst.CstType;
import com.baidu.titan.dexlib.dx.rop.cst.TypedConstant;
import com.baidu.titan.dexlib.dx.rop.type.TypeList;
import com.baidu.titan.dexlib.dx.util.ByteArray;
import com.baidu.titan.dexlib.dx.util.Hex;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Standard subclass of {@link AttributeFactory}, which knows how to parse
 * all the standard attribute types.
 */
public class StdAttributeFactory
    extends AttributeFactory {
    /** {@code non-null;} shared instance of this class */
    public static final StdAttributeFactory THE_ONE =
        new StdAttributeFactory();

    /**
     * Constructs an instance.
     */
    public StdAttributeFactory() {
        // This space intentionally left blank.
    }

    private interface AttributeMethod {
        Attribute attributeMethod(DirectClassFile cf, int offset, int length, ParseObserver observer);
    }

    private final Map<String, AttributeMethod> CLASS_ATTRIBUTE_METHOD = Collections.unmodifiableMap(Stream.of(
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttDeprecated.ATTRIBUTE_NAME, this::deprecated),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttEnclosingMethod.ATTRIBUTE_NAME, this::enclosingMethod),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttInnerClasses.ATTRIBUTE_NAME, this::innerClasses),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttRuntimeInvisibleAnnotations.ATTRIBUTE_NAME, this::runtimeInvisibleAnnotations),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttRuntimeVisibleAnnotations.ATTRIBUTE_NAME, this::runtimeVisibleAnnotations),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttSynthetic.ATTRIBUTE_NAME, this::synthetic),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttSignature.ATTRIBUTE_NAME, this::signature),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttSourceFile.ATTRIBUTE_NAME, this::sourceFile)
            ).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

    private final Map<String, AttributeMethod> FIELD_ATTRIBUTE_METHOD = Collections.unmodifiableMap(Stream.of(
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttConstantValue.ATTRIBUTE_NAME, this::constantValue),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttDeprecated.ATTRIBUTE_NAME, this::deprecated),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttRuntimeInvisibleAnnotations.ATTRIBUTE_NAME, this::runtimeInvisibleAnnotations),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttRuntimeVisibleAnnotations.ATTRIBUTE_NAME, this::runtimeVisibleAnnotations),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttSignature.ATTRIBUTE_NAME, this::signature),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttSynthetic.ATTRIBUTE_NAME, this::synthetic)
    ).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

    private final Map<String, AttributeMethod> METHOD_ATTRIBUTE_METHOD = Collections.unmodifiableMap(Stream.of(
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttAnnotationDefault.ATTRIBUTE_NAME, this::annotationDefault),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttCode.ATTRIBUTE_NAME, this::code),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttDeprecated.ATTRIBUTE_NAME, this::deprecated),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttExceptions.ATTRIBUTE_NAME, this::exceptions),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttRuntimeInvisibleAnnotations.ATTRIBUTE_NAME, this::runtimeInvisibleAnnotations),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttRuntimeVisibleAnnotations.ATTRIBUTE_NAME, this::runtimeVisibleAnnotations),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttRuntimeInvisibleParameterAnnotations.ATTRIBUTE_NAME, this::runtimeInvisibleParameterAnnotations),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttRuntimeVisibleParameterAnnotations.ATTRIBUTE_NAME, this::runtimeVisibleParameterAnnotations),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttSignature.ATTRIBUTE_NAME, this::signature),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttSynthetic.ATTRIBUTE_NAME, this::synthetic)
    ).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

    private final Map<String, AttributeMethod> CODE_ATTRIBUTE_METHOD = Collections.unmodifiableMap(Stream.of(
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttLineNumberTable.ATTRIBUTE_NAME, this::lineNumberTable),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttLocalVariableTable.ATTRIBUTE_NAME, this::localVariableTable),
            new AbstractMap.SimpleEntry<String, AttributeMethod>(AttLocalVariableTypeTable.ATTRIBUTE_NAME, this::localVariableTypeTable)
    ).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

    /** {@inheritDoc} */
    @Override
    protected Attribute parse0(DirectClassFile cf, int context, String name,
                               int offset, int length, ParseObserver observer) {
        switch (context) {
            case CTX_CLASS: {
                if (CLASS_ATTRIBUTE_METHOD.containsKey(name)) {
                    return CLASS_ATTRIBUTE_METHOD.get(name).attributeMethod(cf, offset, length, observer);
                }
                break;
            }
            case CTX_FIELD: {
                if (FIELD_ATTRIBUTE_METHOD.containsKey(name)) {
                    return FIELD_ATTRIBUTE_METHOD.get(name).attributeMethod(cf, offset, length, observer);
                }
                break;
            }
            case CTX_METHOD: {
                if (METHOD_ATTRIBUTE_METHOD.containsKey(name)) {
                    return METHOD_ATTRIBUTE_METHOD.get(name).attributeMethod(cf, offset, length, observer);
                }
                break;
            }
            case CTX_CODE: {
                if (CODE_ATTRIBUTE_METHOD.containsKey(name)) {
                    return CODE_ATTRIBUTE_METHOD.get(name).attributeMethod(cf, offset, length, observer);
                }
                break;
            }
        }

        return super.parse0(cf, context, name, offset, length, observer);
    }

    /**
     * Parses an {@code AnnotationDefault} attribute.
     */
    private Attribute annotationDefault(DirectClassFile cf,
            int offset, int length, ParseObserver observer) {
        if (length < 2) {
            throwSeverelyTruncated();
        }

        AnnotationParser ap =
            new AnnotationParser(cf, offset, length, observer);
        Constant cst = ap.parseValueAttribute();

        return new AttAnnotationDefault(cst, length);
    }

    /**
     * Parses a {@code Code} attribute.
     */
    private Attribute code(DirectClassFile cf, int offset, int length,
            ParseObserver observer) {
        if (length < 12) {
            return throwSeverelyTruncated();
        }

        ByteArray bytes = cf.getBytes();
        ConstantPool pool = cf.getConstantPool();
        int maxStack = bytes.getUnsignedShort(offset); // u2 max_stack
        int maxLocals = bytes.getUnsignedShort(offset + 2); // u2 max_locals
        int codeLength = bytes.getInt(offset + 4); // u4 code_length
        int origOffset = offset;

        if (observer != null) {
            observer.parsed(bytes, offset, 2,
                            "max_stack: " + Hex.u2(maxStack));
            observer.parsed(bytes, offset + 2, 2,
                            "max_locals: " + Hex.u2(maxLocals));
            observer.parsed(bytes, offset + 4, 4,
                            "code_length: " + Hex.u4(codeLength));
        }

        offset += 8;
        length -= 8;

        if (length < (codeLength + 4)) {
            return throwTruncated();
        }

        int codeOffset = offset;
        offset += codeLength;
        length -= codeLength;
        BytecodeArray code =
            new BytecodeArray(bytes.slice(codeOffset, codeOffset + codeLength),
                              pool);
        if (observer != null) {
            code.forEach(new CodeObserver(code.getBytes(), observer));
        }

        // u2 exception_table_length
        int exceptionTableLength = bytes.getUnsignedShort(offset);
        ByteCatchList catches = (exceptionTableLength == 0) ?
            ByteCatchList.EMPTY :
            new ByteCatchList(exceptionTableLength);

        if (observer != null) {
            observer.parsed(bytes, offset, 2,
                            "exception_table_length: " +
                            Hex.u2(exceptionTableLength));
        }

        offset += 2;
        length -= 2;

        if (length < (exceptionTableLength * 8 + 2)) {
            return throwTruncated();
        }

        for (int i = 0; i < exceptionTableLength; i++) {
            if (observer != null) {
                observer.changeIndent(1);
            }

            int startPc = bytes.getUnsignedShort(offset);
            int endPc = bytes.getUnsignedShort(offset + 2);
            int handlerPc = bytes.getUnsignedShort(offset + 4);
            int catchTypeIdx = bytes.getUnsignedShort(offset + 6);
            CstType catchType = (CstType) pool.get0Ok(catchTypeIdx);
            catches.set(i, startPc, endPc, handlerPc, catchType);
            if (observer != null) {
                observer.parsed(bytes, offset, 8,
                                Hex.u2(startPc) + ".." + Hex.u2(endPc) +
                                " -> " + Hex.u2(handlerPc) + " " +
                                ((catchType == null) ? "<any>" :
                                 catchType.toHuman()));
            }
            offset += 8;
            length -= 8;

            if (observer != null) {
                observer.changeIndent(-1);
            }
        }

        catches.setImmutable();

        AttributeListParser parser =
            new AttributeListParser(cf, CTX_CODE, offset, this);
        parser.setObserver(observer);

        StdAttributeList attributes = parser.getList();
        attributes.setImmutable();

        int attributeByteCount = parser.getEndOffset() - offset;
        if (attributeByteCount != length) {
            return throwBadLength(attributeByteCount + (offset - origOffset));
        }

        return new AttCode(maxStack, maxLocals, code, catches, attributes);
    }

    /**
     * Parses a {@code ConstantValue} attribute.
     */
    private Attribute constantValue(DirectClassFile cf, int offset, int length,
            ParseObserver observer) {
        if (length != 2) {
            return throwBadLength(2);
        }

        ByteArray bytes = cf.getBytes();
        ConstantPool pool = cf.getConstantPool();
        int idx = bytes.getUnsignedShort(offset);
        TypedConstant cst = (TypedConstant) pool.get(idx);
        Attribute result = new AttConstantValue(cst);

        if (observer != null) {
            observer.parsed(bytes, offset, 2, "value: " + cst);
        }

        return result;
    }

    /**
     * Parses a {@code Deprecated} attribute.
     */
    private Attribute deprecated(DirectClassFile cf, int offset, int length,
            ParseObserver observer) {
        if (length != 0) {
            return throwBadLength(0);
        }

        return new AttDeprecated();
    }

    /**
     * Parses an {@code EnclosingMethod} attribute.
     */
    private Attribute enclosingMethod(DirectClassFile cf, int offset,
            int length, ParseObserver observer) {
        if (length != 4) {
            throwBadLength(4);
        }

        ByteArray bytes = cf.getBytes();
        ConstantPool pool = cf.getConstantPool();

        int idx = bytes.getUnsignedShort(offset);
        CstType type = (CstType) pool.get(idx);

        idx = bytes.getUnsignedShort(offset + 2);
        CstNat method = (CstNat) pool.get0Ok(idx);

        Attribute result = new AttEnclosingMethod(type, method);

        if (observer != null) {
            observer.parsed(bytes, offset, 2, "class: " + type);
            observer.parsed(bytes, offset + 2, 2, "method: " +
                            DirectClassFile.stringOrNone(method));
        }

        return result;
    }

    /**
     * Parses an {@code Exceptions} attribute.
     */
    private Attribute exceptions(DirectClassFile cf, int offset, int length,
            ParseObserver observer) {
        if (length < 2) {
            return throwSeverelyTruncated();
        }

        ByteArray bytes = cf.getBytes();
        int count = bytes.getUnsignedShort(offset); // number_of_exceptions

        if (observer != null) {
            observer.parsed(bytes, offset, 2,
                            "number_of_exceptions: " + Hex.u2(count));
        }

        offset += 2;
        length -= 2;

        if (length != (count * 2)) {
            throwBadLength((count * 2) + 2);
        }

        TypeList list = cf.makeTypeList(offset, count);
        return new AttExceptions(list);
    }

    /**
     * Parses an {@code InnerClasses} attribute.
     */
    private Attribute innerClasses(DirectClassFile cf, int offset, int length,
            ParseObserver observer) {
        if (length < 2) {
            return throwSeverelyTruncated();
        }

        ByteArray bytes = cf.getBytes();
        ConstantPool pool = cf.getConstantPool();
        int count = bytes.getUnsignedShort(offset); // number_of_classes

        if (observer != null) {
            observer.parsed(bytes, offset, 2,
                            "number_of_classes: " + Hex.u2(count));
        }

        offset += 2;
        length -= 2;

        if (length != (count * 8)) {
            throwBadLength((count * 8) + 2);
        }

        InnerClassList list = new InnerClassList(count);

        for (int i = 0; i < count; i++) {
            int innerClassIdx = bytes.getUnsignedShort(offset);
            int outerClassIdx = bytes.getUnsignedShort(offset + 2);
            int nameIdx = bytes.getUnsignedShort(offset + 4);
            int accessFlags = bytes.getUnsignedShort(offset + 6);
            CstType innerClass = (CstType) pool.get(innerClassIdx);
            CstType outerClass = (CstType) pool.get0Ok(outerClassIdx);
            CstString name = (CstString) pool.get0Ok(nameIdx);
            list.set(i, innerClass, outerClass, name, accessFlags);
            if (observer != null) {
                observer.parsed(bytes, offset, 2,
                                "inner_class: " +
                                DirectClassFile.stringOrNone(innerClass));
                observer.parsed(bytes, offset + 2, 2,
                                "  outer_class: " +
                                DirectClassFile.stringOrNone(outerClass));
                observer.parsed(bytes, offset + 4, 2,
                                "  name: " +
                                DirectClassFile.stringOrNone(name));
                observer.parsed(bytes, offset + 6, 2,
                                "  access_flags: " +
                                AccessFlags.innerClassString(accessFlags));
            }
            offset += 8;
        }

        list.setImmutable();
        return new AttInnerClasses(list);
    }

    /**
     * Parses a {@code LineNumberTable} attribute.
     */
    private Attribute lineNumberTable(DirectClassFile cf, int offset,
            int length, ParseObserver observer) {
        if (length < 2) {
            return throwSeverelyTruncated();
        }

        ByteArray bytes = cf.getBytes();
        int count = bytes.getUnsignedShort(offset); // line_number_table_length

        if (observer != null) {
            observer.parsed(bytes, offset, 2,
                            "line_number_table_length: " + Hex.u2(count));
        }

        offset += 2;
        length -= 2;

        if (length != (count * 4)) {
            throwBadLength((count * 4) + 2);
        }

        LineNumberList list = new LineNumberList(count);

        for (int i = 0; i < count; i++) {
            int startPc = bytes.getUnsignedShort(offset);
            int lineNumber = bytes.getUnsignedShort(offset + 2);
            list.set(i, startPc, lineNumber);
            if (observer != null) {
                observer.parsed(bytes, offset, 4,
                                Hex.u2(startPc) + " " + lineNumber);
            }
            offset += 4;
        }

        list.setImmutable();
        return new AttLineNumberTable(list);
    }

    /**
     * Parses a {@code LocalVariableTable} attribute.
     */
    private Attribute localVariableTable(DirectClassFile cf, int offset,
            int length, ParseObserver observer) {
        if (length < 2) {
            return throwSeverelyTruncated();
        }

        ByteArray bytes = cf.getBytes();
        int count = bytes.getUnsignedShort(offset);

        if (observer != null) {
            observer.parsed(bytes, offset, 2,
                    "local_variable_table_length: " + Hex.u2(count));
        }

        LocalVariableList list = parseLocalVariables(
                bytes.slice(offset + 2, offset + length), cf.getConstantPool(),
                observer, count, false);
        return new AttLocalVariableTable(list);
    }

    /**
     * Parses a {@code LocalVariableTypeTable} attribute.
     */
    private Attribute localVariableTypeTable(DirectClassFile cf, int offset,
            int length, ParseObserver observer) {
        if (length < 2) {
            return throwSeverelyTruncated();
        }

        ByteArray bytes = cf.getBytes();
        int count = bytes.getUnsignedShort(offset);

        if (observer != null) {
            observer.parsed(bytes, offset, 2,
                    "local_variable_type_table_length: " + Hex.u2(count));
        }

        LocalVariableList list = parseLocalVariables(
                bytes.slice(offset + 2, offset + length), cf.getConstantPool(),
                observer, count, true);
        return new AttLocalVariableTypeTable(list);
    }

    /**
     * Parse the table part of either a {@code LocalVariableTable}
     * or a {@code LocalVariableTypeTable}.
     *
     * @param bytes {@code non-null;} bytes to parse, which should <i>only</i>
     * contain the table data (no header)
     * @param pool {@code non-null;} constant pool to use
     * @param count {@code >= 0;} the number of entries
     * @param typeTable {@code true} iff this is for a type table
     * @return {@code non-null;} the constructed list
     */
    private LocalVariableList parseLocalVariables(ByteArray bytes,
            ConstantPool pool, ParseObserver observer, int count,
            boolean typeTable) {
        if (bytes.size() != (count * 10)) {
            // "+ 2" is for the count.
            throwBadLength((count * 10) + 2);
        }

        ByteArray.MyDataInputStream in = bytes.makeDataInputStream();
        LocalVariableList list = new LocalVariableList(count);

        try {
            for (int i = 0; i < count; i++) {
                int startPc = in.readUnsignedShort();
                int length = in.readUnsignedShort();
                int nameIdx = in.readUnsignedShort();
                int typeIdx = in.readUnsignedShort();
                int index = in.readUnsignedShort();
                CstString name = (CstString) pool.get(nameIdx);
                CstString type = (CstString) pool.get(typeIdx);
                CstString descriptor = null;
                CstString signature = null;

                if (typeTable) {
                    signature = type;
                } else {
                    descriptor = type;
                }

                list.set(i, startPc, length, name,
                        descriptor, signature, index);

                if (observer != null) {
                    observer.parsed(bytes, i * 10, 10, Hex.u2(startPc) +
                            ".." + Hex.u2(startPc + length) + " " +
                            Hex.u2(index) + " " + name.toHuman() + " " +
                            type.toHuman());
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("shouldn't happen", ex);
        }

        list.setImmutable();
        return list;
    }

    /**
     * Parses a {@code RuntimeInvisibleAnnotations} attribute.
     */
    private Attribute runtimeInvisibleAnnotations(DirectClassFile cf,
            int offset, int length, ParseObserver observer) {
        if (length < 2) {
            throwSeverelyTruncated();
        }

        AnnotationParser ap =
            new AnnotationParser(cf, offset, length, observer);
        Annotations annotations =
            ap.parseAnnotationAttribute(AnnotationVisibility.BUILD);

        return new AttRuntimeInvisibleAnnotations(annotations, length);
    }

    /**
     * Parses a {@code RuntimeVisibleAnnotations} attribute.
     */
    private Attribute runtimeVisibleAnnotations(DirectClassFile cf,
            int offset, int length, ParseObserver observer) {
        if (length < 2) {
            throwSeverelyTruncated();
        }

        AnnotationParser ap =
            new AnnotationParser(cf, offset, length, observer);
        Annotations annotations =
            ap.parseAnnotationAttribute(AnnotationVisibility.RUNTIME);

        return new AttRuntimeVisibleAnnotations(annotations, length);
    }

    /**
     * Parses a {@code RuntimeInvisibleParameterAnnotations} attribute.
     */
    private Attribute runtimeInvisibleParameterAnnotations(DirectClassFile cf,
            int offset, int length, ParseObserver observer) {
        if (length < 2) {
            throwSeverelyTruncated();
        }

        AnnotationParser ap =
            new AnnotationParser(cf, offset, length, observer);
        AnnotationsList list =
            ap.parseParameterAttribute(AnnotationVisibility.BUILD);

        return new AttRuntimeInvisibleParameterAnnotations(list, length);
    }

    /**
     * Parses a {@code RuntimeVisibleParameterAnnotations} attribute.
     */
    private Attribute runtimeVisibleParameterAnnotations(DirectClassFile cf,
            int offset, int length, ParseObserver observer) {
        if (length < 2) {
            throwSeverelyTruncated();
        }

        AnnotationParser ap =
            new AnnotationParser(cf, offset, length, observer);
        AnnotationsList list =
            ap.parseParameterAttribute(AnnotationVisibility.RUNTIME);

        return new AttRuntimeVisibleParameterAnnotations(list, length);
    }

    /**
     * Parses a {@code Signature} attribute.
     */
    private Attribute signature(DirectClassFile cf, int offset, int length,
            ParseObserver observer) {
        if (length != 2) {
            throwBadLength(2);
        }

        ByteArray bytes = cf.getBytes();
        ConstantPool pool = cf.getConstantPool();
        int idx = bytes.getUnsignedShort(offset);
        CstString cst = (CstString) pool.get(idx);
        Attribute result = new AttSignature(cst);

        if (observer != null) {
            observer.parsed(bytes, offset, 2, "signature: " + cst);
        }

        return result;
    }

    /**
     * Parses a {@code SourceFile} attribute.
     */
    private Attribute sourceFile(DirectClassFile cf, int offset, int length,
            ParseObserver observer) {
        if (length != 2) {
            throwBadLength(2);
        }

        ByteArray bytes = cf.getBytes();
        ConstantPool pool = cf.getConstantPool();
        int idx = bytes.getUnsignedShort(offset);
        CstString cst = (CstString) pool.get(idx);
        Attribute result = new AttSourceFile(cst);

        if (observer != null) {
            observer.parsed(bytes, offset, 2, "source: " + cst);
        }

        return result;
    }

    /**
     * Parses a {@code Synthetic} attribute.
     */
    private Attribute synthetic(DirectClassFile cf, int offset, int length,
            ParseObserver observer) {
        if (length != 0) {
            return throwBadLength(0);
        }

        return new AttSynthetic();
    }

    /**
     * Throws the right exception when a known attribute has a way too short
     * length.
     *
     * @return never
     * @throws ParseException always thrown
     */
    private static Attribute throwSeverelyTruncated() {
        throw new ParseException("severely truncated attribute");
    }

    /**
     * Throws the right exception when a known attribute has a too short
     * length.
     *
     * @return never
     * @throws ParseException always thrown
     */
    private static Attribute throwTruncated() {
        throw new ParseException("truncated attribute");
    }

    /**
     * Throws the right exception when an attribute has an unexpected length
     * (given its contents).
     *
     * @param expected expected length
     * @return never
     * @throws ParseException always thrown
     */
    private static Attribute throwBadLength(int expected) {
        throw new ParseException("bad attribute length; expected length " +
                                 Hex.u4(expected));
    }
}
