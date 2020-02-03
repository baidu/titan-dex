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

package com.baidu.titan.dex;

/**
 * @author zhangdi07
 * @since 2017/10/28
 */

public class DexType implements Comparable<DexType> {

    private DexString mTypeDescriptor;

    public DexType(DexString typeDesc) {
        if (typeDesc == null) {
            throw new IllegalArgumentException("typeDesc cannot null");
        }
        this.mTypeDescriptor = typeDesc;
    }

    public DexType(String typeDesc) {
        this(new DexString(typeDesc));
    }

    public String toTypeDescriptor() {
        return mTypeDescriptor.toString();
    }

    public char toShortDescriptor() {
        return toTypeDescriptor().charAt(0);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DexType)) {
            return false;
        }
        return mTypeDescriptor.equals(((DexType)o).mTypeDescriptor);
    }

    @Override
    public int hashCode() {
        return mTypeDescriptor.hashCode();
    }

    @Override
    public String toString() {
        return toTypeDescriptor();
    }

    @Override
    public int compareTo(DexType o) {
        return this.mTypeDescriptor.compareTo(o.mTypeDescriptor);
    }

    public boolean isArrayType() {
        return toShortDescriptor() == '[';
    }

    public boolean isVoidType() {
        return toShortDescriptor() == 'V';
    }

    /**
     * 是否是引用类型，不包括数组类型
     * @return
     */
    public boolean isReferenceType() {
        return toShortDescriptor() == 'L';
    }

    public boolean isPrimitiveType() {
        return !isReferenceType() && !isArrayType();
    }

    public boolean isWideType() {
        if (!isPrimitiveType()) {
            return false;
        }
        switch (toShortDescriptor()) {
            case DexItemFactory.LongClass.SHORT_DESCRIPTOR:
            case DexItemFactory.DoubleClass.SHORT_DESCRIPTOR: {
                return true;
            }
        }
        return false;
    }

}
