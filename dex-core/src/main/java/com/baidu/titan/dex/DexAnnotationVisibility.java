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
 * @since 2017/10/31
 */

public class DexAnnotationVisibility {

    private String mName;

    private int mVisibility;

    public DexAnnotationVisibility(int visibility, String name) {
        this.mVisibility = visibility;
        this.mName = name;
    }

    public String getName() {
        return mName;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof DexAnnotationVisibility) {
            return mVisibility == ((DexAnnotationVisibility) other).mVisibility;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return get();
    }

    public int get() {
        return mVisibility;
    }

//    public boolean containsAllOf(DexAnnotationVisibility other) {
//        return (mVisibility & other.get()) == other.get();
//    }
//
//    public boolean containsAllOf(int visibility) {
//        return (mVisibility & visibility) == visibility;
//    }
//
//    public boolean containsNoneOf(DexAnnotationVisibility other) {
//        return (mVisibility & other.get()) == 0;
//    }

}
