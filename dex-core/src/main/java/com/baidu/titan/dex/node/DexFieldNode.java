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
import com.baidu.titan.dex.SmaliWriter;
import com.baidu.titan.dex.visitor.DexAnnotationVisitor;
import com.baidu.titan.dex.visitor.DexAnnotationVisitorInfo;
import com.baidu.titan.dex.visitor.DexFieldVisitor;
import com.baidu.titan.dex.visitor.DexFieldVisitorInfo;
import com.baidu.titan.dex.visitor.VisitorSupplier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表示一个字段的节点信息
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/9
 */
public class DexFieldNode extends DexNode implements VisitorSupplier<DexFieldVisitor>,
        Comparable<DexFieldNode> {

    public DexAccessFlags accessFlags;

    public DexString name;

    public DexType type;

    public DexType owner;

    public Object staticValue;

    private List<DexAnnotationNode> mAnnotations = new ArrayList<>();

    public DexFieldNode(DexString name, DexType type, DexType owner, DexAccessFlags access) {
        this.accessFlags = access;
        this.name = name;
        this.type = type;
        this.owner = owner;
    }

    public DexFieldNode(DexFieldVisitorInfo fieldInfo) {
        this(fieldInfo.name, fieldInfo.type, fieldInfo.owner, fieldInfo.accessFlags);
    }

    public List<DexAnnotationNode> getFieldAnnotations() {
        return mAnnotations;
    }

    public void setFieldAnnotations(List<DexAnnotationNode> annotations) {
        this.mAnnotations = annotations;
    }

    public boolean isStatic() {
        return this.accessFlags.containsOneOf(DexAccessFlags.ACC_STATIC);
    }

    public boolean isStaticFinal() {
        return this.accessFlags.containsAllOf(DexAccessFlags.ACC_STATIC | DexAccessFlags.ACC_FINAL);
    }

    public void accept(DexFieldVisitor dfv) {
        dfv.visitBegin();
        for (DexAnnotationNode dan : mAnnotations) {
            DexAnnotationVisitor dav = dfv.visitAnnotation(
                    new DexAnnotationVisitorInfo(dan.getType(), dan.getVisibility()));
            if (dav != null) {
                dan.accept(dav);
            }
        }

        if (staticValue != null) {
            dfv.visitStaticValue(staticValue);
        }

        dfv.visitEnd();
    }


    @Override
    public DexFieldVisitor asVisitor() {
        return new DexFieldVisitor() {
            @Override
            public void visitBegin() {
                super.visitBegin();
            }

            @Override
            public void visitStaticValue(Object staticValue) {
                DexFieldNode.this.staticValue = staticValue;
            }

            @Override
            public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotation) {
                DexAnnotationNode dan = new DexAnnotationNode(annotation);
                mAnnotations.add(dan);
                return dan.asVisitor();
            }

            @Override
            public void visitExtraInfo(String key, Object extra) {
                DexFieldNode.this.setExtraInfo(key, extra);
            }

            @Override
            public void visitEnd() {
                super.visitEnd();
            }
        };
    }

    private static void writeAccessFlags(SmaliWriter writer, int accessFlags) {
        for (DexAccessFlag accessFlag : DexAccessFlags.getAccessFlagForField(accessFlags)) {
            writer.write(accessFlag.accessName);
            writer.write(' ');
        }
    }

    public void smaliTo(SmaliWriter writer) {
        writer.write(".field ");
        writeAccessFlags(writer, this.accessFlags.getFlags());
        writer.write(name.toString());
        writer.write(':');
        writer.write(type.toTypeDescriptor());
        if (staticValue != null) {
            writer.write(" = ");
            // String 类型需要加双引号""
            if ("Ljava/lang/String;".equals(type.toTypeDescriptor())) {
                writer.write(String.format("\"%s\"", staticValue.toString()));
            } else {
                writer.write(staticValue.toString());
            }
        }

        writer.write('\n');

        if (!mAnnotations.isEmpty()) {
            writer.indent(4);

            mAnnotations.forEach(a -> a.smaliTo(writer));

            writer.deindent(4);

            // 如果一个字段没有注解的话，则可以省略.end field
            writer.write(".end field\n");
        }
    }


    @Override
    public int compareTo(DexFieldNode other) {
        // 定义类型是主要顺序
        int res = this.owner.compareTo(other.owner);
        if (res == 0) {
            // 字段名称是中间顺序
            res = this.name.compareTo(other.name);
        }

        if (res == 0) {
            // 类型是次要顺序
            res = this.type.compareTo(other.type);
        }

        return res;
    }

    @Override
    public String toString() {
        return owner.toTypeDescriptor() + " -> (" + type.toTypeDescriptor() + ") "
                + name.toString() + (staticValue == null ? "" : " = " + staticValue.toString());
    }
}
