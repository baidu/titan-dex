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

import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.node.DexClassNode;
import com.baidu.titan.dex.node.DexClassPoolNode;
import com.baidu.titan.dex.node.DexFileNode;
import com.baidu.titan.dex.util.DexIdsCollector;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 *
 * 该类的分包策略是尽可能的在每个Dex中多填充Class。
 *
 *
 *
 * 特别的，如果设置minimalMainDex为true的话，则只会根据mainDexList填充Class
 *
 * @author zhangdi07@baidu.com
 * @since 2018/5/26
 */
public class BestEffortMultiDexSplitter extends MultiDexSplitter {

    private Set<DexType> mMainDexTypeList;

    private boolean mMinimalMainDex = false;

    private DexIdsCollector.Cache mDexIdsCache = new DexIdsCollector.Cache();

    public BestEffortMultiDexSplitter(DexClassPoolNode classPool,
                                      Set<DexType> mainDexTypeList,
                                      boolean minimalMainDex) {
        super(classPool);
        if ((mainDexTypeList == null || mainDexTypeList.isEmpty()) && minimalMainDex) {
            throw new IllegalArgumentException("");
        }
        this.mMainDexTypeList = mainDexTypeList;
        this.mMinimalMainDex = minimalMainDex;
    }

    @Override
    protected int doSplit() {
        DexClassPoolNode classPoolNode = getClassPool();

        AtomicInteger result = new AtomicInteger(SPLIT_SUCCESS);

        if (this.mMainDexTypeList != null && this.mMainDexTypeList.size() > 0) {
            DexFileNode mainDexFile = getDexFile(1);
            classPoolNode.stream()
                    .filter(dcn -> this.mMainDexTypeList.contains(dcn.type))
                    .forEach(dcn -> {
                        if (result.get() == SPLIT_SUCCESS) {
                            boolean success = mainDexFile.addClass(dcn);
                            result.set(success ? SPLIT_SUCCESS : SPLIT_ERROR_TOO_MANY_MEMBER_IDS);
                        }
                    });

            if (result.get() != SPLIT_SUCCESS) {
                return result.get();
            }
        }


        List<DexClassNode> nonMainClasses = classPoolNode.stream()
                .filter(dcn -> this.mMainDexTypeList == null
                        || !this.mMainDexTypeList.contains(dcn.type))
                .sorted(Comparator.comparing(dcn -> {
                    String typeDesc = dcn.type.toTypeDescriptor();
                    return typeDesc.replace('$', '0');
                }))
                .collect(Collectors.toList());

        int nextDexId = mMinimalMainDex ? 2 : 1;
        DexFileNode nextDexFileNode = getDexFile(nextDexId);

        for (DexClassNode dcn : nonMainClasses) {
            if (!nextDexFileNode.addClass(dcn)) {
                nextDexFileNode = getDexFile(++nextDexId);
                // huge class!!!
                if (!nextDexFileNode.addClass(dcn)) {
                    return SPLIT_ERROR_TOO_MANY_MEMBER_IDS;
                }
            }
        }
        return SPLIT_SUCCESS;
    }

    @Override
    protected DexFileNode createDexFileNode() {
        DexFileNode dexFileNode = new DexFileNode(true, mDexIdsCache);
        return dexFileNode;
    }

}
