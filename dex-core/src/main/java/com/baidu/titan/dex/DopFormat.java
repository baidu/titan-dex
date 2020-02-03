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
 *
 * dop format, please ref https://source.android.com/devices/tech/dalvik/instruction-formats
 *
 * @author zhangdi07@baidu.com
 * @since 2018/4/23
 */
public class DopFormat {

    private final int mCodeSize;

    private final String mFormatName;

    public DopFormat(int codeSize, String formatName) {
        this.mCodeSize = codeSize;
        this.mFormatName = formatName;
    }

    public int codeSize() {
        return mCodeSize;
    }

    public String formatName() {
        return mFormatName;
    }

}
