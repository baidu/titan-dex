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

import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dex.node.DexNodeExtraInfo;

/**
 * 跳转相关的指令信息
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/18
 */
public class DexTargetInsnNode extends DexOpcodeInsnNode {

    private DexLabelNode mTarget;

    public DexTargetInsnNode(int opcode, DexRegisterList regs, DexLabelNode target) {
        super(opcode, regs);
        this.mTarget = target;
    }

    public DexLabelNode getTarget() {
        return mTarget;
    }

    public DexTargetInsnNode withTarget(DexLabelNode target) {
        return new DexTargetInsnNode(this.getOpcode(), this.getRegisters(), target);
    }

}
