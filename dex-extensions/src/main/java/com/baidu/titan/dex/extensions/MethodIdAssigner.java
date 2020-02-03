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

import com.baidu.titan.dex.DexAccessFlags;
import com.baidu.titan.dex.node.DexClassNode;
import com.baidu.titan.dex.node.DexMethodNode;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对类级别内部定义的方法，采用DexFile文件规范的形式进行排序，并分配method id。
 * <p> 这里的方法是区分direct和virtual方法的进行methid id分配的。
 *
 *
 * @author zhangdi07@baidu.com
 * @since 2018/4/23
 */
public class MethodIdAssigner {

    private static final int METHOD_TYPE_DIRECT = 0x1 << 16;

    private static final int METHOD_TYPE_VIRTUAL = 0x10 << 16;

    private static final String EXTRA_METHOD_ID = "_extra_method_id";

    public static void assignMethodId(DexClassNode dcn) {
        AtomicInteger methodIdx = new AtomicInteger(0);
        // sort direct method and assign id
        dcn.getMethods().stream()
                .filter(m -> m.accessFlags.containsOneOf(
                        DexAccessFlags.ACC_STATIC |
                                DexAccessFlags.ACC_PRIVATE |
                                DexAccessFlags.ACC_CONSTRUCTOR))
                .sorted()
                .forEachOrdered(m -> m.setExtraInfo(EXTRA_METHOD_ID,
                        METHOD_TYPE_DIRECT | methodIdx.getAndIncrement()));

        methodIdx.set(0);
        // sort virtual method and assign id
        dcn.getMethods().stream()
                .filter(m -> m.accessFlags.containsNoneOf(
                        DexAccessFlags.ACC_STATIC |
                                DexAccessFlags.ACC_PRIVATE |
                                DexAccessFlags.ACC_CONSTRUCTOR))
                .sorted()
                .forEachOrdered(m -> m.setExtraInfo(EXTRA_METHOD_ID,
                        METHOD_TYPE_VIRTUAL | methodIdx.getAndIncrement()));
    }

    /**
     *
     * 获取已经分配的methodId
     *
     * @param methodNode
     * @return
     */
    public static int getMethodId(DexMethodNode methodNode) {
        int methodId = methodNode.getExtraInfo(EXTRA_METHOD_ID, -1);
        if (methodId < 0) {
            throw new IllegalStateException("no method id");
        }
        return methodId;
    }

}
