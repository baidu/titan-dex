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
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.util.HashCodeEqualsHelper;

import java.util.Objects;

/**
 * @author zhangdi07@baidu.com
 * @since 2018/6/1
 */
public class DexMethodIdNode extends DexNamedProtoNode {

    public final DexType owner;

    private final HashCodeEqualsHelper mHashCodeHelper = new HashCodeEqualsHelper();

    public DexMethodIdNode(DexType owner,
                           DexString name,
                           DexTypeList parameters,
                           DexType returnType) {
        super(name, parameters, returnType);
        this.owner = owner;
    }

    public DexMethodIdNode(DexMethodNode methodNode) {
        super(methodNode);
        this.owner = methodNode.owner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DexMethodIdNode that = (DexMethodIdNode) o;
        return Objects.equals(owner, that.owner);
    }

    @Override
    public int hashCode() {
        return mHashCodeHelper.computeHashCode(() ->
                Objects.hash(DexMethodIdNode.super.hashCode(), owner));
    }

    @Override
    public String toString() {
        return "DexMethodIdNode{" +
                "owner=" + owner +
                ", name=" + name +
                ", returnType=" + returnType +
                ", parameters=" + parameters +
                '}';
    }
}
