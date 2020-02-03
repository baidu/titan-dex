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

import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/11/17
 */
public class DexProtoNode extends DexNode {

    public final DexType returnType;

    public final DexTypeList parameters;


    public DexProtoNode(DexTypeList parameters, DexType returnType) {
        this.parameters = parameters;
        this.returnType = returnType;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DexProtoNode)) {
            return false;
        }
        DexProtoNode other = (DexProtoNode)o;
        return returnType.equals(other.returnType)
                && parameters.equals(other.parameters);
    }

    @Override
    public int hashCode() {
        int result = returnType.hashCode();
        result = 31 * result + parameters.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        parameters.forEach(t -> sb.append(t.toTypeDescriptor()));
        sb.append(")");
        sb.append(returnType.toTypeDescriptor());
        return sb.toString();
    }

}
