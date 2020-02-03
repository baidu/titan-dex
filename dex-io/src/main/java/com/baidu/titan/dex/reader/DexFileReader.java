/*
 * Copyright (C) Baidu Inc. All rights reserved.
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

package com.baidu.titan.dex.reader;

import com.baidu.titan.dex.DexAccessFlags;
import com.baidu.titan.dex.DexAnnotationVisibilitys;
import com.baidu.titan.dex.DexFileVersion;
import com.baidu.titan.dex.DexItemFactory;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.visitor.DexAnnotationVisitorInfo;
import com.baidu.titan.dex.visitor.DexClassVisitorInfo;
import com.baidu.titan.dex.visitor.DexFieldVisitorInfo;
import com.baidu.titan.dex.visitor.DexMethodVisitorInfo;
import com.baidu.titan.dexlib.dex.Annotation;
import com.baidu.titan.dexlib.dex.AnnotationSetItem;
import com.baidu.titan.dexlib.dex.AnnotationsDirectoryItem;
import com.baidu.titan.dexlib.dex.ClassData;
import com.baidu.titan.dexlib.dex.ClassDef;
import com.baidu.titan.dexlib.dex.Code;
import com.baidu.titan.dexlib.dex.EncodedValue;
import com.baidu.titan.dexlib.dex.FieldId;
import com.baidu.titan.dexlib.dex.MethodId;
import com.baidu.titan.dexlib.dex.ProtoId;
import com.baidu.titan.dexlib.dex.TypeList;
import com.baidu.titan.dex.visitor.DexAnnotationVisitor;
import com.baidu.titan.dex.visitor.DexClassVisitor;
import com.baidu.titan.dex.visitor.DexCodeVisitor;
import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.visitor.DexFieldVisitor;
import com.baidu.titan.dex.visitor.DexFileVisitor;
import com.baidu.titan.dex.visitor.DexMethodVisitor;
import com.baidu.titan.dexlib.dex.Dex;
import com.baidu.titan.dexlib.dex.EncodedValueReader;

import java.io.IOException;
import java.util.*;

/**
 * DexFileReader <br>
 * Dex具体格式参考：http://source.android.com/devices/tech/dalvik/dex-format.html
 *
 * @author zhangdi07@baidu.com
 * @since 2017/1/15
 */
public class DexFileReader {

    private static final int NO_INDEX = -1;

    public final static String OBJECT_TYPE_DESC = "Ljava/lang/Object;";

    private Dex mDex;

    private DexItemFactory mFactory;

    public DexFileReader(byte[] b, DexItemFactory factory) throws DexReadErrorException {
        try {
            mDex = new Dex(b);
        } catch (IOException e) {
            throw new DexReadErrorException(e);
        }
        if (factory == null) {
            factory = new DexItemFactory();
        }
        mFactory = factory;
    }

    public DexFileReader(byte[] b) throws DexReadErrorException {
        this(b, new DexItemFactory());
    }

    /**
     * 访问DexFile
     *
     * @param visitor
     */
    public void accept(DexFileVisitor visitor) {
        visitor.visitBegin();
        visitor.visitDexVersion(DexFileVersion.getVersion(mDex.getTableOfContents().dexVersion));
        Iterator<ClassDef> classDefIt = mDex.classDefs().iterator();
        while (classDefIt.hasNext()) {
            ClassDef classDef = classDefIt.next();
            readClass(classDef, visitor);
        }
        visitor.visitEnd();
    }

    private void readClass(ClassDef classDef, DexFileVisitor dexFileVisitor) {
        DexAccessFlags access = new DexAccessFlags(classDef.getAccessFlags());
        int typeIdx = classDef.getTypeIndex();
        String typeDesc = mDex.typeNames().get(typeIdx);
        DexType dexType = mFactory.createType(typeDesc);

        int superClassIdx = classDef.getSupertypeIndex();
        String superName;
        if (superClassIdx != NO_INDEX) {
            superName = mDex.typeNames().get(superClassIdx);
        } else {
            superName = OBJECT_TYPE_DESC;
        }
        DexType superType = mFactory.createType(superName);

        short[] interfacesIdx = classDef.getInterfaces();
        DexType[] interfaces = new DexType[interfacesIdx.length];
        for (int i = 0; i < interfacesIdx.length; i++) {
            interfaces[i] = mFactory.createType(mDex.typeNames().get(interfacesIdx[i]));
        }
        DexTypeList interfaceList = new DexTypeList(interfaces);

        DexClassVisitor dexClassVisitor = dexFileVisitor.visitClass(
                new DexClassVisitorInfo(dexType, superType, interfaceList, access));

        if (dexClassVisitor != null) {
            dexClassVisitor.visitBegin();

            int sourceFileIdx = classDef.getSourceFileIndex();
            String sourceFile = null;
            if (sourceFileIdx != -1) {
                sourceFile = mDex.strings().get(sourceFileIdx);
            }
            dexClassVisitor.visitSourceFile(mFactory.createString(sourceFile));

            AnnotationsDirectoryItem annotationDirItem = null;
            int directoryAnnotationOffset = classDef.getAnnotationsOffset();
            if (directoryAnnotationOffset != 0) {
                annotationDirItem = mDex.open(directoryAnnotationOffset)
                        .readAnnotationsDirectoryItem();
            }

            List<Annotation> classAnnotations = null;
            if (annotationDirItem != null) {
                classAnnotations = readClassAnnotations(classDef, annotationDirItem);
            }

            Annotation defaultAnnotation = null;
            if (classAnnotations != null) {
                // 处理Class Annotation
                for (Annotation annotation : classAnnotations) {
                    DexAnnotationVisitor annotationVisitor = dexClassVisitor.visitAnnotation(
                            new DexAnnotationVisitorInfo(
                                    mFactory.createType(
                                            mDex.typeNames().get(annotation.getTypeIndex())),
                                    DexAnnotationVisibilitys.get(annotation.getVisibility())));
                    if (annotationVisitor != null) {
                        annotationVisitor.visitBegin();
                        readAnnotation(annotation, annotationVisitor);
                        annotationVisitor.visitEnd();
                    }
                }
            }

            if (classDef.getClassDataOffset() != 0) {
                ClassData classData = mDex.readClassData(classDef);
                // visitField
                readAllFields(classDef, classData, annotationDirItem, dexClassVisitor);

                //visitMethod
                readAllMethods(classDef, classData, annotationDirItem,
                        defaultAnnotation, dexClassVisitor);
            }
            dexClassVisitor.visitEnd();
        }
    }

    private void readAllFields(ClassDef classDef, ClassData classData,
                               AnnotationsDirectoryItem annotationsDirectoryItem,
                               DexClassVisitor dexClassVisitor) {

        Object[] staticValues = readStaticValues(classDef);
        Map<Integer, AnnotationsDirectoryItem.FieldAnnotation> fieldAnnotationsMap = new HashMap<>();
        if (annotationsDirectoryItem != null) {
            AnnotationsDirectoryItem.FieldAnnotation[] fieldAnnotations =
                    annotationsDirectoryItem.getFieldAnnotations();
            if (fieldAnnotations != null) {
                for (AnnotationsDirectoryItem.FieldAnnotation fieldAnnotation : fieldAnnotations) {
                    fieldAnnotationsMap.put(fieldAnnotation.getFieldIndex(), fieldAnnotation);
                }
            }
        }

        ClassData.Field[] staticFields = classData.getStaticFields();
        if (staticFields != null) {
            readFields(staticFields, true, staticValues, fieldAnnotationsMap, dexClassVisitor);
        }
        ClassData.Field[] instanceFields = classData.getInstanceFields();
        if (instanceFields != null) {
            readFields(instanceFields, false, null, fieldAnnotationsMap, dexClassVisitor);
        }
    }

    private void readFields(ClassData.Field[] fields, boolean isStatic, Object[] staticValues,
                        Map<Integer, AnnotationsDirectoryItem.FieldAnnotation> fieldAnnotationsMap,
                        DexClassVisitor dexClassVisitor) {
        int size = fields.length;
        for (int i = 0; i < size; i++) {
            ClassData.Field field = fields[i];
            DexAccessFlags access = new DexAccessFlags(field.getAccessFlags());
            int fieldIndex = field.getFieldIndex();
            FieldId fieldId = mDex.fieldIds().get(fieldIndex);
            DexString name = mFactory.createString(mDex.strings().get(fieldId.getNameIndex()));
            DexType type = mFactory.createType(mDex.typeNames().get(fieldId.getTypeIndex()));
            DexType owner = mFactory.createType(
                    mDex.typeNames().get(fieldId.getDeclaringClassIndex()));

            DexFieldVisitor dexFieldVisitor = dexClassVisitor.visitField(
                    new DexFieldVisitorInfo(owner, name, type, access));

            if (dexFieldVisitor == null) {
                continue;
            }

            dexFieldVisitor.visitBegin();

            if (isStatic && staticValues != null && i < staticValues.length) {
                Object staticValue = staticValues[i];
                dexFieldVisitor.visitStaticValue(staticValue);
//                builder.setStaticValue(staticValue);
            }

            AnnotationsDirectoryItem.FieldAnnotation fieldAnnotation =
                    fieldAnnotationsMap.get(fieldIndex);

            if (fieldAnnotation != null) {
                AnnotationSetItem annotationSetItem = mDex.open(
                        fieldAnnotation.getAnnotationOffeset()).readAnnotationSetItem();
                List<Annotation> fieldAnnotations = readAnnotationSetItem(annotationSetItem);
                if (fieldAnnotations != null) {
                    for (Annotation annotation : fieldAnnotations) {
                        DexAnnotationVisitor annotationVisitor = dexFieldVisitor.visitAnnotation(
                                new DexAnnotationVisitorInfo(
                                        mFactory.createType(
                                                mDex.typeNames().get(annotation.getTypeIndex())),
                                        DexAnnotationVisibilitys.get(annotation.getVisibility())));
                        if (annotationVisitor != null) {
                            annotationVisitor.visitBegin();
                            readAnnotation(annotation, annotationVisitor);
                            annotationVisitor.visitEnd();
                        }
                    }
                }
            }

            dexFieldVisitor.visitEnd();
        }
    }

    private void readAnnotation(Annotation annotation, DexAnnotationVisitor annotationVisitor) {
        EncodedValueReader encodedValueReader = annotation.getReader();
        int fieldCount = encodedValueReader.readAnnotation();
        for (int i = 0; i < fieldCount; i++) {
            readAnnotationElement(encodedValueReader, annotationVisitor);
        }
    }

    private void readAnnotationElement(EncodedValueReader encodedValueReader,
                                       DexAnnotationVisitor dexAnnotationVisitor) {
        String elementName = mDex.strings().get(encodedValueReader.readAnnotationName());
        readEncodedValue(encodedValueReader, elementName, dexAnnotationVisitor);
    }

    private void readEncodedValue(EncodedValueReader encodedValueReader, String elementName,
                                  DexAnnotationVisitor annotationVisitor) {
        int type = encodedValueReader.peek();
        switch (type) {
            case EncodedValueReader.ENCODED_ENUM: {
                int enumFieldId = encodedValueReader.readEnum();
                FieldId fieldId = mDex.fieldIds().get(enumFieldId);
                DexString enumName = mFactory.createString(
                        mDex.strings().get(fieldId.getNameIndex()));
                // TODO owner or type ?
                DexType enumType = mFactory.createType(
                        mDex.typeNames().get(fieldId.getDeclaringClassIndex()));
                if (annotationVisitor != null) {
                    annotationVisitor.visitEnum(
                            mFactory.createString(elementName), enumType, enumName);
                }
                break;
            }
            case EncodedValueReader.ENCODED_ARRAY: {
                int arraySize = encodedValueReader.readArray();

                DexAnnotationVisitor arrayVisitor = annotationVisitor != null ?
                        annotationVisitor.visitArray(mFactory.createString(elementName)) : null;
                for (int j = 0; j < arraySize; j++) {
                    // elementName is igore
                    readEncodedValue(encodedValueReader, null, arrayVisitor);
                }
                if (arrayVisitor != null) {
                    arrayVisitor.visitEnd();
                }
                break;
            }
            case EncodedValueReader.ENCODED_ANNOTATION: {
                int nesetAnnonationSize = encodedValueReader.readAnnotation();
                DexAnnotationVisitor annotation4AnnotationVisitor
                        = annotationVisitor == null ? null : annotationVisitor.visitAnnotation(
                                mFactory.createString(elementName),
                                mFactory.createType(
                                        mDex.typeNames().get(
                                                encodedValueReader.getAnnotationType())));
                if (annotation4AnnotationVisitor != null) {
                    annotation4AnnotationVisitor.visitBegin();
                }
                for (int i = 0; i < nesetAnnonationSize; i++) {
                    readAnnotationElement(encodedValueReader, annotation4AnnotationVisitor);
                }
                if (annotation4AnnotationVisitor != null) {
                    annotation4AnnotationVisitor.visitEnd();
                }
                break;
            }
            case EncodedValueReader.ENCODED_TYPE: {
                String typeDesc = mDex.typeNames().get(encodedValueReader.readType());
                if (annotationVisitor != null) {
                    annotationVisitor.visitType(mFactory.createString(elementName),
                            mFactory.createType(typeDesc));
                }
                break;
            }
            case EncodedValueReader.ENCODED_STRING: {
                String value = mDex.strings().get(encodedValueReader.readString());
                if (annotationVisitor != null) {
                    annotationVisitor.visitString(mFactory.createString(elementName),
                            mFactory.createString(value));
                }
                break;
            }
            case EncodedValueReader.ENCODED_NULL: {
                encodedValueReader.readNull();
                if (annotationVisitor != null) {
                    annotationVisitor.visitNull(mFactory.createString(elementName));
                }
                break;
            }
            case EncodedValueReader.ENCODED_METHOD: {
                MethodId methodId = mDex.methodIds().get(encodedValueReader.readMethod());
                DexConst.ConstMethodRef methodRef = createConstMethodRef(methodId);
                if (annotationVisitor != null) {
                    annotationVisitor.visitMethod(mFactory.createString(elementName), methodRef);
                }
                break;
            }
            case EncodedValueReader.ENCODED_FIELD: {
                FieldId fieldId = mDex.fieldIds().get(encodedValueReader.readField());
                DexConst.ConstFieldRef fieldRef = createConstFieldRef(fieldId);
                if (annotationVisitor != null) {
                    annotationVisitor.visitField(mFactory.createString(elementName), fieldRef);
                }
                break;
            }
            case EncodedValueReader.ENCODED_BYTE:
            case EncodedValueReader.ENCODED_SHORT:
            case EncodedValueReader.ENCODED_CHAR:
            case EncodedValueReader.ENCODED_INT:
            case EncodedValueReader.ENCODED_LONG:
            case EncodedValueReader.ENCODED_FLOAT:
            case EncodedValueReader.ENCODED_DOUBLE:
            case EncodedValueReader.ENCODED_BOOLEAN: {
                Object primitiveValue = readPrimitiveEncodedValue(encodedValueReader, type);
                if (primitiveValue == null) {
                    throw new DexReadErrorException("non primitive value");
                } else {
                    if (annotationVisitor != null) {
                        annotationVisitor.visitPrimitive(mFactory.createString(elementName),
                                primitiveValue);
                    }
                }
                break;
            }
            default: {
                throw new DexReadErrorException("unknown annotation type for " + type);
            }
        }
    }


    private Object readPrimitiveEncodedValue(EncodedValueReader reader, int type) {
        Object value = null;
        switch (type) {
            case EncodedValueReader.ENCODED_BOOLEAN: {
                value = reader.readBoolean();
                break;
            }
            case EncodedValueReader.ENCODED_BYTE: {
                value = reader.readByte();
                break;
            }
            case EncodedValueReader.ENCODED_SHORT: {
                value = reader.readShort();
                break;
            }
            case EncodedValueReader.ENCODED_CHAR: {
                value = reader.readChar();
                break;
            }
            case EncodedValueReader.ENCODED_INT: {
                value = reader.readInt();
                break;
            }
            case EncodedValueReader.ENCODED_LONG: {
                value = reader.readLong();
                break;
            }
            case EncodedValueReader.ENCODED_FLOAT: {
                value = reader.readFloat();
                break;
            }
            case EncodedValueReader.ENCODED_DOUBLE: {
                value = reader.readDouble();
                break;
            }
        }
        return value;
    }


    private Object[] readStaticValues(ClassDef classDef) {
        if (classDef.getStaticValuesOffset() != 0) {
            Dex.Section staticValuesSection = mDex.open(classDef.getStaticValuesOffset());
            EncodedValue staticValue = staticValuesSection.readEncodedArray();
            EncodedValueReader reader = new EncodedValueReader(staticValue.asByteInput(),
                    EncodedValueReader.ENCODED_ARRAY);
            int size = reader.readArray();
            Object[] staticValues = new Object[size];
            for (int i = 0; i < size; i++) {
                int type = reader.peek();
                Object value = null;
                switch (type) {
                    case EncodedValueReader.ENCODED_STRING: {
                        value = mDex.strings().get(reader.readString());
                        break;
                    }
                    case EncodedValueReader.ENCODED_NULL: {
                        reader.skipValue();
                        break;
                    }
                    default: {
                        value = readPrimitiveEncodedValue(reader, type);
                        if (value == null) {
                            throw new DexReadErrorException("unkown type " + type);
                        }
                    }
                }
                staticValues[i] = value;
            }
            return staticValues;
        }

        return null;
    }

    private void readAllMethods(ClassDef classDef, ClassData classData,
                                AnnotationsDirectoryItem annotationsDirectoryItem,
                                Annotation defaultAnnotation, DexClassVisitor dexClassVisitor) {
        Map<Integer, AnnotationsDirectoryItem.MethodAnnotation> methodAnnotationsMap
                = new HashMap<>();
        Map<Integer, AnnotationsDirectoryItem.ParameterAnnotation> parameterAnnotationsMap
                = new HashMap<>();
        if (annotationsDirectoryItem != null) {
            // methodAnnotations
            AnnotationsDirectoryItem.MethodAnnotation[] methodAnnotations =
                    annotationsDirectoryItem.getMethodAnnotations();
            if (methodAnnotations != null) {
                for (AnnotationsDirectoryItem.MethodAnnotation methodAnnotation
                        : methodAnnotations) {
                    methodAnnotationsMap.put(methodAnnotation.getMethodIndex(), methodAnnotation);
                }
            }
            // parameterAnnotations
            AnnotationsDirectoryItem.ParameterAnnotation[] parameterAnnotations =
                    annotationsDirectoryItem.getParameterAnnotations();
            if (parameterAnnotations != null) {
                for (AnnotationsDirectoryItem.ParameterAnnotation parameterAnnotation :
                        parameterAnnotations) {
                    parameterAnnotationsMap.put(parameterAnnotation.getMethodIndex(),
                            parameterAnnotation);
                }
            }
        }


        ClassData.Method[] directMethods = classData.getDirectMethods();
        if (directMethods != null) {
            readMethods(directMethods, methodAnnotationsMap, parameterAnnotationsMap,
                    defaultAnnotation, dexClassVisitor);
        }

        ClassData.Method[] virtualMethods = classData.getVirtualMethods();
        if (virtualMethods != null) {
            readMethods(virtualMethods, methodAnnotationsMap, parameterAnnotationsMap,
                    defaultAnnotation, dexClassVisitor);
        }
    }

    private void readMethods(ClassData.Method[] methods, Map<Integer,
            AnnotationsDirectoryItem.MethodAnnotation> methodAnnotationMap,
            Map<Integer, AnnotationsDirectoryItem.ParameterAnnotation> parameterAnnotationsMap,
            Annotation defaultAnnotation, DexClassVisitor dexClassVisitor) {
        int size = methods.length;
        for (int i = 0; i < size; i++) {
            ClassData.Method method = methods[i];
            DexAccessFlags access = new DexAccessFlags(method.getAccessFlags());
            int methodIdIdx = method.getMethodIndex();
            MethodId methodId = mDex.methodIds().get(methodIdIdx);
            DexString methodName = mFactory.createString(
                    mDex.strings().get(methodId.getNameIndex()));
            DexType owner = mFactory.createType(
                    mDex.typeNames().get(methodId.getDeclaringClassIndex()));
            ProtoId protoId = mDex.protoIds().get(methodId.getProtoIndex());
            DexType returnType = mFactory.createType(
                    mDex.typeNames().get(protoId.getReturnTypeIndex()));
            TypeList typeList = mDex.readTypeList(protoId.getParametersOffset());
            short[] typesIdx = typeList.getTypes();
            DexType[] parameterTypes = new DexType[typesIdx.length];
            for (int j = 0; j < typesIdx.length; j++) {
                parameterTypes[j] = mFactory.createType(mDex.typeNames().get(typesIdx[j]));
            }
            DexTypeList parameterList = new DexTypeList(parameterTypes);

            DexMethodVisitor dexMethodVisitor = dexClassVisitor.visitMethod(
                    new DexMethodVisitorInfo(owner, methodName, parameterList, returnType, access));


            if (dexMethodVisitor != null) {
                dexMethodVisitor.visitBegin();

                // visit annotation
                AnnotationsDirectoryItem.MethodAnnotation methodAnnotation =
                        methodAnnotationMap.get(methodIdIdx);
                List<Annotation> methodAnnotations = null;
                if (methodAnnotation != null) {
                    AnnotationSetItem annotationSetItem = mDex.open(
                            methodAnnotation.getAnnotationOffeset()).readAnnotationSetItem();
                    methodAnnotations = readAnnotationSetItem(annotationSetItem);
                }
                if (methodAnnotations != null) {
                    for (Annotation annotation : methodAnnotations) {
                        DexAnnotationVisitor annotationVisitor = dexMethodVisitor.visitAnnotation(
                                new DexAnnotationVisitorInfo(
                                        mFactory.createType(
                                                mDex.typeNames().get(annotation.getTypeIndex())),
                                        DexAnnotationVisibilitys.get(annotation.getVisibility())));
                        if (annotationVisitor != null) {
                            annotationVisitor.visitBegin();
                            readAnnotation(annotation, annotationVisitor);
                            annotationVisitor.visitEnd();
                        }
                    }

                    if (defaultAnnotation != null) {
                        EncodedValueReader defaultAnnotationReader = defaultAnnotation.getReader();
                        int elementCount = defaultAnnotationReader.readAnnotation();
                        assert elementCount == 1;
                        for (int j = 0; j < elementCount; j++) {
                            String elementName = mDex.strings().get(
                                    defaultAnnotationReader.readAnnotationName());
                            int elementSize = defaultAnnotationReader.readAnnotation();
                            String typeDesc = mDex.typeNames().get(
                                    defaultAnnotationReader.getAnnotationType());
                            for (int k = 0; k < elementSize; k++) {
                                String elementNameInner = mDex.strings().get(
                                        defaultAnnotationReader.readAnnotationName());
                                DexAnnotationVisitor defaultAnnotationVisitor = null;
                                if (methodName.equals(elementNameInner)) {
                                    defaultAnnotationVisitor =
                                            dexMethodVisitor.visitAnnotationDefault();
                                }
                                readEncodedValue(defaultAnnotationReader,
                                        elementNameInner, defaultAnnotationVisitor);
                                if (defaultAnnotationVisitor != null) {
                                    defaultAnnotationVisitor.visitEnd();
                                }
                            }
                        }
                    }
                }


                // paramaterAnnotations
                AnnotationsDirectoryItem.ParameterAnnotation parameterAnnotation =
                        parameterAnnotationsMap.get(methodIdIdx);
                if (parameterAnnotation != null) {
                    Dex.Section setRefListSection =
                            mDex.open(parameterAnnotation.getAnnotationOffeset());
                    int setRefItemSize = setRefListSection.readInt();
                    for (int j = 0; j < setRefItemSize; j++) {
                        int annotationSetItemOff = setRefListSection.readInt();
                        if (annotationSetItemOff != 0) {
                            AnnotationSetItem parameterAnnotationSetItem =
                                    mDex.open(annotationSetItemOff).readAnnotationSetItem();
                            ArrayList<Annotation> parameterAnnotions =
                                    readAnnotationSetItem(parameterAnnotationSetItem);
                            for (Annotation oneParameterAnnotation : parameterAnnotions) {
                                DexAnnotationVisitor parameterAnnotationVisitor = dexMethodVisitor
                                        .visitParameterAnnotation(j, new DexAnnotationVisitorInfo(
                                                mFactory.createType(mDex.typeNames().get(
                                                           oneParameterAnnotation.getTypeIndex())),
                                                DexAnnotationVisibilitys.get(
                                                        oneParameterAnnotation.getVisibility())));
                                if (parameterAnnotationVisitor != null) {
                                    readAnnotation(oneParameterAnnotation,
                                            parameterAnnotationVisitor);
                                    parameterAnnotationVisitor.visitEnd();
                                }
                            }
                        }
                    }
                }

                int codeOff = method.getCodeOffset();
                if (codeOff != 0) {
                    DexCodeVisitor dexCodeVisitor = dexMethodVisitor.visitCode();
                    if (dexCodeVisitor != null) {
                        Code code = mDex.readCode(method);
                        DexCodeReader codeReader = new DexCodeReader(mDex, owner, parameterList,
                                access, code, dexCodeVisitor, mFactory);
                        codeReader.readCode();
                    }
                }
                dexMethodVisitor.visitEnd();
            }
        }
    }

    private DexConst.ConstMethodRef createConstMethodRef(MethodId methodId) {
        String owner = mDex.typeNames().get(methodId.getDeclaringClassIndex());
        String name = mDex.strings().get(methodId.getNameIndex());
        ProtoId protoId = mDex.protoIds().get(methodId.getProtoIndex());
        String returnType = mDex.typeNames().get(protoId.getReturnTypeIndex());
        TypeList typeList = mDex.readTypeList(protoId.getParametersOffset());
        short[] typesIdx = typeList.getTypes();
        String[] parameterTypes = new String[typesIdx.length];
        for (int j = 0; j < typesIdx.length; j++) {
            parameterTypes[j] = mDex.typeNames().get(typesIdx[j]);
        }
        return DexConst.ConstMethodRef.make(
                mFactory.createType(owner),
                mFactory.createString(name),
                mFactory.createType(returnType),
                mFactory.createTypes(parameterTypes));
    }

    private DexConst.ConstFieldRef createConstFieldRef(FieldId fieldId) {
        String owner = mDex.typeNames().get(fieldId.getDeclaringClassIndex());
        String name = mDex.strings().get(fieldId.getNameIndex());
        String type = mDex.typeNames().get(fieldId.getTypeIndex());
        return DexConst.ConstFieldRef.make(
                mFactory.createType(owner),
                mFactory.createType(type),
                mFactory.createString(name));
    }

    private List<Annotation> readClassAnnotations(ClassDef classDef,
                                                  AnnotationsDirectoryItem annotationDirItem) {
        ArrayList<Annotation> annotations = new ArrayList<>();

        if (annotationDirItem.getClassAnnotationsOffeset() != 0) {
            AnnotationSetItem annotationSetItem = mDex.open(
                    annotationDirItem.getClassAnnotationsOffeset()).readAnnotationSetItem();
            int[] annotationEntries = annotationSetItem.getAnnotationEntries();
            for (int annotiationOff : annotationEntries) {
                Annotation annotationItem = mDex.open(annotiationOff).readAnnotation();
                annotations.add(annotationItem);
            }
        }

        return annotations;
    }

    private ArrayList<Annotation> readAnnotationSetItem(AnnotationSetItem annotationSetItem) {
        ArrayList<Annotation> annotations = new ArrayList<Annotation>();
        int[] annotationEntries = annotationSetItem.getAnnotationEntries();
        for (int annotiationOff : annotationEntries) {
            Annotation annotationItem = mDex.open(annotiationOff).readAnnotation();
            annotations.add(annotationItem);
        }
        return annotations;
    }
}
