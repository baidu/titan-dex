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

package com.baidu.titan.dex.writer;

import com.baidu.titan.dex.DexFileBytes;
import com.baidu.titan.dex.MultiDexFileBytes;
import com.baidu.titan.dex.visitor.DexFileVisitor;
import com.baidu.titan.dex.visitor.MultiDexFileVisitor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author zhangdi07
 * @since 2017/9/18
 */

public class MultiDexFileWriter extends MultiDexFileVisitor {

    private MultiDexFileBytes mMultiDexFileBytes = new MultiDexFileBytes();

    public MultiDexFileWriter() {
        super();
    }

    @Override
    public DexFileVisitor visitDexFile(final int dexId) {
        System.out.println("writer dexid = " + dexId);
        DexFileWriter mdfw = new DexFileWriter() {
            @Override
            public void visitEnd() {
                super.visitEnd();
                mMultiDexFileBytes.addDexFileBytes(dexId, new DexFileBytes(this.toByteArray()));
            }
        };
        return mdfw;
    }

    public MultiDexFileBytes getMultiDexFileBytes() {
        return mMultiDexFileBytes;
    }

}
