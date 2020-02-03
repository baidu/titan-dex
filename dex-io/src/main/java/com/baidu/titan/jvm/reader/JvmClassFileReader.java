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

package com.baidu.titan.jvm.reader;

import com.baidu.titan.dex.DexAccessFlags;
import com.baidu.titan.dex.DexAnnotationVisibility;
import com.baidu.titan.dex.DexItemFactory;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.node.DexClassNode;
import com.baidu.titan.dex.node.DexFieldNode;
import com.baidu.titan.dex.node.DexMethodNode;
import com.baidu.titan.dex.visitor.DexAnnotationVisitor;
import com.baidu.titan.dex.visitor.DexAnnotationVisitorInfo;
import com.baidu.titan.dex.visitor.DexClassVisitor;
import com.baidu.titan.dex.visitor.DexClassVisitorInfo;
import com.baidu.titan.dex.visitor.DexFieldVisitor;
import com.baidu.titan.dex.visitor.DexFieldVisitorInfo;
import com.baidu.titan.dex.visitor.DexFileVisitor;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

/**
 * 用于读取JVM Class二进制格式，转换成Dex格式。暂时不支持Code读取
 *
 * @author zhangdi07@baidu.com
 * @since 2017/11/3
 */

public class JvmClassFileReader {

    private byte[] mContent;

    private DexItemFactory mFactory;

    public JvmClassFileReader(byte[] content, DexItemFactory factory) {
        this.mContent = content;
        this.mFactory = factory;
    }

    public DexClassNode read() {
        ClassReader reader = new ClassReader(mContent);
        Jvm2DexClassVisitor cv = new Jvm2DexClassVisitor(mFactory);
        reader.accept(cv, ClassReader.SKIP_CODE);
        return cv.dexClassNode;
    }

    private static DexAccessFlags createAccessFlagsFromJvm(int access) {
        return new DexAccessFlags(access);
    }

    private static DexAnnotationVisibility createAnnotationVisibilityFromJvm(boolean visible) {
        return null;
    }

    private static class Jvm2DexClassVisitor extends ClassVisitor {

        private DexItemFactory mFactory;

        DexClassNode dexClassNode;


        public Jvm2DexClassVisitor(DexItemFactory factory) {
            super(Opcodes.ASM5);
            this.mFactory = factory;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            String[] interfacesDesc = null;
            if (interfaces != null) {
                interfacesDesc = new String[interfaces.length];
                for (int i = 0; i < interfaces.length; i++) {
                    interfacesDesc[i] = Type.getObjectType(interfaces[i]).getDescriptor();
                }
            }


            this.dexClassNode = new DexClassNode(
                    mFactory.createType(Type.getObjectType(name).getDescriptor()),
                    createAccessFlagsFromJvm(access),
                    superName != null ?
                            mFactory.createType(Type.getObjectType(superName).getDescriptor()) : null,
                    mFactory.createTypes(interfacesDesc));

        }

        @Override
        public void visitSource(String source, String debug) {
            this.dexClassNode.sourceFile = mFactory.createString(source);
        }

        @Override
        public void visitOuterClass(String owner, String name, String desc) {
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc,
                                                     boolean visible) {
            return super.visitTypeAnnotation(typeRef, typePath, desc, visible);
        }

        @Override
        public void visitAttribute(Attribute attr) {
            super.visitAttribute(attr);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature,
                                       Object value) {
            return new Jvm2DexFieldVisitor(dexClassNode, access, name, desc, signature, value,
                    mFactory);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                         String[] exceptions) {
            return new Jvm2DexMethodVisitor(dexClassNode, access, name, desc, signature, exceptions,
                    mFactory);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    private static class Jvm2DexFieldVisitor extends FieldVisitor {

        private DexClassNode mDexClassNode;

        private DexFieldNode mDexFieldNode;

        private DexItemFactory mFactory;

        public Jvm2DexFieldVisitor(DexClassNode dcn, int access, String name, String desc,
                                   String signature, Object value, DexItemFactory factory) {
            super(Opcodes.ASM5);
            this.mDexClassNode = dcn;
            this.mFactory = factory;
            mDexFieldNode = new DexFieldNode(
                    mFactory.createString(name),
                    mFactory.createType(desc),
                    mDexClassNode.type,
                    createAccessFlagsFromJvm(access));
            mDexFieldNode.staticValue = value;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return super.visitAnnotation(desc, visible);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc,
                                                     boolean visible) {
            return super.visitTypeAnnotation(typeRef, typePath, desc, visible);
        }

        @Override
        public void visitAttribute(Attribute attr) {
            super.visitAttribute(attr);
        }

        @Override
        public void visitEnd() {
            mDexClassNode.addField(mDexFieldNode);
        }
    }

    private static class Jvm2DexMethodVisitor extends MethodVisitor {

        private DexClassNode mDexClassNode;

        private DexMethodNode mDexMethodNode;

        private DexItemFactory mFactory;

        public Jvm2DexMethodVisitor(DexClassNode dcn, int access, String name, String desc,
                                    String signature, String[] exceptions, DexItemFactory factory) {
            super(Opcodes.ASM5);
            this.mDexClassNode = dcn;
            this.mFactory = factory;
            Type[] argumentTypes = Type.getArgumentTypes(desc);
            String[] typeDesciptors = null;
            if (argumentTypes != null && argumentTypes.length > 0) {
                typeDesciptors = new String[argumentTypes.length];
                for (int i = 0; i < argumentTypes.length; i++) {
                    typeDesciptors[i] = argumentTypes[i].getDescriptor();
                }
            }
            Type returnType = Type.getReturnType(desc);

            this.mDexMethodNode = new DexMethodNode(
                    mFactory.createString(name),
                    mDexClassNode.type,
                    mFactory.createTypes(typeDesciptors),
                    mFactory.createType(returnType.getDescriptor()),
                    createAccessFlagsFromJvm(access));
        }

        @Override
        public void visitParameter(String name, int access) {

        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return super.visitAnnotation(desc, visible);
        }

        @Override
        public void visitEnd() {
            mDexClassNode.addMethod(mDexMethodNode);
        }
    }

}
