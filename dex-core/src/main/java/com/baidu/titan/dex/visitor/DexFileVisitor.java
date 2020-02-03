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

import com.baidu.titan.dex.DexFileVersion;

/**
 * Dex File Visitor。<br>
 * <p>
 * 访问顺序：<br>
 * <ul>
 * <li> visit </li>
 * <li> visitClass </li>
 * <li> visitEnd </li>
 * </ul>
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/6
 */
public class DexFileVisitor implements VisitorExtraInfo {

    protected DexFileVisitor delegate;

    public DexFileVisitor(DexFileVisitor delegate) {
        this.delegate = delegate;
    }

    public DexFileVisitor() {
        this(null);
    }

    /**
     * 开始访问DexFile
     *
     *
     */
    public void visitBegin() {
        if (delegate != null) {
            delegate.visitBegin();
        }
    }

    public void visitDexVersion(DexFileVersion version) {
        if (delegate != null) {
            delegate.visitDexVersion(version);
        }
    }

    public DexClassVisitor visitClass(DexClassVisitorInfo classInfo) {
        if (delegate != null) {
            return delegate.visitClass(classInfo);
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
