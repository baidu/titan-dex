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

package com.baidu.titan.dex.visitor;

import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;

/**
 * Dex Annotation Visitor。<br>
 * <p>
 * 访问顺序：(具体顺序与Annotation本身格式有关)<br>
 * <ul>
 * <li> visit * </li>
 * <li> visitEnd </li>
 * </ul>
 *
 * @author zhangdi07@baidu.com
 * @since 2017/1/6
 */
public class DexAnnotationVisitor implements VisitorExtraInfo {

    protected DexAnnotationVisitor delegate;

    public DexAnnotationVisitor() {

    }

    public DexAnnotationVisitor(DexAnnotationVisitor delegate) {
        this.delegate = delegate;
    }

    public void visitBegin() {
        if (delegate != null) {
            delegate.visitBegin();
        }
    }

    /**
     * 访问基本数据类型
     *
     * @param name
     * @param value
     */
    public void visitPrimitive(DexString name, Object value) {
        if (delegate != null) {
            delegate.visitPrimitive(name, value);
        }
    }

    /**
     * 访问字符串
     *
     * @param name
     * @param value
     */
    public void visitString(DexString name, DexString value) {
        if (delegate != null) {
            delegate.visitString(name, value);
        }
    }

    /**
     * 访问枚举类型
     *
     * @param name
     * @param enumType
     * @param enumName
     */
    public void visitEnum(DexString name, DexType enumType, DexString enumName) {
        if (delegate != null) {
            delegate.visitEnum(name, enumType, enumName);
        }
    }

    /**
     * 访问内嵌的注解类型
     *
     * @param name
     * @param type
     * @return
     */
    public DexAnnotationVisitor visitAnnotation(DexString name, DexType type) {
        if (delegate != null) {
            return delegate.visitAnnotation(name, type);
        }
        return null;
    }

    /**
     * 访问数据类型
     *
     * @param name
     * @return
     */
    public DexAnnotationVisitor visitArray(DexString name) {
        if (delegate != null) {
            return delegate.visitArray(name);
        }
        return null;
    }

    public void visitMethod(DexString name, DexConst.ConstMethodRef methodRef) {
        if (delegate != null) {
            delegate.visitMethod(name, methodRef);
        }
    }

    public void visitField(DexString name, DexConst.ConstFieldRef fieldRef) {
        if (delegate != null) {
            delegate.visitField(name, fieldRef);
        }
    }

    /**
     * 访问类类型
     *
     * @param name
     * @param type
     */
    public void visitType(DexString name, DexType type) {
        if (delegate != null) {
            delegate.visitType(name, type);
        }
    }

    public void visitNull(DexString name) {
        if (delegate != null) {
            delegate.visitNull(name);
        }
    }

    /**
     * 访问结束
     */
    public void visitEnd() {
        if (delegate != null) {
            delegate.visitEnd();
        }
    }
}
