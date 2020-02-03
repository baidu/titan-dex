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

package com.baidu.titan.dex.extensions;

import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.node.DexClassNode;
import com.baidu.titan.dex.visitor.DexClassPoolNodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 填充子类
 *
 * @author zhangdi07@baidu.com
 * @since 2017/9/22
 */

public class DexSubClassHierarchyFiller implements DexClassPoolNodeVisitor {

    public static final String EXTRA_KEY_SUBCLASSES = "sub-classes";

    private Function<DexType, DexClassNode> mLookups;

    private static final Map<DexType, DexClassNode> EMPTY = new HashMap<>();

    public DexSubClassHierarchyFiller(Function<DexType, DexClassNode> lookups) {
        this.mLookups = lookups;
    }

    public static void forEachSubClass(DexClassNode dcn, Consumer<DexClassNode> consumer) {
        Map<DexType, DexClassNode> subClasses = dcn.getExtraInfo(EXTRA_KEY_SUBCLASSES, null);
        if (subClasses != null) {
            new ArrayList<>(subClasses.values()).forEach(consumer);
        }
    }

    public static boolean removeSubClass(DexClassNode superClass, DexType subType) {
        Map<DexType, DexClassNode> subClasses = superClass.getExtraInfo(EXTRA_KEY_SUBCLASSES, EMPTY);
        return subClasses.remove(subType) != null;
    }

    public static boolean addSubClass(DexClassNode superClass, DexClassNode subClass) {
        Map<DexType, DexClassNode> subClasses = superClass.getExtraInfo(EXTRA_KEY_SUBCLASSES, null);
        if (subClasses == null) {
            subClasses = new HashMap<>();
            superClass.setExtraInfo(EXTRA_KEY_SUBCLASSES, subClasses);
        }
        return subClasses.put(subClass.type, subClass) == null;
    }


    @Override
    public void visitClass(DexClassNode dcn) {

        DexType superType = dcn.superType;
        if (superType != null) {
            DexClassNode superDcn = mLookups.apply(superType);
            if (superDcn != null) {
                Map<DexType, DexClassNode> subClasses = superDcn
                        .getExtraInfo(EXTRA_KEY_SUBCLASSES, null);
                if (subClasses == null) {
                    subClasses = new HashMap<>();
                    superDcn.setExtraInfo(EXTRA_KEY_SUBCLASSES, subClasses);
                }
                subClasses.put(dcn.type, dcn);
            }

        }
    }

    @Override
    public void classPoolVisitEnd() {

    }
}
