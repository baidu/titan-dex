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

import com.baidu.titan.dex.DexAccessFlag;
import com.baidu.titan.dex.DexAccessFlags;
import com.baidu.titan.dex.DexItemFactory;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.SmaliWriter;
import com.baidu.titan.dex.visitor.DexAnnotationVisitor;
import com.baidu.titan.dex.visitor.DexAnnotationVisitorInfo;
import com.baidu.titan.dex.visitor.DexClassNodeVisitor;
import com.baidu.titan.dex.visitor.DexClassVisitor;
import com.baidu.titan.dex.visitor.DexClassVisitorInfo;
import com.baidu.titan.dex.visitor.DexFieldVisitor;
import com.baidu.titan.dex.visitor.DexFieldVisitorInfo;
import com.baidu.titan.dex.visitor.DexFileVisitor;
import com.baidu.titan.dex.visitor.DexMethodVisitor;
import com.baidu.titan.dex.visitor.DexMethodVisitorInfo;
import com.baidu.titan.dex.visitor.VisitorSupplier;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 类Node信息
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/8
 */
public class DexClassNode extends DexNode implements VisitorSupplier<DexClassVisitor>, Comparable<DexClassNode> {

    public DexType type;

    public DexAccessFlags accessFlags;

    public DexType superType;

    public DexTypeList interfaces;

    public DexString sourceFile;

    private List<DexAnnotationNode> mAnnotations = new ArrayList<>();

    private List<DexFieldNode> mFields = new ArrayList<>();

    private List<DexMethodNode> mMethods = new ArrayList<>();

    public DexClassNode(DexType type, DexAccessFlags access, DexType superType,
                        DexTypeList interfaces) {
        this.type = type;
        this.accessFlags = access;
        this.superType = superType;
        this.interfaces = interfaces;
    }

    public DexClassNode(DexClassVisitorInfo classInfo) {
        this(classInfo.type, classInfo.access, classInfo.superType, classInfo.interfaces);
    }

    public boolean isPrimitiveType() {
        char s = type.toTypeDescriptor().charAt(0);
        return s != 'L' && s != '[';
    }

    /**
     * 获取类级别注解列表
     *
     * @return
     */
    public List<DexAnnotationNode> getClassAnnotations() {
        return mAnnotations;
    }

    public void setClassAnnotations(List<DexAnnotationNode> annotations) {
        this.mAnnotations = annotations;
    }

    public void addClassAnnotation(DexAnnotationNode annotation) {
        this.mAnnotations.add(annotation);
    }

    public void removeClassAnnotation(DexAnnotationNode annotation) {
        this.mAnnotations.remove(annotation);
    }

    /**
     * 获取字段列表
     *
     * @return
     */
    public List<DexFieldNode> getFields() {
        return mFields;
    }

    public void setFields(List<DexFieldNode> fields) {
        this.mFields = fields;
    }

    public void addField(DexFieldNode dfn) {
        this.mFields.add(dfn);
    }

    public void removeField(DexFieldNode dfn) {
        this.mFields.remove(dfn);
    }

    /**
     * 获取方法列表
     *
     * @return
     */
    public List<DexMethodNode> getMethods() {
        return mMethods;
    }

    public void setMethods(List<DexMethodNode> methods) {
        this.mMethods = methods;
    }

    public void addMethod(DexMethodNode dmn) {
        this.mMethods.add(dmn);
    }

    public void removeMethod(DexMethodNode dmn) {
        this.mMethods.remove(dmn);
    }

    public boolean isInterface() {
        return accessFlags.containsOneOf(DexAccessFlags.ACC_INTERFACE);
    }

    public boolean isAbstract() {
        return accessFlags.containsOneOf(DexAccessFlags.ACC_ABSTRACT);
    }

    public boolean isArrayClass() {
        return type.toShortDescriptor() == DexItemFactory.ArrayType.SHORT_DESCRIPTOR;
    }

    void accept(DexFileVisitor dfv) {
        DexClassVisitor dcv = dfv.visitClass(
                new DexClassVisitorInfo(type, superType, interfaces, accessFlags));
        if (dcv != null) {
            accept(dcv);
        }
    }

    public void accept(DexClassVisitor dcv) {
        dcv.visitBegin();

        if (sourceFile != null) {
            dcv.visitSourceFile(sourceFile);
        }

        if (mAnnotations != null) {
            for (DexAnnotationNode adn : mAnnotations) {
                DexAnnotationVisitor cdav = dcv.visitAnnotation(
                        new DexAnnotationVisitorInfo(adn.getType(), adn.getVisibility()));
                if (cdav != null) {
                    adn.accept(cdav);
                }
            }
        }

        if (mFields != null) {
            for (DexFieldNode dfn : mFields) {
                DexFieldVisitor dfv = dcv.visitField(
                        new DexFieldVisitorInfo(dfn.owner, dfn.name, dfn.type, dfn.accessFlags));
                if (dfv != null) {
                    dfn.accept(dfv);
                }
            }
        }

        if (mMethods != null) {
            for (DexMethodNode dmn : mMethods) {
                DexMethodVisitor dmv = dcv.visitMethod(
                        new DexMethodVisitorInfo(dmn.owner, dmn.name, dmn.parameters,
                                dmn.returnType, dmn.accessFlags));
                if (dmv != null) {
                    dmn.accept(dmv);
                }
            }
        }
        dcv.visitEnd();
    }

    ///////////////////////////////////////////

    public void accept(DexClassNodeVisitor dcnv) {
        if (mAnnotations != null) {
            for (DexAnnotationNode adn : mAnnotations) {
                dcnv.visitClassAnnotation(adn);
            }
        }

        if (mFields != null) {
            for (DexFieldNode dfn : mFields) {
                dcnv.visitField(dfn);
            }
        }

        if (mMethods != null) {
            for (DexMethodNode dmn : mMethods) {
                dcnv.visitMethod(dmn);
            }
        }

        dcnv.visitClassNodeEnd();
    }


    @Override
    public DexClassVisitor asVisitor() {

        return new DexClassVisitor() {

            @Override
            public void visitBegin() {
                super.visitBegin();
            }

            @Override
            public void visitSourceFile(DexString sourceFile) {
                DexClassNode.this.sourceFile = sourceFile;
            }

            @Override
            public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
                DexAnnotationNode dan = new DexAnnotationNode(annotationInfo);
                mAnnotations.add(dan);
                return dan.asVisitor();
            }

            @Override
            public DexFieldVisitor visitField(DexFieldVisitorInfo field) {
                DexFieldNode dfn = new DexFieldNode(field);
                mFields.add(dfn);
                return dfn.asVisitor();
            }

            @Override
            public DexMethodVisitor visitMethod(DexMethodVisitorInfo method) {
                DexMethodNode dmn = new DexMethodNode(method);
                mMethods.add(dmn);
                return dmn.asVisitor();
            }

            @Override
            public void visitExtraInfo(String key, Object extra) {
                DexClassNode.this.setExtraInfo(key, extra);
            }

            @Override
            public void visitEnd() {
                super.visitEnd();
            }

        };
    }

    private static void writeSmaliAccessFlags(SmaliWriter writer, int accessFlags) {
        for (DexAccessFlag accessFlag : DexAccessFlags.getAccessFlagForClass(accessFlags)) {
            writer.write(accessFlag.accessName);
            writer.write(' ');
        }
    }

    public void smaliTo(SmaliWriter writer) {
        // # write class
        writer.write(".class ");
        // # access flag
        writeSmaliAccessFlags(writer, accessFlags.getFlags());

        // # type descriptor
        writer.write(type.toTypeDescriptor());
        writer.write('\n');

        // # write super class
        if (superType != null) {
            writer.write(".super ");
            writer.write(superType.toTypeDescriptor());
            writer.write('\n');
        }

        // # write sourceFile
        if (sourceFile != null) {
            writer.write(".source ");
            writer.write('"');
            writer.write(sourceFile.toString());
            writer.write('"');
            writer.write('\n');
        }

        // # write interfaces
        if (interfaces != null && interfaces.count() > 0) {
            writer.write('\n');
            writer.write("#interfaces\n");
            for (DexType interfaceType: interfaces.types()) {
                writer.write(".implements ");
                writer.write(interfaceType.toTypeDescriptor());
                writer.write('\n');
            }
        }

        // # write annotations
        if (mAnnotations != null && mAnnotations.size() > 0) {
            writer.write('\n');
            writer.writeLine("# annotations");
            writer.newLine();

            mAnnotations.forEach(a -> a.smaliTo(writer));
        }

        // # write staticFields
        List<DexFieldNode> staticFields = this.mFields.stream()
                .filter(f -> f.accessFlags.containsOneOf(DexAccessFlags.ACC_STATIC))
                .collect(Collectors.toList());

        if (!staticFields.isEmpty()) {
            writer.write("\n\n");
            writer.write("# static fields");

            for (DexFieldNode field : staticFields) {
                writer.write('\n');

                field.smaliTo(writer);
            }
        }

        // # write instance fields
        List<DexFieldNode> instanceFields = this.mFields.stream()
                .filter(f -> f.accessFlags.containsNoneOf(DexAccessFlags.ACC_STATIC))
                .collect(Collectors.toList());

        if (!instanceFields.isEmpty()) {
            writer.write("\n\n");
            writer.writeLine("# instance fields");

            for (DexFieldNode field : instanceFields) {
                writer.write('\n');

                field.smaliTo(writer);
            }
        }

        // # write direct methods
        List<DexMethodNode> directMethods = this.mMethods.stream()
                .filter(m -> m.accessFlags.containsOneOf(DexAccessFlags.ACC_STATIC |
                        DexAccessFlags.ACC_PRIVATE | DexAccessFlags.ACC_CONSTRUCTOR))
                .sorted()
                .collect(Collectors.toList());

        if (!directMethods.isEmpty()) {
            writer.write("\n\n");
            writer.writeLine("# direct methods");

            for (DexMethodNode method : directMethods) {
                writer.write('\n');

                method.smaliTo(writer);
            }
        }

        // # write virtual methods
        List<DexMethodNode> virtualMethods = this.mMethods.stream()
                .filter(m -> m.accessFlags.containsNoneOf(DexAccessFlags.ACC_STATIC |
                        DexAccessFlags.ACC_PRIVATE | DexAccessFlags.ACC_CONSTRUCTOR))
                .sorted()
                .collect(Collectors.toList());

        if (!virtualMethods.isEmpty()) {
            writer.write("\n\n");
            writer.writeLine("# virtual methods");

            for (DexMethodNode method : virtualMethods) {
                writer.write('\n');

                method.smaliTo(writer);
            }
        }
    }

    public String toSmaliString() {
        StringWriter strWriter = new StringWriter();
        smaliTo(new SmaliWriter(strWriter));
        return strWriter.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (DexAccessFlag accessFlag :
                DexAccessFlags.getAccessFlagForClass(accessFlags.getFlags())) {
            sb.append(accessFlag.accessName);
            sb.append(' ');
        }
        sb.append(type.toTypeDescriptor());
        return sb.toString();
    }

    @Override
    public int compareTo(DexClassNode o) {
        return this.type.compareTo(o.type);
    }

}
