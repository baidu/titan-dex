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
import com.baidu.titan.dex.node.DexClassPoolNode;
import com.baidu.titan.dex.node.DexFileNode;
import com.baidu.titan.dex.visitor.DexClassPoolNodeVisitor;
import com.baidu.titan.dex.visitor.MultiDexFileNodeVisitor;

/**
 * 实现了DexClassPoolNodeVisitor和MultiDexFileNodeVisitor, 向DexClassPoolNode填充类节点
 *
 * @author zhangdi07@baidu.com
 * @since 2017/9/13
 */

public class DexClassPoolNodeFiller implements DexClassPoolNodeVisitor, MultiDexFileNodeVisitor {

    private final DexClassPoolNode mClassPool;

    private int mDexId;

    private boolean mMarkDexId;

    private boolean mMultidexVisitMode = false;

    public DexClassPoolNodeFiller(DexClassPoolNode dcp) {
        this(dcp, false);
    }

    public DexClassPoolNodeFiller(DexClassPoolNode dcp, boolean markDexId) {
        this.mClassPool = dcp;
        this.mMarkDexId = markDexId;
    }

    @Override
    public void visitClass(DexClassNode dcn) {
        if (mMultidexVisitMode && mMarkDexId) {
            MarkedMultiDexSplitter.setDexIdForClassNode(dcn, mDexId);
        }
        this.mClassPool.addClass(dcn);
    }

    @Override
    public void classPoolVisitEnd() {

    }

    @Override
    public void visitDexFile(int dexId, DexFileNode dfn) {
        mMultidexVisitMode = true;
        mDexId = dexId;
        dfn.accept(this);
    }

}
