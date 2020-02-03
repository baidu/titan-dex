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

package com.baidu.titan.dex.writer;

import com.baidu.titan.dex.DexAccessFlags;
import com.baidu.titan.dex.DexAnnotationVisibility;
import com.baidu.titan.dex.DexAnnotationVisibilitys;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.visitor.DexAnnotationVisitorInfo;
import com.baidu.titan.dex.visitor.DexClassVisitorInfo;
import com.baidu.titan.dex.visitor.DexFieldVisitorInfo;
import com.baidu.titan.dex.visitor.DexMethodVisitorInfo;
import com.baidu.titan.dexlib.dx.dex.code.DalvCode;
import com.baidu.titan.dexlib.dx.dex.file.ClassDefItem;
import com.baidu.titan.dexlib.dx.dex.file.DexFile;
import com.baidu.titan.dexlib.dx.dex.file.EncodedField;
import com.baidu.titan.dexlib.dx.dex.file.EncodedMethod;
import com.baidu.titan.dexlib.dx.rop.cst.CstNat;
import com.baidu.titan.dexlib.dx.rop.cst.CstType;
import com.baidu.titan.dexlib.dx.rop.type.StdTypeList;
import com.baidu.titan.dexlib.dx.rop.type.Type;
import com.baidu.titan.dex.visitor.DexAnnotationVisitor;
import com.baidu.titan.dex.visitor.DexClassVisitor;
import com.baidu.titan.dex.visitor.DexCodeVisitor;
import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexConstant;
import com.baidu.titan.dex.visitor.DexFieldVisitor;
import com.baidu.titan.dex.visitor.DexMethodVisitor;
import com.baidu.titan.dexlib.dx.rop.annotation.Annotation;
import com.baidu.titan.dexlib.dx.rop.annotation.AnnotationVisibility;
import com.baidu.titan.dexlib.dx.rop.annotation.Annotations;
import com.baidu.titan.dexlib.dx.rop.annotation.AnnotationsList;
import com.baidu.titan.dexlib.dx.rop.annotation.NameValuePair;
import com.baidu.titan.dexlib.dx.rop.cst.Constant;
import com.baidu.titan.dexlib.dx.rop.cst.CstAnnotation;
import com.baidu.titan.dexlib.dx.rop.cst.CstArray;
import com.baidu.titan.dexlib.dx.rop.cst.CstBoolean;
import com.baidu.titan.dexlib.dx.rop.cst.CstByte;
import com.baidu.titan.dexlib.dx.rop.cst.CstChar;
import com.baidu.titan.dexlib.dx.rop.cst.CstDouble;
import com.baidu.titan.dexlib.dx.rop.cst.CstEnumRef;
import com.baidu.titan.dexlib.dx.rop.cst.CstFieldRef;
import com.baidu.titan.dexlib.dx.rop.cst.CstFloat;
import com.baidu.titan.dexlib.dx.rop.cst.CstInteger;
import com.baidu.titan.dexlib.dx.rop.cst.CstKnownNull;
import com.baidu.titan.dexlib.dx.rop.cst.CstLong;
import com.baidu.titan.dexlib.dx.rop.cst.CstMethodRef;
import com.baidu.titan.dexlib.dx.rop.cst.CstShort;
import com.baidu.titan.dexlib.dx.rop.cst.CstString;

import java.util.ArrayList;

/**
 * DexClassWriter <br>
 * 内部使用，非公开API <br>
 * Dex 指令集参考：http://source.android.com/devices/tech/dalvik/dalvik-bytecode.html <br>
 * http://source.android.com/devices/tech/dalvik/instruction-formats.html
 * @author zhangdi07@baidu.com
 * @since 2017/1/15
 */
/** package */ class DexClassWriter extends DexClassVisitor {

    ClassDefItem mClassDefItem;
    DexFile mDexFile;
    Annotations mClassAnnotations = new Annotations();

    public DexClassWriter(DexFile dexFile, DexClassVisitorInfo classInfo) {
        super();
        this.mDexFile = dexFile;

        StdTypeList interfaceList = new StdTypeList(
                classInfo.interfaces != null ? classInfo.interfaces.count() : 0);
        if (classInfo.interfaces != null) {
            for (int i = 0; i < classInfo.interfaces.count(); i++) {
                interfaceList.set(i,
                        Type.intern(classInfo.interfaces.getType(i).toTypeDescriptor()));
            }
        }
        mClassDefItem = new ClassDefItem(CstType.intern(
                Type.intern(classInfo.type.toTypeDescriptor())), classInfo.access.getFlags(),
                CstType.intern(
                        Type.intern(classInfo.superType.toTypeDescriptor())), interfaceList, null);
    }

    @Override
    public void visitSourceFile(DexString sourceFile) {
        CstString sourceFileCst = sourceFile == null ? null : new CstString(sourceFile.toString());
        mClassDefItem.setSourceFile(sourceFileCst);
    }

    @Override
    public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
        return new DexAnnotationWriter(annotationInfo) {
            @Override
            public void writeAnnotationsEnd(Annotation annotation) {
                mClassAnnotations.add(annotation);
            }
        };
    }

    @Override
    public DexFieldVisitor visitField(DexFieldVisitorInfo field) {
        return new DexFieldWriter(field);
    }

    @Override
    public DexMethodVisitor visitMethod(DexMethodVisitorInfo method) {
        return new DexMethodWriter(method);
    }

    @Override
    public void visitEnd() {
        if (mClassAnnotations.size() > 0) {
            mClassDefItem.setClassAnnotations(mClassAnnotations,mDexFile);
        }
        mDexFile.add(mClassDefItem);
    }

    private Constant primitiveToConstant(Object value) {
        Constant constant = null;
        if (value instanceof Boolean) {
            constant = CstBoolean.make((boolean) value);
        } else if (value instanceof Byte) {
            constant = CstByte.make((byte) value);
        } else if (value instanceof Short) {
            constant = CstShort.make((short) value);
        } else if (value instanceof Character) {
            constant = CstChar.make((char) value);
        } else if (value instanceof Integer) {
            constant = CstInteger.make((int) value);
        } else if (value instanceof Float) {
            constant = CstFloat.make(Float.floatToIntBits((float) value));
        } else if (value instanceof Long) {
            constant = CstLong.make((long) value);
        } else if (value instanceof Double) {
            constant = CstDouble.make(Double.doubleToLongBits((double)value));
        }
        return constant;
    }

    abstract class DexAnnotationWriter extends DexAnnotationVisitor {

        private Annotation mAnnotation;

        DexAnnotationWriter(DexAnnotationVisitorInfo annotationInfo) {
            AnnotationVisibility annotationVisibility;
            if (annotationInfo.visibility.get() ==
                    DexAnnotationVisibilitys.ANNOTATION_VISIBILITY_BUILD) {
                annotationVisibility = AnnotationVisibility.BUILD;
            } else if (annotationInfo.visibility.get() ==
                    DexAnnotationVisibilitys.ANNOTATION_VISIBILITY_RUNTIME) {
                annotationVisibility = AnnotationVisibility.RUNTIME;
            } else if (annotationInfo.visibility.get() ==
                    DexAnnotationVisibilitys.ANNOTATION_VISIBILITY_SYSTEM) {
                annotationVisibility = AnnotationVisibility.SYSTEM;
            } else {
                throw new IllegalStateException();
            }
            mAnnotation = new Annotation(
                    CstType.intern(Type.intern(annotationInfo.type.toTypeDescriptor())),
                    annotationVisibility);
        }

        @Override
        public void visitPrimitive(DexString name, Object value) {
            Constant constant = primitiveToConstant(value);
            if (constant == null) {
                throw new IllegalStateException();
            }
            NameValuePair nvp = new NameValuePair(new CstString(name.toString()), constant);
            mAnnotation.add(nvp);
        }

        @Override
        public void visitString(DexString name, DexString value) {
            NameValuePair nvp = new NameValuePair(
                    new CstString(name.toString()), new CstString(value.toString()));
            mAnnotation.add(nvp);
        }

        @Override
        public void visitEnum(DexString name, DexType enumType, DexString enumName) {
            CstEnumRef cstEnumRef = new CstEnumRef(new CstNat(new CstString(enumName.toString()),
                    new CstString(enumType.toTypeDescriptor())));
            NameValuePair nvp = new NameValuePair(new CstString(name.toString()), cstEnumRef);
            mAnnotation.add(nvp);
        }

        @Override
        public void visitMethod(DexString name, DexConst.ConstMethodRef methodRef) {
            NameValuePair nvp = new NameValuePair(
                    new CstString(name.toString()),
                    new CstMethodRef(
                            CstType.intern(Type.intern(methodRef.getOwner().toTypeDescriptor())),
                            new CstNat(new CstString(methodRef.getName().toString()),
                                    new CstString(methodRef.getDesc()))));
            mAnnotation.add(nvp);
        }

        @Override
        public void visitNull(DexString name) {
            NameValuePair nvp = new NameValuePair(
                    new CstString(name.toString()), CstKnownNull.THE_ONE);
            mAnnotation.add(nvp);
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(final DexString name, DexType type) {

            return new DexAnnotationWriter(new DexAnnotationVisitorInfo(type,
                    DexAnnotationVisibilitys.
                            get(DexAnnotationVisibilitys.ANNOTATION_VISIBILITY_BUILD))) {

                @Override
                public void writeAnnotationsEnd(Annotation annotation) {
                    annotation.setImmutable();
                    NameValuePair nvp = new NameValuePair(
                            new CstString(name.toString()), new CstAnnotation(annotation));
                    mAnnotation.add(nvp);
                }
            };
        }

        @Override
        public DexAnnotationVisitor visitArray(final DexString name) {
            return new DexAnnotationArrayWriter() {
                @Override
                public void writeAnnotationsEnd(CstArray cstArray) {
                    NameValuePair nvp = new NameValuePair(new CstString(name.toString()), cstArray);
                    mAnnotation.add(nvp);
                }
            };
        }

        @Override
        public void visitType(DexString name, DexType type) {
            NameValuePair nvp = new NameValuePair(
                    new CstString(name.toString()),
                    CstType.intern(Type.internReturnType(type.toTypeDescriptor())));
            mAnnotation.add(nvp);
        }

        @Override
        public void visitEnd() {
            writeAnnotationsEnd(mAnnotation);
        }

        public abstract void writeAnnotationsEnd(Annotation annotation);

    }

    abstract class DexAnnotationArrayWriter extends DexAnnotationVisitor {

        private ArrayList<Constant> mConstantArray = new ArrayList<>();

        @Override
        public void visitPrimitive(DexString name, Object value) {
            mConstantArray.add(primitiveToConstant(value));
        }

        @Override
        public void visitString(DexString name, DexString value) {
            mConstantArray.add(new CstString(value.toString()));
        }

        @Override
        public void visitEnum(DexString name, DexType enumType, DexString enumName) {
            CstEnumRef cstEnumRef = new CstEnumRef(
                    new CstNat(new CstString(enumName.toString()),
                            new CstString(enumType.toTypeDescriptor())));
            mConstantArray.add(cstEnumRef);
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(DexString name, DexType type) {
            return new DexAnnotationWriter(new DexAnnotationVisitorInfo(type,
                    DexAnnotationVisibilitys.
                            get(DexAnnotationVisibilitys.ANNOTATION_VISIBILITY_BUILD))) {
                @Override
                public void writeAnnotationsEnd(Annotation annotation) {
                    annotation.setImmutable();
                    mConstantArray.add(new CstAnnotation(annotation));
                }
            };
        }

        @Override
        public DexAnnotationVisitor visitArray(DexString name) {
            return new DexAnnotationArrayWriter() {
                @Override
                public void writeAnnotationsEnd(CstArray cstArray) {
                    mConstantArray.add(cstArray);
                }
            };
        }

        @Override
        public void visitType(DexString name, DexType type) {
            mConstantArray.add(CstType.intern(Type.intern(type.toTypeDescriptor())));
        }

        @Override
        public void visitEnd() {
            CstArray.List cstArrayList = new CstArray.List(mConstantArray.size());
            for (int i = 0; i < mConstantArray.size(); i++) {
                cstArrayList.set(i, mConstantArray.get(i));
            }
            cstArrayList.setImmutable();
            writeAnnotationsEnd(new CstArray(cstArrayList));
        }

        public abstract void writeAnnotationsEnd(CstArray cstArray);
    }


    /** package */ class DexFieldWriter extends DexFieldVisitor {
        private DexFieldVisitorInfo mDexFieldInfo;
        private EncodedField mEncodedField;
        private Annotations mFieldAnnotations = new Annotations();
        private Object mStaticValue;

        DexFieldWriter(DexFieldVisitorInfo fieldInfo) {
            this.mDexFieldInfo = fieldInfo;
            CstFieldRef fieldRef = new CstFieldRef(mClassDefItem.getThisClass(),
                    new CstNat(new CstString(mDexFieldInfo.name.toString()),
                            new CstString(mDexFieldInfo.type.toTypeDescriptor())));
            mEncodedField = new EncodedField(fieldRef, mDexFieldInfo.accessFlags.getFlags());
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
            return new DexAnnotationWriter(annotationInfo) {
                @Override
                public void writeAnnotationsEnd(Annotation annotation) {
                    mFieldAnnotations.add(annotation);
                }
            };
        }

        @Override
        public void visitStaticValue(Object staticValue) {
            mStaticValue = staticValue;
        }

        @Override
        public void visitEnd() {
            if ((mEncodedField.getAccessFlags() & DexAccessFlags.ACC_STATIC) != 0) {
                Constant initValue = null;
                if (mStaticValue != null) {
                    if (mStaticValue instanceof String) {
                        initValue = new CstString((String)mStaticValue);
                    } else {
                        initValue = primitiveToConstant(mStaticValue);
                    }
                }
                mClassDefItem.addStaticField(mEncodedField, initValue);
            } else {
                mClassDefItem.addInstanceField(mEncodedField);
            }
            if (mFieldAnnotations.size() > 0) {
                mClassDefItem.addFieldAnnotations(mEncodedField.getRef(),
                        mFieldAnnotations, mDexFile);
            }

        }
    }


    /** package */ class DexMethodWriter extends DexMethodVisitor {

        private DexMethodVisitorInfo mDexMethodInfo;

        private Annotations mMethodAnnotations = new Annotations();

        private EncodedMethod mEncodedMethod;

        private DalvCode mDalvCode;

        private Annotations[] mAnnotationsArray;

        public DexMethodWriter(DexMethodVisitorInfo dexMethodInfo) {
            this.mDexMethodInfo = dexMethodInfo;
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
            return new DexAnnotationWriter(annotationInfo) {
                @Override
                public void writeAnnotationsEnd(Annotation annotation) {
                    mMethodAnnotations.add(annotation);
                }
            };
        }

        @Override
        public DexAnnotationVisitor visitParameterAnnotation(final int parameter,
                                                    DexAnnotationVisitorInfo annotationInfo) {
            if (mAnnotationsArray == null) {
                mAnnotationsArray = new Annotations[mDexMethodInfo.parameters.count()];
            }

            return new DexAnnotationWriter(annotationInfo) {
                @Override
                public void writeAnnotationsEnd(Annotation annotation) {
                    Annotations annotations = mAnnotationsArray[parameter];
                    if (annotations == null) {
                        annotations = new Annotations();
                        mAnnotationsArray[parameter] = annotations;
                    }
                    annotations.add(annotation);
                }
            };
        }

        @Override
        public DexCodeVisitor visitCode() {
            DexCodeWriter dcw = new DexCodeWriter(mDexMethodInfo) {
                @Override
                public void writeDexCodeEnd(DalvCode dalvCode) {
                    mDalvCode = dalvCode;
                }
            };

            return new DexCodeWriterBackend(dcw);

        }

        private String getDesc(DexMethodVisitorInfo methodInfo) {
            StringBuilder dp = new StringBuilder("(");
            if (methodInfo.parameters != null) {
                for (DexType param : methodInfo.parameters.types()) {
                    dp.append(param.toTypeDescriptor());
                }
            }
            dp.append(")").append(methodInfo.returnType.toTypeDescriptor());
            return dp.toString();
        }

        @Override
        public void visitEnd() {
            CstMethodRef methodRef = new CstMethodRef(mClassDefItem.getThisClass(),
                    new CstNat(new CstString(mDexMethodInfo.name.toString()),
                            new CstString(getDesc(mDexMethodInfo))));
            EncodedMethod encodedMethod = new EncodedMethod(methodRef,
                    mDexMethodInfo.accessFlags.getFlags(),
                    mDalvCode,
                    new StdTypeList(0));
            if (mDexMethodInfo.accessFlags.containsOneOf(
                    DexAccessFlags.ACC_PRIVATE | DexAccessFlags.ACC_STATIC
                            | DexAccessFlags.ACC_CONSTRUCTOR)) {
                mClassDefItem.addDirectMethod(encodedMethod);
            } else {
                mClassDefItem.addVirtualMethod(encodedMethod);
            }
            if (mMethodAnnotations.size() > 0) {
                mClassDefItem.addMethodAnnotations(methodRef, mMethodAnnotations, mDexFile);
            }

            if (mAnnotationsArray != null) {
                AnnotationsList paraAnnotationList = new AnnotationsList(mAnnotationsArray.length);
                for (int i = 0; i < mAnnotationsArray.length; i++) {
                    Annotations annotations = mAnnotationsArray[i];
                    if (annotations == null) {
                        annotations = new Annotations();
                    }
                    annotations.setImmutable();
                    paraAnnotationList.set(i, annotations);
                }
                mClassDefItem.addParameterAnnotations(methodRef, paraAnnotationList, mDexFile);
            }
        }
    }

}
