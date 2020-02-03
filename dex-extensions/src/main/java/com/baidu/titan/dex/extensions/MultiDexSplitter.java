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
import com.baidu.titan.dex.node.MultiDexFileNode;

/**
 *
 * 多Dex分包器：因为一个Dex文件的索引有65K限制，该类是为了而引入的
 *
 * @author zhangdi07@baidu.com
 * @since 2018/5/26
 */
public abstract class MultiDexSplitter {

    private DexClassPoolNode mDexClassPool;

    private MultiDexFileNode mMultiDexFileNode = new MultiDexFileNode();

    public MultiDexSplitter(DexClassPoolNode classPool) {
        this.mDexClassPool = classPool;
    }

    public static final int SPLIT_SUCCESS = 0;

    public static final int SPLIT_ERROR_TOO_MANY_MEMBER_IDS = -1;

    public int split() {
        return doSplit();
    }

    protected abstract int doSplit();

    protected void addClassToDexFile(DexFileNode dexFileNode, DexClassNode dcn) {
        dexFileNode.addClass(dcn);
    }

    protected DexFileNode getDexFile(int dexId) {
        DexFileNode dexFileNode = mMultiDexFileNode.getDexNodes().get(dexId);
        if (dexFileNode == null) {
            dexFileNode = createDexFileNode();
            mMultiDexFileNode.addDexFile(dexId, dexFileNode);
        }
        return dexFileNode;
    }

    protected DexClassPoolNode getClassPool() {
        return mDexClassPool;
    }

    protected abstract DexFileNode createDexFileNode();

    public MultiDexFileNode getMultiDexFileNode() {
        return mMultiDexFileNode;
    }

}
