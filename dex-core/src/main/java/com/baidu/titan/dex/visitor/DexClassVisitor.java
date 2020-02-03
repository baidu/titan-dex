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

import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;

/**
 * Dex Class Visitor。<br>
 * <p>
 * 访问顺序：<br>
 * <ul>
 * <li> visitAnnotation </li>
 * <li> visitField </li>
 * <li> visitMethod </li>
 * <li> visitEnd </li>
 * </ul>
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/6
 */
public class DexClassVisitor implements VisitorExtraInfo {

    protected DexClassVisitor delegate;

    public DexClassVisitor(DexClassVisitor delegate) {
        this.delegate = delegate;
    }

    public DexClassVisitor() {
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

    public void visitSourceFile(DexString sourceFile) {
        if (delegate != null) {
            delegate.visitSourceFile(sourceFile);
        }
    }

    public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
        if (delegate != null) {
            return delegate.visitAnnotation(annotationInfo);
        }
        return null;
    }

    public DexFieldVisitor visitField(DexFieldVisitorInfo fieldInfo) {
        if (delegate != null) {
            return delegate.visitField(fieldInfo);
        }
        return null;
    }

    public DexMethodVisitor visitMethod(DexMethodVisitorInfo methodInfo) {
        if (delegate != null) {
            return delegate.visitMethod(methodInfo);
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
