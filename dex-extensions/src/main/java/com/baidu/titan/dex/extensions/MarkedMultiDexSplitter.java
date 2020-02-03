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
import com.baidu.titan.dex.util.DexIdsCollector;
import com.baidu.titan.dex.visitor.DexClassPoolNodeVisitor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通过之前标识的类的dexid完成分包
 *
 * @author zhangdi07@baidu.com
 * @since 2018/6/2
 */
public class MarkedMultiDexSplitter extends MultiDexSplitter {

    private DexIdsCollector.Cache mDexIdsCache = new DexIdsCollector.Cache();

    public MarkedMultiDexSplitter(DexClassPoolNode classPool) {
        super(classPool);
    }

    public static final int SPLIT_ERROR_DEX_ID_MISSING = -2;

    private static final String EXTRA_CLASS_DEXID = "_extra_dexId";

    public static int getDexIdForClassNode(DexClassNode dcn) {
        return dcn.getExtraInfo(EXTRA_CLASS_DEXID, -1);
    }

    public static void setDexIdForClassNode(DexClassNode dcn, int dexId) {
        dcn.setExtraInfo(EXTRA_CLASS_DEXID, dexId);
    }

    @Override
    protected DexFileNode createDexFileNode() {
        DexFileNode dexFileNode = new DexFileNode(true, mDexIdsCache);
        return dexFileNode;
    }



    @Override
    protected int doSplit() {

        DexClassPoolNode classPool = getClassPool();

        AtomicInteger result = new AtomicInteger(SPLIT_SUCCESS);

        classPool.accept(new DexClassPoolNodeVisitor() {

            @Override
            public void visitClass(DexClassNode dcn) {

                if (result.get() != SPLIT_SUCCESS) {
                    return;
                }

                int dexId = getDexIdForClassNode(dcn);
                if (dexId < 0) {
                    result.set(SPLIT_ERROR_DEX_ID_MISSING);
                } else {
                    DexFileNode dfn = getDexFile(dexId);
                    boolean success = dfn.addClass(dcn);
                    result.set(success ? SPLIT_SUCCESS : SPLIT_ERROR_TOO_MANY_MEMBER_IDS);
                }
            }

            @Override
            public void classPoolVisitEnd() {

            }
        });
        return result.get();
    }

}
