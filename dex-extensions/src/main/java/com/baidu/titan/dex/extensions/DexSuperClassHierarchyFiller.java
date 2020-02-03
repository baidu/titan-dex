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

import java.util.function.Function;

/**
 * 填充父类关系
 *
 * @author zhangdi07@baidu.com
 * @since 2017/9/22
 */

public class DexSuperClassHierarchyFiller implements DexClassPoolNodeVisitor {

    public static final String EXTRA_KEY_SUPERCLASSES = "super-classes";

    private Function<DexType, DexClassNode> mLookups;

    public DexSuperClassHierarchyFiller(Function<DexType, DexClassNode> lookups) {
        this.mLookups = lookups;
    }


    public static DexClassNode getSuperClass(DexClassNode dcn) {
        return dcn.getExtraInfo(EXTRA_KEY_SUPERCLASSES, null);
    }

    public static void setSuperClass(DexClassNode superDcn, DexClassNode subDcn) {
        subDcn.setExtraInfo(EXTRA_KEY_SUPERCLASSES, superDcn);
    }

    @Override
    public void visitClass(DexClassNode dcn) {
        DexType thisType = dcn.type;
        if (thisType.isPrimitiveType()) {
            return;
        }
        DexType superType = dcn.superType;
        if (superType == null) {
            if (!thisType.equals(new DexType("Ljava/lang/Object;"))) {
                throw new IllegalStateException(
                        String.format("class type %s has not superType", thisType.toTypeDescriptor()));
            }
            return;
        }
        DexClassNode superDcn = mLookups.apply(superType);
        if (superDcn == null) {
            System.out.println("cannot find super class for " + dcn.type);
        } else {
            dcn.setExtraInfo(EXTRA_KEY_SUPERCLASSES, superDcn);
        }
    }

    @Override
    public void classPoolVisitEnd() {

    }

}
