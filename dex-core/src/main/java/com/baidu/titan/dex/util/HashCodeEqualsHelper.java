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
 * @author zhangdi07@baidu.com
 * @since 2018/6/3
 */
public class HashCodeEqualsHelper {

    private int mCachedHashCode = HASH_CODE_COMPUTE_UNINIT;

    private static final int HASH_CODE_COMPUTE_UNINIT = -1;

    private static final int HASH_CODE_COMPUTE_FINISHED = 0;

    public HashCodeEqualsHelper() {

    }

    public boolean hasCached() {
        return mCachedHashCode == HASH_CODE_COMPUTE_FINISHED;
    }

    public int getCachedHashCode() {
        return mCachedHashCode;
    }

    public void cacheHashCode(int hashCode) {
        this.mCachedHashCode = hashCode;
    }

    public interface HashCodeComputer {

        int onComputeHashCode();

    }

    public int computeHashCode(HashCodeComputer c) {
        if (mCachedHashCode == HASH_CODE_COMPUTE_UNINIT) {
            mCachedHashCode = c.onComputeHashCode();
        }
        return mCachedHashCode;
    }


}
