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
 * 至少有两种早期版本的格式已在广泛提供的公开软件版本中使用。例如，009 版本已用于 M3 版 Android 平台（2007 年 11 月至 12 月），013 版本已用于 M5 版 Android 平台（2008 年 2 月至 3 月）。在有些方面，这些早期版本的格式与本文档中所述的版本存在很大差异。
 *
 * Android 8.0 版本中新增了对 038 版格式的支持。038 版本中添加了新字节码（invoke-polymorphic 和 invoke-custom）和用于方法句柄的数据。
 *
 * Android 7.0 版本中新增了对 037 版格式的支持。在 037 版本之前，大多数 Android 版本都使用过 035 版格式。035 版与 037 版之间的唯一区别是，是否添加默认方法以及是否调整 invoke。
 *
 * @author zhangdi07@baidu.com
 * @since 2019/1/9
 */
public final class DexFileVersion {

    public final int androidApiLevel;

    public final String dexVersion;

    public static final DexFileVersion ANDROID_API_3_2 = new DexFileVersion(4, "035");

    public static final DexFileVersion ANDROID_API_7_0 = new DexFileVersion(24, "037");

    public static final DexFileVersion ANDROID_API_8_0 = new DexFileVersion(26, "038");

    public static final DexFileVersion ANDROID_API_9_0 = new DexFileVersion(28, "039");

    public static final DexFileVersion LATEST_VERSION = ANDROID_API_9_0;

    private DexFileVersion(int androidSDKInt, String dexVersion) {
        this.androidApiLevel = androidSDKInt;
        this.dexVersion = dexVersion;
    }

    public static DexFileVersion[] VERSIONS = new DexFileVersion[] {
            ANDROID_API_3_2,
            ANDROID_API_7_0,
            ANDROID_API_8_0,
            ANDROID_API_9_0 };

    public static DexFileVersion getVersion(String dexVersion) {
        for (DexFileVersion version : VERSIONS) {
            if (version.dexVersion.equals(dexVersion)) {
                return version;
            }
        }
        throw new IllegalArgumentException("unsupported dex version " + dexVersion);
    }

}
