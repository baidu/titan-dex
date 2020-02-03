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

package com.baidu.titan.dex.reader;

import com.baidu.titan.dex.DexItemFactory;
import com.baidu.titan.dex.visitor.DexFileVisitor;
import com.baidu.titan.dex.visitor.MultiDexFileVisitor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author zhangdi07
 * @since 2017/9/13
 */

public class MultiDexFileReader {

    private LinkedHashMap<Integer, byte[]> mDexFiles = new LinkedHashMap<>();

    private DexItemFactory mFactory;

    public MultiDexFileReader() {
        this(null);
    }

    public MultiDexFileReader(DexItemFactory factory) {
        this.mFactory = factory;
    }

    public MultiDexFileReader addDexContent(int dexId, byte[] content) {
        mDexFiles.put(dexId, content);
        return this;
    }

    public void accept(MultiDexFileVisitor mdfv) {
        for (Map.Entry<Integer, byte[]> entry: mDexFiles.entrySet()) {
            int dexId = entry.getKey();
            byte[] content = entry.getValue();

            DexFileVisitor dfv = mdfv.visitDexFile(dexId);
            if (dfv != null) {
                DexFileReader dfr = new DexFileReader(content, this.mFactory);
                dfr.accept(dfv);
            }
        }
    }


}
