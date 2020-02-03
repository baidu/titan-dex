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

package com.baidu.titan.dex.node.insn;

/**
 * 字节码行信息
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/18
 */
public class DexLineNumberNode extends DexPseudoInsnNode {

    private int mLine;

    private DexLabelNode mLabel;

    public DexLineNumberNode(int line, DexLabelNode label) {
        this.mLine = line;
        this.mLabel = label;
    }

    public int getLineNumber() {
        return mLine;
    }

    public DexLabelNode getStartLabel() {
        return mLabel;
    }

}
