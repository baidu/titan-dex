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

import com.baidu.titan.dex.DexFileVersion;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.SmaliWriter;
import com.baidu.titan.dex.util.DexIdsCollector;
import com.baidu.titan.dex.visitor.DexClassPoolNodeVisitor;
import com.baidu.titan.dex.visitor.DexClassVisitor;
import com.baidu.titan.dex.visitor.DexClassVisitorInfo;
import com.baidu.titan.dex.visitor.DexFileVisitor;
import com.baidu.titan.dex.visitor.VisitorSupplier;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DexFileNode
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/9
 */
public class DexFileNode extends DexNode implements VisitorSupplier<DexFileVisitor> {

    private LinkedHashMap<DexType, DexClassNode> mDexClassesMap = new LinkedHashMap<>();

    private HashSet<DexMethodIdNode> mMethodIds = new HashSet<>();

    private HashSet<DexFieldIdNode> mFieldIds = new HashSet<>();

    private HashSet<DexString> mStringIds = new HashSet<>();

    public List<DexClassNode> getClassesList() {
        return new ArrayList<>(mDexClassesMap.values());
    }

    public Map<DexType, DexClassNode> getClassesMap() {
        return mDexClassesMap;
    }

    private DexFileVersion mDexVersion;

    private boolean mCheckDexIdCount = false;

    private static final int MAX_MEMBER_IDX = 0xFFFF;

    private DexIdsCollector.Cache mDexIdsCollectorCache;

    public DexFileNode() {

    }

    public DexFileNode(boolean checkDexIds,
                       DexIdsCollector.Cache dexIdsCollectorCache) {
        this.mCheckDexIdCount = checkDexIds;
        this.mDexIdsCollectorCache = dexIdsCollectorCache;
    }

    public DexFileVersion getDexVersion() {
        return mDexVersion;
    }

    public void setDexVersion(DexFileVersion version) {
        this.mDexVersion = version;
    }

    public Set<DexFieldIdNode> getFieldIds() {
        return mFieldIds;
    }

    public Set<DexMethodIdNode> getMethodIds() {
        return this.mMethodIds;
    }

    public void setCheckDexIdCount(boolean check) {
        mCheckDexIdCount = check;
    }

    public void rebuildDexIdsPool() {
        mMethodIds.clear();
        mFieldIds.clear();
        mDexClassesMap.values().forEach(dcn -> {
            DexIdsCollector collector = DexIdsCollector.collectDexIds(dcn, mDexIdsCollectorCache);
            mMethodIds.addAll(collector.getMethodIds());
            mFieldIds.addAll(collector.getFieldIds());
        });
    }

    public boolean addClass(DexClassNode dcn) {
        if (!mCheckDexIdCount) {
            addClassInternal(dcn);
            return true;
        } else {
            DexIdsCollector collector = DexIdsCollector.collectDexIds(dcn, mDexIdsCollectorCache);
            int oldMethodIdsCount = mMethodIds.size();

            Set<DexMethodIdNode> dexFileMethodIds = mMethodIds;

            int newAddedMethodIdCount = 0;
            for (DexMethodIdNode methodId : collector.getMethodIds()) {
                if (!dexFileMethodIds.contains(methodId)) {
                    newAddedMethodIdCount++;
                }
            }
            int newMethodIdsCount = dexFileMethodIds.size() + newAddedMethodIdCount;

            if (newMethodIdsCount > MAX_MEMBER_IDX + 1) {
                return false;
            }

            int oldFieldIdsCount = mFieldIds.size();

            Set<DexFieldIdNode> dexFileFieldIds = mFieldIds;

            int newAddedFieldIdCount = 0;
            for (DexFieldIdNode fieldId : collector.getFieldIds()) {
                if (!dexFileFieldIds.contains(fieldId)) {
                    newAddedFieldIdCount++;
                }
            }
            int newFieldIdsCount = dexFileFieldIds.size() + newAddedFieldIdCount;

            if (newFieldIdsCount > MAX_MEMBER_IDX + 1) {
                return false;
            }

            this.mMethodIds.addAll(collector.getMethodIds());
            this.mFieldIds.addAll(collector.getFieldIds());

            addClassInternal(dcn);

            return true;
        }
    }

    private void addClassInternal(DexClassNode dcn) {
        this.mDexClassesMap.put(dcn.type, dcn);
    }

    public void accept(DexFileVisitor dfv) {
        dfv.visitBegin();
        dfv.visitDexVersion(mDexVersion);
        for (Map.Entry<DexType, DexClassNode> cle : mDexClassesMap.entrySet()) {
            cle.getValue().accept(dfv);
        }
        dfv.visitEnd();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void accept(DexClassPoolNodeVisitor visitor) {
        for (DexClassNode dcn : mDexClassesMap.values()) {
            visitor.visitClass(dcn);
        }

        visitor.classPoolVisitEnd();
    }


    @Override
    public DexFileVisitor asVisitor() {

        return new DexFileVisitor() {

            @Override
            public void visitBegin() {
                super.visitBegin();
            }

            @Override
            public void visitDexVersion(DexFileVersion version) {
                mDexVersion = version;
            }

            @Override
            public DexClassVisitor visitClass(DexClassVisitorInfo classInfo) {
                DexClassNode dcn = new DexClassNode(classInfo);
                addClassInternal(dcn);
                return dcn.asVisitor();
            }

            @Override
            public void visitExtraInfo(String key, Object extra) {
                DexFileNode.this.setExtraInfo(key, extra);
            }

            @Override
            public void visitEnd() {
                super.visitEnd();
            }

        };
    }

    public void smaliTo(SmaliWriter writer) {
        writer.write(".dex ");
        writer.write("\n");

        // # write classes
        mDexClassesMap.values().stream()
                .sorted()
                .forEach(dcn -> {
                    dcn.smaliTo(writer);
                });
    }

    public void smaliToDir(File baseDir) throws IOException {

        List<DexClassNode> sortedClasses = mDexClassesMap.values().stream()
                .sorted()
                .collect(Collectors.toList());

        for (DexClassNode dcn : sortedClasses) {
            String desc = dcn.type.toTypeDescriptor();
            if (desc.startsWith("L")) {

                desc = desc.substring(1, desc.length() - 1);

                int lstPackageIdx = desc.lastIndexOf("/");

                String className = lstPackageIdx == -1 ? desc
                        : desc.substring(lstPackageIdx + 1, desc.length());
                File packageDir = lstPackageIdx == -1 ? baseDir
                        : new File(baseDir, desc.substring(0, lstPackageIdx));

                packageDir.mkdirs();

                File smaliFile = new File(packageDir, className + ".smali");

                FileWriter fw = null;
                try {
                    fw = new FileWriter(smaliFile);
                    dcn.smaliTo(new SmaliWriter(fw));
                } finally {
                    if (fw != null) {
                        try {
                            fw.close();
                        } catch (IOException e) {

                        }
                    }
                }
            }
        }
    }

}
