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

package com.baidu.titan.dex.util;

/**
 *
 * flag helper
 *
 * @author zhangdi07@baidu.com
 * @since 2018/1/30
 */
public class Flags {

    private int mFlags;

    public Flags(int flags) {
        this.mFlags = flags;
    }

    public Flags(int... flags) {
        this(combineFlags(flags));
    }

    private static int combineFlags(int[] flags) {
        int combined = 0;
        for (int flag : flags) {
            combined |= flag;
        }
        return combined;
    }

    public int getFlags() {
        return mFlags;
    }

    public void setFlags(int flags) {
        this.mFlags = flags;
    }

    public void appendFlags(int flags) {
        this.mFlags |= flags;
    }

    public void clearFlags(int flags) {
        mFlags &= ~flags;
    }

    public void changeFlags(int flags, boolean set) {
        if (set) {
            appendFlags(flags);
        } else {
            clearFlags(flags);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Flags) {
            return mFlags == ((Flags) other).mFlags;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getFlags();
    }

    public boolean containsAllOf(int expected) {
        return (mFlags & expected) == expected;
    }

    public boolean containsNoneOf(int unExpected) {
        return (mFlags & unExpected) == 0;
    }

    public boolean containsOneOf(int expected) {
        return (mFlags & expected) != 0;
    }

}
