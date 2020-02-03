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

import com.baidu.titan.dex.visitor.DexFileVisitor;
import com.baidu.titan.dex.visitor.MultiDexFileNodeVisitor;
import com.baidu.titan.dex.visitor.MultiDexFileVisitor;
import com.baidu.titan.dex.visitor.VisitorSupplier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 *
 * 包含多个DexFile，每个DexFile都对应一个1-based索引。
 *
 * @author zhangdi07
 * @since 2017/9/13
 */

public class MultiDexFileNode extends DexNode implements VisitorSupplier<MultiDexFileVisitor> {

    private LinkedHashMap<Integer, DexFileNode> mDexFiles = new LinkedHashMap<>();

    public MultiDexFileNode() {
    }

    public void addDexFile(int dexId, DexFileNode dexFileNode) {
        mDexFiles.put(dexId, dexFileNode);
    }

    public Map<Integer, DexFileNode> getDexNodes() {
        return mDexFiles;
    }

    public void accept(MultiDexFileNodeVisitor visitor) {
        for (Map.Entry<Integer, DexFileNode> entry : mDexFiles.entrySet()) {
            visitor.visitDexFile(entry.getKey(), entry.getValue());
        }
    }

    public void accept(MultiDexFileNodeVisitor visitor, ExecutorService executors) {
        List<Future<?>> futures = new ArrayList<>();
        mDexFiles.entrySet().stream()
                .forEach(e ->
                        futures.add(
                                executors.submit(() ->
                                        visitor.visitDexFile(e.getKey(), e.getValue()))));
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }


    public void accept(MultiDexFileVisitor visitor) {
        for (Map.Entry<Integer, DexFileNode> entry : mDexFiles.entrySet()) {
            int dexId = entry.getKey();
            DexFileNode dfn = entry.getValue();
            DexFileVisitor dfv = visitor.visitDexFile(entry.getKey());
            if (dfv != null) {
                dfn.accept(dfv);
            }
        }
    }

    public void accept(MultiDexFileVisitor visitor, ExecutorService executors) {
        List<Future<?>> futures = new ArrayList<>();
        mDexFiles.entrySet().stream()
                .forEach(e ->
                        futures.add(
                                executors.submit(() -> {
                                        DexFileVisitor dfv = visitor.visitDexFile(e.getKey());
                                        if (dfv != null) {
                                            e.getValue().accept(dfv);
                                        }
                                })));
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }



    @Override
    public MultiDexFileVisitor asVisitor() {
        return new MultiDexFileVisitor() {

            @Override
            public DexFileVisitor visitDexFile(int dexId) {
                DexFileNode dfn = new DexFileNode();
                mDexFiles.put(dexId, dfn);

                return dfn.asVisitor();
            }

            public void accept(MultiDexFileVisitor mdfv) {
                for (Map.Entry<Integer, DexFileNode> entry : mDexFiles.entrySet()) {
                    DexFileVisitor dfv = mdfv.visitDexFile(entry.getKey());
                    if (dfv != null) {
                        entry.getValue().accept(dfv);
                    }
                }
            }
        };
    }


    public void smaliToDir(File baseDir) throws IOException {
        List<Map.Entry<Integer, DexFileNode>> sortedDexFiles = mDexFiles.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .collect(Collectors.toList());

        for (Map.Entry<Integer, DexFileNode> entry : sortedDexFiles) {
            int dexIdx = entry.getKey();
            DexFileNode dfn = entry.getValue();
            File baseDirForPerDexFile = new File(baseDir, dexIdx == 1 ?
                    "smali" : "smali_classes" + dexIdx);
            baseDirForPerDexFile.mkdirs();
            dfn.smaliToDir(baseDirForPerDexFile);
        }
    }

}
