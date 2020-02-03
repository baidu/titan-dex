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

import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.node.DexNodeExtraInfo;

/**
 * try catch信息
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/18
 */
public class DexTryCatchNode extends DexPseudoInsnNode {

    private DexLabelNode mStartLabel;

    private DexLabelNode mEndLabel;

    private DexTypeList mTypes;

    private DexLabelNode[] mHandlers;

    private DexLabelNode mCatchAllHandler;

    public DexTryCatchNode(DexLabelNode start, DexLabelNode end, DexTypeList types,
                           DexLabelNode[] handlers, DexLabelNode catchAllHandler) {
        this.mStartLabel = start;
        this.mEndLabel = end;
        this.mTypes = types;
        this.mHandlers = handlers;
        this.mCatchAllHandler = catchAllHandler;
    }

    public DexLabelNode getStart() {
        return mStartLabel;
    }

    public DexLabelNode getEnd() {
        return mEndLabel;
    }

    public DexTypeList getTypes() {
        return mTypes;
    }

    public DexLabelNode[] getHandlers() {
        return mHandlers;
    }

    public DexLabelNode getCatchAllHandler() {
        return this.mCatchAllHandler;
    }

}
