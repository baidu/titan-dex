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

package com.baidu.titan.dex.analyze.types;

import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.node.DexClassNode;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/12/27
 */
public class UninitializedType extends RegType {

    private int allocationPc;

    public UninitializedType(int id, DexType dexType, DexClassNode classNode, int allocationPc) {
        super(id, dexType, classNode);
        this.allocationPc = allocationPc;
    }

    public int getAllocationPc() {
        return allocationPc;
    }

    @Override
    public boolean isUninitializedTypes() {
        return true;
    }

    @Override
    public boolean isNonZeroReferenceTypes() {
        return true;
    }
}
