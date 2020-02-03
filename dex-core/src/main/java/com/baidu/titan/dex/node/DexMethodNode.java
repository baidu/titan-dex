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
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.SmaliWriter;
import com.baidu.titan.dex.visitor.DexAnnotationVisitor;
import com.baidu.titan.dex.visitor.DexAnnotationVisitorInfo;
import com.baidu.titan.dex.visitor.DexCodeVisitor;
import com.baidu.titan.dex.visitor.DexMethodVisitor;
import com.baidu.titan.dex.visitor.DexMethodVisitorInfo;
import com.baidu.titan.dex.visitor.VisitorSupplier;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 方法节点
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/18
 */
public class DexMethodNode extends DexNode implements VisitorSupplier<DexMethodVisitor>, Comparable<DexMethodNode> {

    public DexString name;

    public DexAccessFlags accessFlags;

    public DexType owner;

    public DexTypeList parameters;

    public DexType returnType;

    private List<DexAnnotationNode> mMethodAnnotations = new ArrayList<>();

    private List<DexAnnotationNode>[] mParameterAnnotations;

    private DexCodeNode mDexCodeNode;

    public DexMethodNode(DexString name,
                         DexType owner,
                         DexTypeList parameters,
                         DexType returnType,
                         DexAccessFlags access) {
        this.name = name;
        this.accessFlags = access;
        this.owner = owner;
        this.parameters = parameters;
        this.returnType = returnType;
    }

    public DexMethodNode(DexMethodVisitorInfo methodInfo) {
        this(methodInfo.name, methodInfo.owner, methodInfo.parameters, methodInfo.returnType,
                methodInfo.accessFlags);
    }

    public boolean isInstanceInitMethod() {
        return "<init>".equals(name.toString());
    }

    public boolean isStaticInitMethod() {
        return "<clinit>".equals(name.toString());
    }

    public boolean isStatic() {
        return accessFlags.containsOneOf(DexAccessFlags.ACC_STATIC);
    }

    public boolean isDirectMethod() {
        return this.accessFlags.containsOneOf(DexAccessFlags.ACC_PRIVATE |
        DexAccessFlags.ACC_STATIC) || isInstanceInitMethod() || isStaticInitMethod();
    }

    public boolean isVirtualMethod() {
        return !isDirectMethod();
    }

    public DexCodeNode getCode() {
        return mDexCodeNode;
    }

    public void setCode(DexCodeNode code) {
        this.mDexCodeNode = code;
    }

    public List<DexAnnotationNode> getMethodAnnotations() {
        return mMethodAnnotations;
    }

    public void setMethodAnnotations(List<DexAnnotationNode> annotations) {
        this.mMethodAnnotations = annotations;
    }

    public void accept(DexMethodVisitor dmv) {
        dmv.visitBegin();
        if (mMethodAnnotations != null) {
            for (DexAnnotationNode dan : mMethodAnnotations) {
                DexAnnotationVisitor dav = dmv.visitAnnotation(
                        new DexAnnotationVisitorInfo(dan.getType(), dan.getVisibility()));
                if (dav != null) {
                    dan.accept(dav);
                }
            }
        }

        if (mParameterAnnotations != null) {
            for (int i = 0; i < mParameterAnnotations.length; i++) {
                List<DexAnnotationNode> pdans = mParameterAnnotations[i];
                if (pdans != null) {
                    for (DexAnnotationNode dan : pdans) {
                        DexAnnotationVisitor padv = dmv.visitParameterAnnotation(i,
                                new DexAnnotationVisitorInfo(dan.getType(), dan.getVisibility()));
                        if (padv != null) {
                            dan.accept(padv);
                        }
                    }
                }
            }
        }

        if (mDexCodeNode != null) {
            DexCodeVisitor dcv = dmv.visitCode();
            if (dcv != null) {
                mDexCodeNode.accept(dcv);
            }
        }

        dmv.visitEnd();
    }

    @Override
    public DexMethodVisitor asVisitor() {

        return new DexMethodVisitor() {

            @Override
            public void visitBegin() {
                super.visitBegin();
            }

            @Override
            public DexAnnotationVisitor visitAnnotationDefault() {
                return super.visitAnnotationDefault();
            }

            @Override
            public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
                DexAnnotationNode dan = new DexAnnotationNode(annotationInfo);
                mMethodAnnotations.add(dan);
                return dan.asVisitor();
            }

            @Override
            public DexAnnotationVisitor visitParameterAnnotation(int parameter, DexAnnotationVisitorInfo annotationInfo) {
                if (mParameterAnnotations == null) {
                    mParameterAnnotations = (List<DexAnnotationNode>[]) new List<?>[parameters.count()];
                }
                DexAnnotationNode adn = new DexAnnotationNode(annotationInfo);
                List<DexAnnotationNode> parameterAnnotations = mParameterAnnotations[parameter];
                if (parameterAnnotations == null) {
                    parameterAnnotations = new ArrayList<>();
                    mParameterAnnotations[parameter] = parameterAnnotations;
                }
                parameterAnnotations.add(adn);
                return adn.asVisitor();
            }

            @Override
            public DexCodeVisitor visitCode() {
                DexCodeNode dcn = new DexCodeNode();
                mDexCodeNode = dcn;
                return dcn.asVisitor();
            }

            @Override
            public void visitExtraInfo(String key, Object extra) {
                DexMethodNode.this.setExtraInfo(key, extra);
            }

            @Override
            public void visitEnd() {
                super.visitEnd();
            }

        };
    }

    private static void writeSmaliAccessFlags(SmaliWriter writer, int accessFlags) {
        for (DexAccessFlag accessFlag : DexAccessFlags.getAccessFlagForMethod(accessFlags)) {
            writer.write(accessFlag.accessName);
            writer.write(' ');
        }
    }

    public void smaliTo(SmaliWriter writer) {
        writer.write(".method ");
        writeSmaliAccessFlags(writer, this.accessFlags.getFlags());
        writer.write(name.toString());
        // write parameters
        writer.write("(");
        for (DexType para : parameters.types()) {
            writer.write(para.toTypeDescriptor());
        }
        writer.write(")");
        // write return type
        writer.write(returnType.toTypeDescriptor());

        writer.write('\n');

        // write code
        writer.indent(4);

        // # write parameters
        DexString[] parameterNames = mDexCodeNode != null ? mDexCodeNode.getParameterNames() : null;
        int nextParaIdx = isStatic() ? 0 : 1;
        for (int i = 0; i < parameters.count(); i++) {
            DexType paraType = parameters.getType(i);
            writer.write(".param p");
            writer.printSignedIntAsDec(nextParaIdx);
            if (parameterNames != null && parameterNames[i] != null) {
                writer.write(", ");
                writer.write('"');
                writer.write(parameterNames[i].toString());
                writer.write('"');
            }
            writer.write("    # ");
            writer.write(paraType.toTypeDescriptor());
            writer.write('\n');

            if (mParameterAnnotations != null && mParameterAnnotations[i] != null &&
                    mParameterAnnotations[i].size() > 0) {
                List<DexAnnotationNode> paraAnnotations = mParameterAnnotations[i];
                writer.indent(4);
                for (DexAnnotationNode annotation : paraAnnotations) {
                    annotation.smaliTo(writer);
                }

                writer.deindent(4);

                writer.writeLine(".end param");
            }

            nextParaIdx += paraType.isWideType() ? 2 : 1;
        }

        // # write method annotations
        List<DexAnnotationNode> methodAnnotions = mMethodAnnotations;
        if (methodAnnotions != null && methodAnnotions.size() > 0) {
            for (DexAnnotationNode annotation : methodAnnotions) {
                annotation.smaliTo(writer);
            }
        }

        // # write code items
        if (mDexCodeNode != null) {
            this.mDexCodeNode.smaliTo(writer, 0);
        }
        writer.deindent(4);
        writer.writeLine(".end method");
    }

    public String toSmaliString() {
        StringWriter writer = new StringWriter();
        smaliTo(new SmaliWriter(writer));
        return writer.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (DexAccessFlag accessFlag :
                DexAccessFlags.getAccessFlagForMethod(accessFlags.getFlags())) {

            sb.append(accessFlag.accessName);
            sb.append(' ');
        }

        sb.append(this.owner.toTypeDescriptor());
        sb.append("->");
        sb.append(this.name.toString());
        sb.append("(");
        if (parameters != null) {
            for (int i = 0; i < parameters.count(); i++) {
                sb.append(parameters.getType(i).toTypeDescriptor());
            }
        }
        sb.append(")");
        sb.append(this.returnType.toTypeDescriptor());
        return sb.toString();
    }

    @Override
    public int compareTo(DexMethodNode other) {
        // 定义类型为主要顺序
        int res = this.owner.compareTo(other.owner);
        // 方法名称为中间顺序
        if (res == 0) {
            res = this.name.compareTo(other.name);
        }
        // 方法原型为次要顺序

        // 方法原型
        // 方法原型：返回值为主要顺序
        if (res == 0) {
            res = this.returnType.compareTo(other.returnType);
        }
        // 方法原型：参数列表为第二顺序
        if (res == 0) {
            res = this.parameters.compareTo(other.parameters);
        }

        return res;
    }
}
