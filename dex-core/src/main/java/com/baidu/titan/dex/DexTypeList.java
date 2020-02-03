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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Type list
 *
 * @author zhangdi07@baidu.com
 * @since 2017/10/10
 */

public class DexTypeList implements Comparable<DexTypeList>, Iterable<DexType> {

    private static final DexTypeList EMPTY = new DexTypeList(new DexType[0]);

    private final DexType[] mTypes;

    public DexTypeList(DexType[] types) {
        this.mTypes = types;
    }

    public DexType[] types() {
        return mTypes;
    }

    public int count() {
        return mTypes == null ? 0 : mTypes.length;
    }

    public DexType getType(int i) {
        return mTypes[i];
    }

    public static DexTypeList empty() {
        return EMPTY;
    }

    public static class Builder {

        private List<DexType> mTypes = new ArrayList<>();

        public Builder addType(DexType type) {
            this.mTypes.add(type);
            return this;
        }

        public DexTypeList build() {
            if (mTypes.size() == 0) {
                return DexTypeList.empty();
            }
            DexType[] typeArray = new DexType[mTypes.size()];
            mTypes.toArray(typeArray);
            return new DexTypeList(typeArray);
        }

    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DexTypeList)) {
            return false;
        }
        return Arrays.equals(mTypes, ((DexTypeList)o).mTypes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mTypes);
    }

    @Override
    public String toString() {
        return "types {" + Arrays.toString(mTypes) + '}';
    }

    @Override
    public int compareTo(DexTypeList other) {
        for (int i = 0; i <= Math.min(mTypes.length, other.mTypes.length); i++) {
            if (i == mTypes.length) {
                return i == other.mTypes.length ? 0 : -1;
            } else if (i == other.mTypes.length) {
                return 1;
            } else {
                int result = mTypes[i].compareTo(other.mTypes[i]);
                if (result != 0) {
                    return result;
                }
            }
        }
        return 0;
    }

    @Override
    public Iterator<DexType> iterator() {
        return new It();
    }

    private class It implements Iterator<DexType> {

        private int mIdx = 0;

        @Override
        public boolean hasNext() {
            return mIdx != count();
        }

        @Override
        public DexType next() {
            return getType(mIdx++);
        }

    }


}
