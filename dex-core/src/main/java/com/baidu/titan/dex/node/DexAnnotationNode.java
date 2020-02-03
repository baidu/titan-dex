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

package com.baidu.titan.dex.node;

import com.baidu.titan.dex.DexAnnotationVisibility;
import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.SmaliWriter;
import com.baidu.titan.dex.visitor.DexAnnotationVisitor;
import com.baidu.titan.dex.visitor.DexAnnotationVisitorInfo;
import com.baidu.titan.dex.visitor.VisitorSupplier;

/**
 * Annotation Node
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/8
 */
public class DexAnnotationNode extends DexNode implements VisitorSupplier<DexAnnotationVisitor> {

    /**
     * 可见性
     */
    private DexAnnotationVisibility mVisibility;


    private DexConst.EncodedAnnotation mEncodedAnnotation;

    /**
     * 类型描述符
     */
    private DexType mType;

    public DexAnnotationNode(DexType type, DexAnnotationVisibility visibility) {
        this.mVisibility = visibility;
        this.mType = type;
    }

    public DexAnnotationNode(DexAnnotationVisitorInfo annotation) {
        this(annotation.type, annotation.visibility);
    }

    /**
     * get visibility
     *
     * @return
     */
    public DexAnnotationVisibility getVisibility() {
        return mVisibility;
    }

    public DexConst.EncodedAnnotation getEncodedAnnotation() {
        return mEncodedAnnotation;
    }

    /**
     * 获取类型描述符
     *
     * @return
     */
    public DexType getType() {
        return mType;
    }

    private void accept(DexAnnotationVisitor dav, DexConst.EncodedAnnotation encodedAnnotation) {
        dav.visitBegin();
        encodedAnnotation.getAnnotationItems()
                .forEach(a -> {
                    DexConst.EncodedValue value = a.getValue();
                    DexString name = a.getName();
                    switch (value.getType()) {
                        case DexConst.EncodedValue.VALUE_BYTE:
                        case DexConst.EncodedValue.VALUE_SHORT:
                        case DexConst.EncodedValue.VALUE_CHAR:
                        case DexConst.EncodedValue.VALUE_INT:
                        case DexConst.EncodedValue.VALUE_LONG:
                        case DexConst.EncodedValue.VALUE_FLOAT:
                        case DexConst.EncodedValue.VALUE_DOUBLE:
                        case DexConst.EncodedValue.VALUE_BOOLEAN: {
                            dav.visitPrimitive(name, value.asPrimitive());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_METHOD_TYPE:
                        case DexConst.EncodedValue.VALUE_METHOD_HANDLE: {
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_STRING: {
                            dav.visitString(name, value.asString());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_TYPE: {
                            dav.visitType(name, value.asType());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_FIELD: {
                            dav.visitField(name, value.asField());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_METHOD: {
                            dav.visitMethod(name, value.asMethod());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_ENUM: {
                            DexConst.EncodedEnum encodedEnum = value.asEnum();
                            dav.visitEnum(name, encodedEnum.getType(), encodedEnum.getName());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_ARRAY: {
                            DexAnnotationVisitor arrayVisitor = dav.visitArray(name);
                            accept(arrayVisitor, value.asArray());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_ANNOTATION: {
                            DexConst.EncodedAnnotation innerAnnotation = value.asAnnotation();
                            DexAnnotationVisitor innerAnnotationVisitor =
                                    dav.visitAnnotation(name, innerAnnotation.getType());
                            accept(innerAnnotationVisitor, innerAnnotation);
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_NULL: {
                            dav.visitNull(name);
                            break;
                        }
                    }

                });
        dav.visitEnd();
    }

    private void accept(DexAnnotationVisitor dav, DexConst.EncodedArray encodedArray) {
        dav.visitBegin();
        encodedArray.getArrayValue()
                .forEach(value -> {
                    DexString name = null;
                    switch (value.getType()) {
                        case DexConst.EncodedValue.VALUE_BYTE:
                        case DexConst.EncodedValue.VALUE_SHORT:
                        case DexConst.EncodedValue.VALUE_CHAR:
                        case DexConst.EncodedValue.VALUE_INT:
                        case DexConst.EncodedValue.VALUE_LONG:
                        case DexConst.EncodedValue.VALUE_FLOAT:
                        case DexConst.EncodedValue.VALUE_DOUBLE:
                        case DexConst.EncodedValue.VALUE_BOOLEAN: {
                            dav.visitPrimitive(name, value.asPrimitive());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_METHOD_TYPE:
                        case DexConst.EncodedValue.VALUE_METHOD_HANDLE: {
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_STRING: {
                            dav.visitString(name, value.asString());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_TYPE: {
                            dav.visitType(name, value.asType());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_FIELD: {
                            dav.visitField(name, value.asField());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_METHOD: {
                            dav.visitMethod(name, value.asMethod());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_ENUM: {
                            DexConst.EncodedEnum encodedEnum = value.asEnum();
                            dav.visitEnum(name, encodedEnum.getType(), encodedEnum.getName());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_ARRAY: {
                            DexAnnotationVisitor arrayVisitor = dav.visitArray(name);
                            accept(arrayVisitor, value.asArray());
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_ANNOTATION: {
                            DexConst.EncodedAnnotation innerAnnotation = value.asAnnotation();
                            DexAnnotationVisitor innerAnnotationVisitor =
                                    dav.visitAnnotation(name, innerAnnotation.getType());
                            accept(innerAnnotationVisitor, innerAnnotation);
                            break;
                        }
                        case DexConst.EncodedValue.VALUE_NULL: {
                            dav.visitNull(name);
                            break;
                        }
                    }

                });
        dav.visitEnd();
    }

    public void accept(DexAnnotationVisitor dav) {
        accept(dav, mEncodedAnnotation);
    }

    static class EncodedArrayFillerVisitor extends DexAnnotationVisitor {

        private DexConst.EncodedArray mArray;

        EncodedArrayFillerVisitor(DexConst.EncodedArray array) {
            mArray = array;
        }

        @Override
        public void visitBegin() {
            super.visitBegin();
        }

        @Override
        public void visitPrimitive(DexString name, Object value) {
            mArray.addEncodedValue(DexConst.EncodedValue.makePrimitive(value));
        }

        @Override
        public void visitString(DexString name, DexString value) {
            mArray.addEncodedValue(DexConst.EncodedValue.makeString(value));
        }

        @Override
        public void visitEnum(DexString name, DexType enumType, DexString enumName) {
            mArray.addEncodedValue(DexConst.EncodedValue.makeEnum(
                    DexConst.EncodedEnum.make(enumType, enumName)));
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(DexString name, DexType type) {
            DexConst.EncodedAnnotation inner = DexConst.EncodedAnnotation.makeMutable(type);
            mArray.addEncodedValue(DexConst.EncodedValue.makeAnnotation(inner));
            return new EncodedAnnotationFillerVisitor(inner);
        }

        @Override
        public DexAnnotationVisitor visitArray(DexString name) {
            DexConst.EncodedArray encodedArray = DexConst.EncodedArray.makeMutable();
            mArray.addEncodedValue(DexConst.EncodedValue.makeArray(encodedArray));
            return new EncodedArrayFillerVisitor(encodedArray);
        }

        @Override
        public void visitMethod(DexString name, DexConst.ConstMethodRef methodRef) {
            mArray.addEncodedValue(DexConst.EncodedValue.makeMethod(methodRef));
        }

        @Override
        public void visitField(DexString name, DexConst.ConstFieldRef fieldRef) {
            mArray.addEncodedValue(DexConst.EncodedValue.makeField(fieldRef));
        }

        @Override
        public void visitType(DexString name, DexType type) {
            mArray.addEncodedValue(DexConst.EncodedValue.makeType(type));
        }

        @Override
        public void visitNull(DexString name) {
            mArray.addEncodedValue(DexConst.EncodedValue.makeNull());
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            mArray.setImmutable();
        }

    }

    static class EncodedAnnotationFillerVisitor extends DexAnnotationVisitor {

        private DexConst.EncodedAnnotation mEncodedAnnotation;

        EncodedAnnotationFillerVisitor(DexConst.EncodedAnnotation annotation) {
            this.mEncodedAnnotation = annotation;
        }

        @Override
        public void visitBegin() {
            super.visitBegin();
        }

        @Override
        public void visitPrimitive(DexString name, Object value) {
            mEncodedAnnotation.addAnnotationElement(
                    DexConst.AnnotationElement.make(
                            name, DexConst.EncodedValue.makePrimitive(value)));
        }

        @Override
        public void visitString(DexString name, DexString value) {
            mEncodedAnnotation.addAnnotationElement(
                    DexConst.AnnotationElement.make(
                            name, DexConst.EncodedValue.makeString(value)));
        }

        @Override
        public void visitEnum(DexString name, DexType type, DexString enumName) {
            mEncodedAnnotation.addAnnotationElement(
                    DexConst.AnnotationElement.make(name,
                            DexConst.EncodedValue.makeEnum(
                                    DexConst.EncodedEnum.make(type, enumName))));
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(DexString name, DexType type) {
            DexConst.EncodedAnnotation inner = DexConst.EncodedAnnotation.makeMutable(type);
            mEncodedAnnotation.addAnnotationElement(
                    DexConst.AnnotationElement.make(name, DexConst.EncodedValue.makeAnnotation(inner)));
            return new EncodedAnnotationFillerVisitor(inner);
        }

        @Override
        public DexAnnotationVisitor visitArray(DexString name) {
            DexConst.EncodedArray encodedArray = DexConst.EncodedArray.makeMutable();
            mEncodedAnnotation.addAnnotationElement(
                    DexConst.AnnotationElement.make(name, DexConst.EncodedValue.makeArray(encodedArray)));
            return new EncodedArrayFillerVisitor(encodedArray);
        }

        @Override
        public void visitType(DexString name, DexType type) {
            mEncodedAnnotation.addAnnotationElement(
                    DexConst.AnnotationElement.make(name, DexConst.EncodedValue.makeType(type)));
        }

        @Override
        public void visitMethod(DexString name, DexConst.ConstMethodRef methodRef) {
            mEncodedAnnotation.addAnnotationElement(DexConst.AnnotationElement.make(name,
                    DexConst.EncodedValue.makeMethod(methodRef)));
        }

        @Override
        public void visitField(DexString name, DexConst.ConstFieldRef fieldRef) {
            mEncodedAnnotation.addAnnotationElement(DexConst.AnnotationElement.make(name,
                    DexConst.EncodedValue.makeField(fieldRef)));
        }

        @Override
        public void visitNull(DexString name) {
            mEncodedAnnotation.addAnnotationElement(DexConst.AnnotationElement.make(name,
                    DexConst.EncodedValue.makeNull()));
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            mEncodedAnnotation.setImmutable();
        }

    }

    @Override
    public DexAnnotationVisitor asVisitor() {
        DexConst.EncodedAnnotation annotation = DexConst.EncodedAnnotation.makeMutable(mType);
        mEncodedAnnotation = annotation;
        return new EncodedAnnotationFillerVisitor(annotation);
    }

    public void smaliTo(SmaliWriter writer) {
        writer.write(".annotation ");
        writer.write(getVisibility().getName());
        writer.write(' ');
        writer.write(getType().toTypeDescriptor());

        // # write element
        if (mEncodedAnnotation != null) {
            writer.indent(4);
            mEncodedAnnotation.getAnnotationItems()
                    .forEach(ai -> {
                        writer.newLine();
                        ai.smaliTo(writer);
                    });
            writer.deindent(4);
        }

        writer.newLine();
        writer.writeLine(".end annotation");
    }

}
