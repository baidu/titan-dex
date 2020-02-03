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

package com.baidu.titan.dex.node;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/10/11
 */
public class DexNode implements DexNodeExtraInfo {


    /** store extra info */
    private HashMap<String, Object> mExtraMap;

    @Override
    public Map<String, Object> getOrCreateExtraMap() {
        if (mExtraMap == null) {
            mExtraMap = new HashMap<>();
        }
        return mExtraMap;
    }

    @Override
    public void clearAllExtraInfo() {
        mExtraMap = null;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
