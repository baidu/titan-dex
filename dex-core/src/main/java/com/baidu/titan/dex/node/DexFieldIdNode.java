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
import com.baidu.titan.dex.util.HashCodeEqualsHelper;

import java.util.Objects;

/**
 * @author zhangdi07@baidu.com
 * @since 2018/6/1
 */
public class DexFieldIdNode extends DexNamedFieldProtoNode {

    public final DexType owner;

    private HashCodeEqualsHelper mHashCodeHelper = new HashCodeEqualsHelper();

    public DexFieldIdNode(DexType owner, DexString name, DexType type) {
        super(name, type);
        this.owner = owner;
    }

    public DexFieldIdNode(DexFieldNode fieldNode) {
        super(fieldNode);
        this.owner = fieldNode.owner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DexFieldIdNode that = (DexFieldIdNode) o;
        return Objects.equals(owner, that.owner) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return mHashCodeHelper.computeHashCode(() -> computeHashCode());
    }

    private int computeHashCode() {
        return Objects.hash(DexFieldIdNode.super.hashCode(), owner);
    }

    @Override
    public String toString() {
        return "DexFieldIdNode{" +
                "owner=" + owner +
                ", type=" + type +
                ", name=" + name +
                '}';
    }
}
