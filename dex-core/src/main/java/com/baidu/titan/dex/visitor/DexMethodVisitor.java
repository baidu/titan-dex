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
 * Dex Method Visitor。<br>
 * <p>
 * 访问顺序：<br>
 * <ul>
 * <li> visitAnnotation </li>
 * <li> visitParameterAnnotation </li>
 * <li> visitCode </li>
 * <li> visitEnd </li>
 * </ul>
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/7
 */
public class DexMethodVisitor implements VisitorExtraInfo {

    protected DexMethodVisitor delegate;

    public DexMethodVisitor(DexMethodVisitor delegate) {
        this.delegate = delegate;
    }

    public DexMethodVisitor() {
        this(null);
    }

    /**
     * 访问开始
     */
    public void visitBegin() {
        if (delegate != null) {
            delegate.visitBegin();
        }
    }

    /**
     * 访问默认注解，用于注解类本身
     *
     * @return
     */
    public DexAnnotationVisitor visitAnnotationDefault() {
        if (delegate != null) {
            return delegate.visitAnnotationDefault();
        }
        return null;
    }

    public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
        if (delegate != null) {
            return delegate.visitAnnotation(annotationInfo);
        }
        return null;
    }


    public DexAnnotationVisitor visitParameterAnnotation(int parameter,
                                                         DexAnnotationVisitorInfo annotationInfo) {
        if (delegate != null) {
            return delegate.visitParameterAnnotation(parameter, annotationInfo);
        }
        return null;
    }

    /**
     * 访问代码
     *
     * @return
     */
    public DexCodeVisitor visitCode() {
        if (delegate != null) {
            return delegate.visitCode();
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
