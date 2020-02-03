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

import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;

import java.util.Objects;

/**
 * @author zhangdi07@baidu.com
 * @since 2018/6/1
 */
public class DexNamedFieldProtoNode extends DexNode {

    public final DexType type;

    public final DexString name;

    public DexNamedFieldProtoNode(DexString name, DexType type) {
        this.name = name;
        this.type = type;
    }

    public DexNamedFieldProtoNode(DexFieldNode fieldNode) {
        this(fieldNode.name, fieldNode.type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DexNamedFieldProtoNode that = (DexNamedFieldProtoNode) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }
}
