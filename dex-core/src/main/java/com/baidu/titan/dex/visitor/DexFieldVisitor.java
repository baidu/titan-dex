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

/**
 * Dex Field Visitor。<br>
 * <p>
 * 访问顺序：<br>
 * <ul>
 * <li> visitAnnotation </li>
 * <li> visitEnd </li>
 * </ul>
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/7
 */
public class DexFieldVisitor implements VisitorExtraInfo {

    protected DexFieldVisitor delegate;

    public DexFieldVisitor() {
        this(null);
    }

    public DexFieldVisitor(DexFieldVisitor delegate) {
        this.delegate = delegate;
    }

    /**
     * 开始访问
     */
    public void visitBegin() {
        if (delegate != null) {
            delegate.visitBegin();
        }
    }

    public void visitStaticValue(Object staticValue) {
        if (delegate != null) {
            delegate.visitStaticValue(staticValue);
        }
    }

    /**
     * visit field annotation
     *
     * @param annotation
     * @return
     */
    public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotation) {
        if (delegate != null) {
            return delegate.visitAnnotation(annotation);
        }
        return null;
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
