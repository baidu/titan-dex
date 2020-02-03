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

import com.baidu.titan.dex.node.DexCodeNode;
import com.baidu.titan.dex.visitor.DexCodeVisitor;

/**
 * 使用DexCodeNode作为后端存储，
 * 解决Visitor.visitRegister必须优先访问的问题
 *
 * @author zhangdi07
 * @since 2017/9/14
 */

class DexCodeWriterBackend extends DexCodeVisitor {

    private DexCodeWriter mCodeWriter;

    private DexCodeNode mBackEnd;

    public DexCodeWriterBackend(DexCodeWriter codeWriter) {
        super();
        mBackEnd = new DexCodeNode();
        delegate = mBackEnd.asVisitor();
        this.mCodeWriter = codeWriter;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        mBackEnd.accept(mCodeWriter);
    }


}
