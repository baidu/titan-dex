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

import com.baidu.titan.dex.node.DexCodeNode;
import com.baidu.titan.dex.node.insn.DexConstInsnNode;
import com.baidu.titan.dex.node.insn.DexInsnNode;
import com.baidu.titan.dex.node.insn.DexLabelNode;
import com.baidu.titan.dex.node.insn.DexLineNumberNode;
import com.baidu.titan.dex.node.insn.DexOpcodeInsnNode;
import com.baidu.titan.dex.node.insn.DexSimpleInsnNode;
import com.baidu.titan.dex.node.insn.DexSwitchDataInsnNode;
import com.baidu.titan.dex.node.insn.DexTargetInsnNode;
import com.baidu.titan.dex.node.insn.DexTryCatchNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * 用于压缩指令集合，目前主要是合并相邻的DexLabel元素
 *
 * @author zhangdi07@baidu.com
 * @since 2018/1/29
 */
public class CodeShrinker {

    static class LabelNodeEntia {

        public Set<DexLabelNode> labels = new HashSet<>();

        public DexLabelNode uniLabel;

    }

    private static LabelNodeEntia findAndEnsureLabelNodeEntia(LabelNodeEntia[] labelNodeEntias,
                                                              DexLabelNode labelNode) {
        for (int i = 0; i < labelNodeEntias.length; i++) {
            LabelNodeEntia labelNodeEntia = labelNodeEntias[i];
            if (labelNodeEntia != null && labelNodeEntia.labels.contains(labelNode)) {
                return labelNodeEntia;
            }
        }
        throw new IllegalStateException("cannot find label");
    }

    /**
     *
     * 压缩code，并返回新的DexCodeNode
     *
     * @param oldCodeNode
     * @return
     */
    public static DexCodeNode shrinkDexCode(DexCodeNode oldCodeNode) {
        DexCodeNode newCodeNode = new DexCodeNode();
        newCodeNode.setRegisters(oldCodeNode.getLocalRegCount(),
                oldCodeNode.getParameterRegCount());

        // copy one
        List<DexInsnNode> oldInsns = new ArrayList<>(oldCodeNode.getInsns());
        List<DexInsnNode> newInsns = new ArrayList<>(oldInsns.size());
        newCodeNode.setInsns(newInsns);

        List<DexTryCatchNode> oldTries = Collections.unmodifiableList(oldCodeNode.getTryCatches());
        List<DexTryCatchNode> newTries = new ArrayList<>(oldTries.size());
        newCodeNode.setTryCatches(newTries);

        List<DexLineNumberNode> oldLineNumbers =
                Collections.unmodifiableList(oldCodeNode.getLineNumbers());
        List<DexLineNumberNode> newLineNumbers = new ArrayList<>(oldLineNumbers.size());
        newCodeNode.setLineNumbers(newLineNumbers);

        // 在第一个元素位置插入Label Node, 如果之前该位置有label元素，后继也会被合并
        oldInsns.add(0, new DexLabelNode());

        LabelNodeEntia[] labelEntias = new LabelNodeEntia[oldInsns.size()];

        boolean lastIsLabel = false;
        LabelNodeEntia lastLabelEntia = null;

        // 首先去除相邻的Label元素
        for (int i = 0; i < oldInsns.size(); i++) {
            DexInsnNode insnNode = oldInsns.get(i);
            if (insnNode instanceof DexLabelNode) {
                LabelNodeEntia labelEntia = null;
                if (lastIsLabel) {
                    labelEntia = lastLabelEntia;
                } else {
                    labelEntia = new LabelNodeEntia();
                    lastLabelEntia = labelEntia;
                    labelEntias[i] = labelEntia;
                }
                DexLabelNode label = (DexLabelNode) insnNode;
                labelEntia.labels.add(label);
                lastIsLabel = true;
            } else {
                lastIsLabel = false;
            }
        }

        for (int i = 0; i < labelEntias.length; i++) {
            LabelNodeEntia labelEntia = labelEntias[i];
            if (labelEntia != null) {
                labelEntia.uniLabel = new DexLabelNode();
            }
        }


        for (int i = 0; i < oldInsns.size(); i++) {
            DexInsnNode insNode = oldInsns.get(i);
            if (insNode instanceof DexLabelNode) {
                LabelNodeEntia labelEntia = labelEntias[i];
                if (labelEntia != null) {
                    newInsns.add(labelEntia.uniLabel);
                }
            } else if (insNode instanceof DexOpcodeInsnNode) {
                if (insNode instanceof DexSimpleInsnNode) {
                    newInsns.add(insNode);
                } else if (insNode instanceof DexConstInsnNode) {
                    newInsns.add(insNode);
                } else if (insNode instanceof DexTargetInsnNode) {
                    DexTargetInsnNode targetInsnNode = (DexTargetInsnNode) insNode;
                    newInsns.add(targetInsnNode.withTarget(
                            findAndEnsureLabelNodeEntia(
                                    labelEntias, targetInsnNode.getTarget()).uniLabel));
                } else if (insNode instanceof DexSwitchDataInsnNode) {
                    DexSwitchDataInsnNode switchInsNode = (DexSwitchDataInsnNode)insNode;
                    DexLabelNode[] uniCaseLabels =
                            new DexLabelNode[switchInsNode.getCasesLabel().length];
                    for (int j = 0; j < uniCaseLabels.length; j++) {
                        uniCaseLabels[j] = findAndEnsureLabelNodeEntia(labelEntias,
                                switchInsNode.getCasesLabel()[j]).uniLabel;
                    }
                    newInsns.add(switchInsNode.withCaseLabels(uniCaseLabels));
                }
            }
        }

        // 修正try item中的label引用
        for (int i = 0; i < oldTries.size(); i++) {
            DexTryCatchNode tryCatchNode = oldTries.get(i);
            DexLabelNode startLabel =
                    findAndEnsureLabelNodeEntia(labelEntias, tryCatchNode.getStart()).uniLabel;
            DexLabelNode endLabel =
                    findAndEnsureLabelNodeEntia(labelEntias, tryCatchNode.getEnd()).uniLabel;
            DexLabelNode[] handlers = null;
            DexLabelNode[] oldHandlers = tryCatchNode.getHandlers();
            if (oldHandlers != null && oldHandlers.length > 0) {
                handlers = new DexLabelNode[oldHandlers.length];
                for (int j = 0; j < handlers.length; j++) {
                    handlers[j] = findAndEnsureLabelNodeEntia(labelEntias,
                            oldHandlers[j]).uniLabel;
                }
            }

            DexLabelNode catchAllLabel = null;
            if (tryCatchNode.getCatchAllHandler() != null) {
                catchAllLabel = findAndEnsureLabelNodeEntia(labelEntias,
                        tryCatchNode.getCatchAllHandler()).uniLabel;
            }
            newTries.add(new DexTryCatchNode(startLabel, endLabel, tryCatchNode.getTypes(),
                    handlers, catchAllLabel));
        }

        // 修正linenumber中的label引用
        for (int i = 0; i < oldLineNumbers.size(); i++) {
            DexLineNumberNode lineNode = oldLineNumbers.get(i);
            newLineNumbers.add(new DexLineNumberNode(lineNode.getLineNumber(),
                    findAndEnsureLabelNodeEntia(labelEntias, lineNode.getStartLabel()).uniLabel));
        }

        return newCodeNode;
    }

}
