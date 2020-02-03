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


import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.visitor.DexClassPoolNodeVisitor;
import com.baidu.titan.dex.visitor.VisitorSupplier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author zhangdi07
 * @since 2017/9/13
 */

public class DexClassPoolNode extends DexNode implements Iterable<DexClassNode> ,
        VisitorSupplier<DexClassPoolNodeVisitor> {

    private final Map<DexType, DexClassNode> mClasses = new HashMap<>();

    public void addClass(DexClassNode clazz) {
        mClasses.put(clazz.type, clazz);
    }

    public boolean removeClass(DexClassNode clazz) {
        return mClasses.remove(clazz.type) != null;
    }

    public boolean removeClass(DexType type) {
        return mClasses.remove(type) != null;
    }

    public DexClassNode getClass(DexType type) {
        return mClasses.get(type);
    }

    public int size() {
        return mClasses.size();
    }


    public void accept(DexClassPoolNodeVisitor visitor) {
        mClasses.values().forEach(visitor::visitClass);
        visitor.classPoolVisitEnd();
    }

    public void accept(DexType type, DexClassPoolNodeVisitor visitor) {
        DexClassNode dcn = getClass(type);
        if (dcn != null) {
            visitor.visitClass(dcn);
        }
    }

    @Override
    public Iterator<DexClassNode> iterator() {
        return mClasses.values().iterator();
    }

    public void forEach(BiConsumer<DexType, DexClassNode> consumer) {
        mClasses.forEach(consumer);
    }

    public Stream<DexClassNode> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Override
    public DexClassPoolNodeVisitor asVisitor() {
        return new DexClassPoolNodeVisitor() {

            @Override
            public void visitClass(DexClassNode dcn) {
                DexClassPoolNode.this.addClass(dcn);
            }

            @Override
            public void classPoolVisitEnd() {

            }
        };
    }
}
