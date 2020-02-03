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

package com.baidu.titan.dex.extensions;

import com.baidu.titan.dex.node.DexClassNode;
import com.baidu.titan.dex.visitor.DexClassPoolNodeVisitor;

/**
 * 通过DexNode的Extra机制标志一个类的类型（LIBRARY或者PROGRAM）
 *
 * @author zhangdi07@baidu.com
 * @since 2017/11/5
 */

public class DexClassKindMarker implements DexClassPoolNodeVisitor {

    private ClassKind mKind;

    public static final String EXTRA_KEY_CLASSKIND = "classkind";

    public enum ClassKind {

        CLASS_KIND_LIBRARY,
        CLASS_KIND_PROGRAM

    }

    public static ClassKind getClassKind(DexClassNode dcn) {
        return dcn.getExtraInfo(EXTRA_KEY_CLASSKIND, null);
    }

    public static boolean isLibraryClass(DexClassNode dcn) {
        return getClassKind(dcn) == ClassKind.CLASS_KIND_LIBRARY;
    }

    public static boolean isProgramClass(DexClassNode dcn) {
        return getClassKind(dcn) == ClassKind.CLASS_KIND_PROGRAM;
    }

    public static void setClassKind(DexClassNode dcn, ClassKind kind) {
        dcn.setExtraInfo(EXTRA_KEY_CLASSKIND, kind);
    }

    public DexClassKindMarker(ClassKind kind) {
        this.mKind = kind;
    }

    @Override
    public void visitClass(DexClassNode dcn) {
        dcn.setExtraInfo(EXTRA_KEY_CLASSKIND, mKind);
    }

    @Override
    public void classPoolVisitEnd() {

    }

}
