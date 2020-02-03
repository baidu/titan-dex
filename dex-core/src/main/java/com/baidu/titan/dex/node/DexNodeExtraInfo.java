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

import java.util.Map;

/**
 *
 * 所有实现该接口的Node节点都具有额外存储value的能力，同时必须实现getOrCreateExtraMap()方法
 *
 * @author zhangdi07
 * @since 2017/9/13
 */

public interface DexNodeExtraInfo {

    Map<String, Object> getOrCreateExtraMap();

    default Map<String, Object> getOrCreateExtraMapNotNull() {
        Map<String, Object> extraMap = getOrCreateExtraMap();
        if (extraMap == null) {
            throw new IllegalArgumentException("sub class must implement getOrCreateExtraMap() " +
                    "method, and return a map");
        }
        return extraMap;
    }

    default void setExtraInfo(String key, Object extra) {
        getOrCreateExtraMapNotNull().put(key, extra);
    }

    default <T extends Object> T getExtraInfo(String key, Object defExtra) {
        return (T)getOrCreateExtraMapNotNull().getOrDefault(key, defExtra);
    }

    default <T extends Object> T getExtraInfo(String key) {
        T value = (T)getOrCreateExtraMapNotNull().get(key);
        if (value != null) {
            return value;
        }
        throw new IllegalStateException(String.format("extra value for key %s does not exist", key));
    }

    default void clearAllExtraInfo() {
        getOrCreateExtraMapNotNull().clear();
    }


}
