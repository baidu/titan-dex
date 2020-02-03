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
import java.util.List;
import java.util.function.Function;

/**
 * 填充类的接口列表
 *
 * @author zhangdi07@baidu.com
 * @since 2018/1/9
 */
public class DexInterfacesHierarchyFiller implements DexClassPoolNodeVisitor {

    public static final String EXTRA_KEY_INTERFACES = "interface-classes";

    private Function<DexType, DexClassNode> mLookups;

    public DexInterfacesHierarchyFiller(Function<DexType, DexClassNode> lookups) {
        this.mLookups = lookups;
    }


    public static List<DexClassNode> getInterfaces(DexClassNode dcn) {
        return dcn.getExtraInfo(EXTRA_KEY_INTERFACES, null);
    }

    public static void setInterfaces(DexClassNode dcn, List<DexClassNode> interfaces) {
        dcn.setExtraInfo(EXTRA_KEY_INTERFACES, interfaces);
    }

    @Override
    public void visitClass(DexClassNode dcn) {
        List<DexClassNode> itfs = new ArrayList<>();
        for (DexType itf : dcn.interfaces.types()) {
            DexClassNode itfCn = mLookups.apply(itf);
            if (itfCn != null) {
                itfs.add(itfCn);
            }
        }
        dcn.setExtraInfo(EXTRA_KEY_INTERFACES, itfs);
    }

    @Override
    public void classPoolVisitEnd() {

    }

}

