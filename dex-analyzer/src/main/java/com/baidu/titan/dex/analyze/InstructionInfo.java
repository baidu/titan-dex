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

package com.baidu.titan.dex.analyze;

import com.baidu.titan.dex.node.insn.DexInsnNode;
import com.baidu.titan.dex.util.Flags;

/**
 *
 * 指令信息
 *
 * @author zhangdi07@baidu.com
 * @since 2018/2/4
 */
public class InstructionInfo extends Flags {

    private static final String EXTRA_INSN_INFO = "ext_insn_info";

    private static final int FLAG_VISITED = 1 << 0;

    private static final int FLAG_CHANGED = 1 << 1;

    private static final int FLAG_OPCODE = 1 << 2;

    private static final int FLAG_INTRY = 1 << 3;

    private static final int FLAG_BRANCH_TARGET = 1 << 4;

    private static final int FLAG_RETURN = 1 << 5;

    public int idx = -1;

    public RegisterPc registerPc;

    public DexInsnNode attachedInsNode;

    /**
     *
     * get InstructionInfo for current Instruction Node
     *
     * @param insnNode
     * @return
     */
    public static InstructionInfo infoForIns(DexInsnNode insnNode) {
        return insnNode.getExtraInfo(EXTRA_INSN_INFO, null);
    }

    /**
     *
     * attach this with Instruction Node
     *
     * @param insnNode
     */
    public void attachIns(DexInsnNode insnNode) {
        insnNode.setExtraInfo(EXTRA_INSN_INFO, this);
        this.attachedInsNode = insnNode;
    }

    public void setInTry(boolean intry) {
        changeFlags(FLAG_INTRY, intry);
    }

    public boolean isInTry() {
        return containsAllOf(FLAG_INTRY);
    }

    public void setBranchTarget(boolean isTarget) {
        changeFlags(FLAG_BRANCH_TARGET, isTarget);
    }

    public boolean isBranchTarget() {
        return containsAllOf(FLAG_BRANCH_TARGET);
    }

    public void setVisited(boolean visited) {
        changeFlags(FLAG_VISITED, visited);
    }

    public boolean isVisited() {
        return containsAllOf(FLAG_VISITED);
    }

    public void setChanged(boolean changed) {
        changeFlags(FLAG_CHANGED, changed);
    }

    public boolean isChanged() {
        return containsAllOf(FLAG_CHANGED);
    }

    public boolean isVisitedOrChanged() {
        return isVisited() || isChanged();
    }

    @Override
    public String toString() {
        return "InstructionInfo{" +
                "idx=" + idx +
                ", registerPc=" + registerPc +
                ", insNode = " + attachedInsNode +
                '}';
    }

}
