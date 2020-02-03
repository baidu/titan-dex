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

import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.Dop;
import com.baidu.titan.dex.Dops;
import com.baidu.titan.dex.SmaliWriter;
import com.baidu.titan.dex.node.insn.DexConstInsnNode;
import com.baidu.titan.dex.node.insn.DexInsnNode;
import com.baidu.titan.dex.node.insn.DexLabelNode;
import com.baidu.titan.dex.node.insn.DexLineNumberNode;
import com.baidu.titan.dex.node.insn.DexOpcodeInsnNode;
import com.baidu.titan.dex.node.insn.DexPseudoInsnNode;
import com.baidu.titan.dex.node.insn.DexSimpleInsnNode;
import com.baidu.titan.dex.node.insn.DexSwitchDataInsnNode;
import com.baidu.titan.dex.node.insn.DexTargetInsnNode;
import com.baidu.titan.dex.node.insn.DexTryCatchNode;
import com.baidu.titan.dex.util.Flags;
import com.baidu.titan.dex.visitor.DexCodeVisitor;
import com.baidu.titan.dex.visitor.DexLabel;
import com.baidu.titan.dex.visitor.VisitorSupplier;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.baidu.titan.dex.DopFormats.FORMAT_10X;

/**
 * Dex 方法节点
 * @author zhangdi07@baidu.codm
 * @since 2016/11/8
 */
public class DexCodeNode extends DexNode implements VisitorSupplier<DexCodeVisitor> {

    private List<DexInsnNode> mInsns = new ArrayList<>();

    private List<DexTryCatchNode> mTryCatches = new ArrayList<>();

    private List<DexLineNumberNode> mLineNumbers = new ArrayList<>();

    private int mLocalRegCount;

    private int mParameterRegCount;

    private DexString[] mParameterNames;

    private static final String SMALI_LABEL_COND_PREFIX = ":cond_";

    private static final String SMALI_LABEL_GOTO_PREFIX = ":goto_";

    private static final String SMALI_LABEL_TRY_START_PREFIX = ":try_start_";

    private static final String SMALI_LABEL_TRY_END_PREFIX = ":try_end_";

    private static final String SMALI_LABEL_CATCH_HANDLER_PREFIX = ":catch_";

    private static final String SMALI_LABEL_CATCH_ALL_HANDLER_PREFIX = ":catchall_";

    public static final String SMALI_LABEL_ARRAY_DATA_PREFIX = ":array_";

    public static final String SMALI_LABEL_PACK_SWITCH_PREFIX = ":pswitch_data_";

    public static final String SMALI_LABEL_PACK_SWITCH_ITEM_PREFIX = ":pswitch_";

    public static final String SMALI_LABEL_SPARSE_SWITCH_PREFIX = ":sswitch_data_";

    public static final String SMALI_LABEL_SPARSE_SWITCH_ITEM_PREFIX = ":sswitch_";

    private static final String SMALI_DIRECTIVE_LINE = ".line";

    private static final String SMALI_DIRECTIVE_CATCH = ".catch";

    private static final String SMALI_DIRECTIVE_CATCH_ALL = ".catchall";

    private static final String SMALI_DIRECTIVE_PACK_SWITCH = ".packed-switch";

    private static final String SMALI_DIRECTIVE_PACK_SWITCH_END = ".end packed-switch";

    private static final String SMALI_DIRECTIVE_SPARSE_SWITCH = ".sparse-switch";

    private static final String SMALI_DIRECTIVE_SPARSE_SWITCH_END = ".end sparse-switch";

    public static final int SMALI_FLAG_INS_IDX = 1 << 0;

    public DexCodeNode() {
        super();
    }

    public List<DexInsnNode> getInsns() {
        return mInsns;
    }

    public void setInsns(List<DexInsnNode> insns) {
        this.mInsns = insns;
    }

    public List<DexTryCatchNode> getTryCatches() {
        return this.mTryCatches;
    }

    public void setTryCatches(List<DexTryCatchNode> tryCatches) {
        this.mTryCatches = tryCatches;
    }

    public List<DexLineNumberNode> getLineNumbers() {
        return this.mLineNumbers;
    }

    public void setLineNumbers(List<DexLineNumberNode> lineNumbers) {
        this.mLineNumbers = lineNumbers;
    }

    public void setRegisters(int localRegCount, int parameterRegCount) {
        this.mLocalRegCount = localRegCount;
        this.mParameterRegCount = parameterRegCount;
    }

    public int getLocalRegCount() {
        return this.mLocalRegCount;
    }

    public int getParameterRegCount() {
        return mParameterRegCount;
    }

    public DexString[] getParameterNames() {
        return mParameterNames;
    }

    /**
     * used by accept
     *
     * @param mapping
     * @param labelNode
     * @return
     */
    private static DexLabel getOrCreateDexLabel(Map<DexLabelNode, DexLabel> mapping,
                                                DexLabelNode labelNode) {
        DexLabel label = mapping.get(labelNode);
        if (label == null) {
            label = new DexLabel();
            mapping.put(labelNode, label);
        }
        return label;
    }

    public void accept(DexCodeVisitor dcv) {
        dcv.visitBegin();
        dcv.visitRegisters(mLocalRegCount, mParameterRegCount);
        Map<DexLabelNode, DexLabel> labelMap = new HashMap<>();
        if (mTryCatches != null) {
            for (DexTryCatchNode dtcn : mTryCatches) {
                DexLabel[] handlers = null;
                DexLabelNode[] oldHandlers = dtcn.getHandlers();
                if (oldHandlers != null && oldHandlers.length > 0) {
                    handlers = new DexLabel[dtcn.getHandlers().length];
                    for (int i = 0; i < handlers.length; i++) {
                        handlers[i] = getOrCreateDexLabel(labelMap, dtcn.getHandlers()[i]);
                    }
                }
                dcv.visitTryCatch(getOrCreateDexLabel(labelMap, dtcn.getStart()),
                        getOrCreateDexLabel(labelMap, dtcn.getEnd()), dtcn.getTypes(), handlers,
                        dtcn.getCatchAllHandler() == null ?
                                null : getOrCreateDexLabel(labelMap, dtcn.getCatchAllHandler()));
            }
        }
        if (mInsns != null) {
            for (int i = 0; i < mInsns.size(); i++) {
                DexInsnNode din = mInsns.get(i);
                if (din instanceof DexOpcodeInsnNode) {
                    DexOpcodeInsnNode doin = (DexOpcodeInsnNode) din;
                    if (din instanceof DexSimpleInsnNode) {
                        dcv.visitSimpleInsn(doin.getOpcode(), doin.getRegisters());
                    } else if (din instanceof DexConstInsnNode) {
                        DexConstInsnNode dcin = (DexConstInsnNode) din;
                        dcv.visitConstInsn(dcin.getOpcode(), dcin.getRegisters(), dcin.getConst());
                    } else if (din instanceof DexTargetInsnNode) {
                        DexTargetInsnNode dtin = (DexTargetInsnNode) din;
                        dcv.visitTargetInsn(dtin.getOpcode(), dtin.getRegisters(),
                                getOrCreateDexLabel(labelMap, dtin.getTarget()));
                    } else if (din instanceof DexSwitchDataInsnNode) {
                        DexSwitchDataInsnNode dsdin = (DexSwitchDataInsnNode) din;
                        DexLabel[] cases = new DexLabel[dsdin.getCasesLabel().length];
                        for (int j = 0; j < cases.length; j++) {
                            cases[j] = getOrCreateDexLabel(labelMap, dsdin.getCasesLabel()[j]);
                        }
                        int[] keys = dsdin.getKeys();
                        dcv.visitSwitch(dsdin.getOpcode(), dsdin.getRegisters(), keys, cases);
                    } else {

                    }
                } else if (din instanceof DexPseudoInsnNode) {
                    if (din instanceof DexLabelNode) {
                        DexLabelNode dln = (DexLabelNode) din;
                        DexLabel dexLabel = getOrCreateDexLabel(labelMap, dln);
                        for (DexLineNumberNode lineNumNode : mLineNumbers) {
                            if (din == lineNumNode.getStartLabel()) {
                                dcv.visitLineNumber(lineNumNode.getLineNumber(), dexLabel);
                            }
                        }
                        dcv.visitLabel(dexLabel);
                    }
                }
            }
        }

        dcv.visitEnd();
    }

    @Override
    public DexCodeVisitor asVisitor() {

        return new DexCodeVisitor() {

            private Map<DexLabel, DexLabelNode> mLabelMap = new HashMap<>();

            @Override
            public void visitBegin() {
                super.visitBegin();
            }

            @Override
            public void visitRegisters(int localRegCount, int parameterRegCount) {
                mLocalRegCount = localRegCount;
                mParameterRegCount = parameterRegCount;
            }

            @Override
            public void visitLineNumber(int line, DexLabel start) {
                mLineNumbers.add(new DexLineNumberNode(line, getOrCreateLabelNode(start)));
            }

            @Override
            public void visitTryCatch(DexLabel start, DexLabel end, DexTypeList types,
                                      DexLabel[] handlers, DexLabel catchAllHandler) {
                DexLabelNode[] dlns = null;
                if (handlers != null && handlers.length > 0) {
                    dlns = new DexLabelNode[handlers.length];
                    for (int i = 0; i < handlers.length; i++) {
                        dlns[i] = getOrCreateLabelNode(handlers[i]);
                    }
                }
                DexTryCatchNode dtn = new DexTryCatchNode(getOrCreateLabelNode(start),
                        getOrCreateLabelNode(end), types, dlns,
                        catchAllHandler == null ? null : getOrCreateLabelNode(catchAllHandler));
                mTryCatches.add(dtn);
            }

            @Override
            public void visitLabel(DexLabel label) {
                mInsns.add(getOrCreateLabelNode(label));
            }

            @Override
            public void visitConstInsn(int op, DexRegisterList regs, DexConst dexConst) {
                mInsns.add(new DexConstInsnNode(op, regs, dexConst));
            }

            @Override
            public void visitTargetInsn(int op, DexRegisterList regs, DexLabel label) {
                mInsns.add(new DexTargetInsnNode(op, regs, getOrCreateLabelNode(label)));
            }

            @Override
            public void visitSimpleInsn(int op, DexRegisterList regs) {
                mInsns.add(new DexSimpleInsnNode(op, regs));
            }

            @Override
            public void visitSwitch(int op, DexRegisterList regs, int[] keys, DexLabel[] targets) {
                DexLabelNode[] targetNodes = new DexLabelNode[keys.length];
                for (int i = 0; i < keys.length; i++) {
                    targetNodes[i] = getOrCreateLabelNode(targets[i]);
                }
                DexSwitchDataInsnNode insn = new DexSwitchDataInsnNode(op, regs, keys, targetNodes);
                mInsns.add(insn);
            }

            @Override
            public void visitParameters(DexString[] parameters) {
                mParameterNames = parameters;
            }

            @Override
            public void visitLocal(int reg, DexString name, DexType type, DexString signature,
                                   DexLabel start, DexLabel end) {
                super.visitLocal(reg, name, type, signature, start, end);
            }

            @Override
            public void visitExtraInfo(String key, Object extra) {
                DexCodeNode.this.setExtraInfo(key, extra);
            }

            @Override
            public void visitEnd() {
                super.visitEnd();
            }

            private DexLabelNode getOrCreateLabelNode(DexLabel label) {
                DexLabelNode labelNode = mLabelMap.get(label);
                if (labelNode == null) {
                    labelNode = new DexLabelNode();
                    mLabelMap.put(label, labelNode);
                }
                return labelNode;
            }

        };
    }

    public void smaliTo(SmaliWriter writer, int flags) {
        writer.writeLine(".locals " + this.mLocalRegCount);

        writer.newLine();
        writer.newLine();

        Map<DexInsnNode, SmaliInfo> smaliInfos = new HashMap<>();

        fillSmaliInfo(smaliInfos);

        writeCode(writer, smaliInfos, flags);
    }

    public String toSmaliString() {
        StringWriter sw = new StringWriter();
        smaliTo(new SmaliWriter(sw), 0);
        return sw.toString();
    }

    public String toSmaliStringWithInsIndex() {
        StringWriter sw = new StringWriter();
        smaliTo(new SmaliWriter(sw), SMALI_FLAG_INS_IDX);
        return sw.toString();
    }

    static class SmaliInfo extends Flags {

        public static final int FLAG_GOTO = 1 << 0;

        public static final int FLAG_COND = 1 << 1;

        public static final int FLAG_TRY_START = 1 << 2;

        public static final int FLAG_TRY_END = 1 << 3;

        public static final int FLAG_HANDLER = 1 << 4;

        public static final int FLAG_CATCH_ALL = 1 << 5;

        public static final int FLAG_LINE = 1 << 6;

        public static final int FLAG_PACK_SWITCH = 1 << 7;

        public static final int FLAG_PACK_DATA = 1 << 8;

        public static final int FLAG_SPARSE_SWITCH = 1 << 9;

        public static final int FLAG_SPARSE_DATA = 1 << 10;

        public static final int FLAG_ARRAY_DATA = 1 << 11;

        public int gotoId;

        public int condId;

        public int tryStartId;

        public int tryEndId;

        public int handlerId;

        public int catchAllId;

        public int lineNum = -1;

        public int packedSwitchIdx;

        public int sparseSwitchIdx;

        public int packDataIdx;

        public int sparseDataIdx;

        public int arrayDataIdx;

        public DexTryCatchNode tryCatchNode;

        public SmaliInfo() {
            super(0);
        }

    }


    private void fillSmaliInfo(Map<DexInsnNode, SmaliInfo> smaliInfos) {
        List<DexInsnNode> insns = mInsns;
        insns.stream()
                .filter(ins -> {
                    if (ins instanceof DexLabelNode) {
                        return true;
                    }
                    if (ins instanceof DexSwitchDataInsnNode) {
                        return true;
                    }
                    if (ins instanceof DexConstInsnNode
                            && ((DexConstInsnNode)ins).getOpcode() == Dops.FILL_ARRAY_DATA) {
                        return true;
                    }
                    return false;
                })
                .forEach(ins -> {
                    smaliInfos.put(ins, new SmaliInfo());
                });

        List<DexLineNumberNode> lines = mLineNumbers;
        if (lines != null) {
            lines.forEach(line -> {
                SmaliInfo smaliInfo = smaliInfos.get(line.getStartLabel());
                smaliInfo.appendFlags(SmaliInfo.FLAG_LINE);
                smaliInfo.lineNum = line.getLineNumber();
            });
        }

        Function<DexLabelNode, SmaliInfo> findSmaliInfo = l -> {
            SmaliInfo smaliInfo = smaliInfos.get(l);
            if (smaliInfo == null) {
                throw new IllegalStateException();
            }
            return smaliInfo;
        };

        insns.stream()
                .filter(ins -> ins instanceof DexTargetInsnNode)
                .map(ins -> (DexTargetInsnNode) ins)
                .forEach(targetInsn -> {
                    DexLabelNode targetLabel = targetInsn.getTarget();
                    SmaliInfo smaliInfo = findSmaliInfo.apply(targetLabel);
                            smaliInfos.get(targetLabel);

                    switch (targetInsn.getOpcode()) {
                        case Dops.GOTO:
                        case Dops.GOTO_16:
                        case Dops.GOTO_32: {
                            smaliInfo.appendFlags(SmaliInfo.FLAG_GOTO);
                            break;
                        }
                        case Dops.IF_EQ:
                        case Dops.IF_EQZ:
                        case Dops.IF_GE:
                        case Dops.IF_GEZ:
                        case Dops.IF_GT:
                        case Dops.IF_GTZ:
                        case Dops.IF_LE:
                        case Dops.IF_LEZ:
                        case Dops.IF_LT:
                        case Dops.IF_LTZ:
                        case Dops.IF_NE:
                        case Dops.IF_NEZ: {
                            smaliInfo.appendFlags(SmaliInfo.FLAG_COND);
                            break;
                        }
                    }
                });

        insns.stream()
                .filter(ins -> ins instanceof DexSwitchDataInsnNode)
                .map(ins -> (DexSwitchDataInsnNode)ins)
                .forEach(switchIns -> {
                    boolean packed = switchIns.getOpcode() == Dops.PACKED_SWITCH;
                    DexLabelNode[] cases = switchIns.getCasesLabel();
                    for (DexLabelNode caseLabel : cases) {
                        SmaliInfo smaliInfo = smaliInfos.get(caseLabel);
                        smaliInfo.appendFlags(packed ?
                                SmaliInfo.FLAG_PACK_SWITCH : SmaliInfo.FLAG_SPARSE_SWITCH);
                    }

                });

        insns.stream()
                .filter(ins -> ins instanceof DexConstInsnNode)
                .map(ins -> (DexConstInsnNode)ins)
                .filter(ins -> ins.getOpcode() == Dops.FILL_ARRAY_DATA)
                .forEach(arrayDataIns -> {
                    SmaliInfo smaliInfo = smaliInfos.get(arrayDataIns);
                    smaliInfo.appendFlags(SmaliInfo.FLAG_ARRAY_DATA);
                });

        List<DexTryCatchNode> tries = mTryCatches;
        if (tries != null) {
            tries.stream()
                    .forEach(tryCatch -> {
                        SmaliInfo startSmaliInfo = smaliInfos.get(tryCatch.getStart());
                        startSmaliInfo.appendFlags(SmaliInfo.FLAG_TRY_START);
                        startSmaliInfo.tryCatchNode = tryCatch;

                        SmaliInfo endSmaliInfo = smaliInfos.get(tryCatch.getEnd());
                        endSmaliInfo.appendFlags(SmaliInfo.FLAG_TRY_END);
                        endSmaliInfo.tryCatchNode = tryCatch;

                        if (tryCatch.getHandlers() != null && tryCatch.getHandlers().length > 0) {
                            for (DexLabelNode handlerLabel : tryCatch.getHandlers()) {
                                SmaliInfo handlerSmaliInfo = smaliInfos.get(handlerLabel);
                                handlerSmaliInfo.appendFlags(SmaliInfo.FLAG_HANDLER);
                            }
                        }

                        if (tryCatch.getCatchAllHandler() != null) {
                            SmaliInfo catchAllSmaliInfo =
                                    smaliInfos.get(tryCatch.getCatchAllHandler());
                            catchAllSmaliInfo.appendFlags(SmaliInfo.FLAG_CATCH_ALL);
                        }
                    });
        }

        AtomicInteger nextGotoIdx = new AtomicInteger(0);

        AtomicInteger nextCondIdx = new AtomicInteger(0);

        AtomicInteger nextTryIdx = new AtomicInteger(0);

        AtomicInteger nextHandlerIdx = new AtomicInteger(0);

        AtomicInteger nextCatchAllIdx = new AtomicInteger(0);

        AtomicInteger nextPackedSwitchIdx = new AtomicInteger(0);

        AtomicInteger nextSparseSwitchIdx = new AtomicInteger(0);

        AtomicInteger nextPackDataIdx = new AtomicInteger(0);

        AtomicInteger nextSparseDataIdx = new AtomicInteger(0);

        AtomicInteger nextArrayDataIdx = new AtomicInteger(0);

        insns.stream()
                .filter(ins -> smaliInfos.containsKey(ins))
                .forEach(label -> {
                    SmaliInfo smaliInfo = smaliInfos.get(label);
                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_GOTO)) {
                        smaliInfo.gotoId = nextGotoIdx.getAndIncrement();
                    }

                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_COND)) {
                        smaliInfo.condId = nextCondIdx.getAndIncrement();
                    }

                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_TRY_START)) {
                        smaliInfo.tryStartId = nextTryIdx.getAndIncrement();
                        SmaliInfo tryEndLabelInfo = smaliInfos.get(smaliInfo.tryCatchNode.getEnd());
                        tryEndLabelInfo.tryEndId = smaliInfo.tryStartId;
                    }

                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_HANDLER)) {
                        smaliInfo.handlerId = nextHandlerIdx.getAndIncrement();
                    }

                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_CATCH_ALL)) {
                        smaliInfo.catchAllId = nextCatchAllIdx.getAndIncrement();
                    }

                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_PACK_SWITCH)) {
                        smaliInfo.packedSwitchIdx = nextPackedSwitchIdx.getAndIncrement();
                    }

                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_SPARSE_SWITCH)) {
                        smaliInfo.sparseSwitchIdx = nextSparseSwitchIdx.getAndIncrement();
                    }

                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_PACK_DATA)) {
                        smaliInfo.packDataIdx = nextPackDataIdx.getAndIncrement();
                    }

                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_SPARSE_DATA)) {
                        smaliInfo.sparseDataIdx = nextSparseDataIdx.getAndIncrement();
                    }

                    if (smaliInfo.containsOneOf(SmaliInfo.FLAG_ARRAY_DATA)) {
                        smaliInfo.arrayDataIdx = nextArrayDataIdx.getAndIncrement();
                    }

                });

    }

    private void writeCode(SmaliWriter writer, Map<DexInsnNode, SmaliInfo> smaliInfos, int flags) {
        List<DexInsnNode> insns = mInsns;
        AtomicInteger nexInsIdx = new AtomicInteger(0);

        insns.forEach((DexInsnNode ins) -> {
            SmaliInfo smaliInfo = smaliInfos.get(ins);
            if ((flags & SMALI_FLAG_INS_IDX) != 0) {
                writer.write("# .index ");
                writer.printSignedIntAsDec(nexInsIdx.getAndIncrement());
                writer.newLine();
            }
            if (ins instanceof DexLabelNode) {
                DexLabelNode labelNode = (DexLabelNode)ins;

                // # write line
                if (smaliInfo.containsOneOf(SmaliInfo.FLAG_LINE)) {
                    writer.write(SMALI_DIRECTIVE_LINE);
                    writer.write(" ");
                    writer.printSignedIntAsDec(smaliInfo.lineNum);
                    writer.newLine();
                }

                // # write cond label
                if (smaliInfo.containsOneOf(SmaliInfo.FLAG_COND)) {
                    writer.write(SMALI_LABEL_COND_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.condId);
                    writer.newLine();
                }

                // # write goto label
                if (smaliInfo.containsOneOf(SmaliInfo.FLAG_GOTO)) {
                    writer.write(SMALI_LABEL_GOTO_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.gotoId);
                    writer.newLine();
                }

                // #write try_start
                if (smaliInfo.containsOneOf(SmaliInfo.FLAG_TRY_START)) {
                    writer.write(SMALI_LABEL_TRY_START_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.tryStartId);
                    writer.newLine();
                }

                // # write try_end
                if (smaliInfo.containsOneOf(SmaliInfo.FLAG_TRY_END)) {
                    writer.write(SMALI_LABEL_TRY_END_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.tryEndId);
                    writer.newLine();

                    DexLabelNode[] handlers = smaliInfo.tryCatchNode.getHandlers();
                    DexTypeList exceptionTypes = smaliInfo.tryCatchNode.getTypes();
                    if (handlers != null) {
                        for (int i = 0; i < handlers.length; i++) {
                            DexLabelNode handlerLabel = handlers[i];
                            DexType exceptionType = exceptionTypes.getType(i);
                            SmaliInfo handlerLabelInfo = smaliInfos.get(handlerLabel);

                            writer.write(SMALI_DIRECTIVE_CATCH);
                            writer.write(" ");
                            writer.write(exceptionType.toTypeDescriptor());
                            writer.write(' ');

                            writer.write("{");
                            writer.write(SMALI_LABEL_TRY_START_PREFIX);
                            writer.printSignedIntAsDec(smaliInfo.tryEndId);
                            writer.write(" .. ");
                            writer.write(SMALI_LABEL_TRY_END_PREFIX);
                            writer.printSignedIntAsDec(smaliInfo.tryEndId);
                            writer.write("} ");
                            writer.write(SMALI_LABEL_CATCH_HANDLER_PREFIX);
                            writer.printSignedIntAsDec(handlerLabelInfo.handlerId);
                            writer.newLine();
                        }
                    }

                    // # write try catch all scope
                    DexLabelNode catchAllLabel = smaliInfo.tryCatchNode.getCatchAllHandler();
                    if (catchAllLabel != null) {
                        SmaliInfo catchAllLabelInfo = smaliInfos.get(catchAllLabel);
                        writer.write(SMALI_DIRECTIVE_CATCH_ALL);
                        writer.write(" ");
                        writer.write("{");
                        writer.write(SMALI_LABEL_TRY_START_PREFIX);
                        writer.printSignedIntAsDec(smaliInfo.tryEndId);
                        writer.write(" .. ");
                        writer.write(SMALI_LABEL_TRY_END_PREFIX);
                        writer.printSignedIntAsDec(smaliInfo.tryEndId);
                        writer.write("} ");
                        writer.write(SMALI_LABEL_CATCH_ALL_HANDLER_PREFIX);
                        writer.printSignedIntAsDec(catchAllLabelInfo.handlerId);
                        writer.newLine();
                    }

                }

                // # write catch handler label
                if (smaliInfo.containsOneOf(SmaliInfo.FLAG_HANDLER)) {
                    writer.write(SMALI_LABEL_CATCH_HANDLER_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.handlerId);
                    writer.newLine();
                }

                // # write catch_all label
                if (smaliInfo.containsOneOf(SmaliInfo.FLAG_CATCH_ALL)) {
                    writer.write(SMALI_LABEL_CATCH_ALL_HANDLER_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.catchAllId);
                    writer.newLine();
                }

                // # write switch case label
                if (smaliInfo.containsOneOf(SmaliInfo.FLAG_PACK_SWITCH)) {
                    writer.write(SMALI_LABEL_PACK_SWITCH_ITEM_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.packedSwitchIdx);
                    writer.newLine();
                }

                // # write sparse case label
                if (smaliInfo.containsOneOf(SmaliInfo.FLAG_SPARSE_SWITCH)) {
                    writer.write(SMALI_LABEL_SPARSE_SWITCH_ITEM_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.sparseSwitchIdx);
                    writer.newLine();
                }

            } else if (ins instanceof DexOpcodeInsnNode) {
                DexOpcodeInsnNode opcodeInsnNode = (DexOpcodeInsnNode) ins;
                Dop dop = Dops.dopFor(opcodeInsnNode.getOpcode());
                writeDexOpcodeInsnNodeCode(writer, opcodeInsnNode,dop);

                // # write other
                if (ins instanceof DexTargetInsnNode) {
                    DexTargetInsnNode targetInsnNode = (DexTargetInsnNode) ins;
                    DexLabelNode targetLabel = targetInsnNode.getTarget();
                    SmaliInfo targetLabelInfo = smaliInfos.get(targetLabel);

                    switch (targetInsnNode.getOpcode()) {
                        case Dops.GOTO:
                        case Dops.GOTO_16:
                        case Dops.GOTO_32: {
                            writer.write(SMALI_LABEL_GOTO_PREFIX);
                            writer.printSignedIntAsDec(targetLabelInfo.gotoId);
                            break;
                        }
                        case Dops.IF_EQ:
                        case Dops.IF_EQZ:
                        case Dops.IF_GE:
                        case Dops.IF_GEZ:
                        case Dops.IF_GT:
                        case Dops.IF_GTZ:
                        case Dops.IF_LE:
                        case Dops.IF_LEZ:
                        case Dops.IF_LT:
                        case Dops.IF_LTZ:
                        case Dops.IF_NE:
                        case Dops.IF_NEZ: {
                            writer.write(", ");
                            writer.write(SMALI_LABEL_COND_PREFIX);
                            writer.printSignedIntAsDec(targetLabelInfo.condId);
                            break;
                        }
                        default: {
                            throw new IllegalStateException("unkown target opcode");
                        }
                    }

                } else if (ins instanceof DexConstInsnNode) {
                    DexConstInsnNode constInsnNode = (DexConstInsnNode) ins;
                    DexConst dexConst = constInsnNode.getConst();
                    writer.write(", ");

                    if (dop.opcode == Dops.FILL_ARRAY_DATA) {
                        writer.write(SMALI_LABEL_ARRAY_DATA_PREFIX);
                        writer.printSignedIntAsDec(smaliInfo.arrayDataIdx);
                    } else {
                        writer.write(dexConst.toSmaliString());
                    }
                } else if (ins instanceof DexSwitchDataInsnNode) {
                    writer.write(", ");
                    boolean packed = opcodeInsnNode.getOpcode() == Dops.PACKED_SWITCH;
                    writer.write(packed ?
                            SMALI_LABEL_PACK_SWITCH_PREFIX : SMALI_LABEL_SPARSE_SWITCH_PREFIX);
                    writer.printSignedIntAsDec(packed ?
                            smaliInfo.packDataIdx : smaliInfo.sparseDataIdx);
                } else if (ins instanceof DexSimpleInsnNode) {
                    // do nothing
                }
                writer.newLine();
                writer.newLine();
            }
        });

        writer.newLine();

        // # write packed switch data
        insns.stream()
                .filter(ins -> ins instanceof DexSwitchDataInsnNode &&
                        ((DexSwitchDataInsnNode) ins).getOpcode() == Dops.PACKED_SWITCH)
                .map(ins -> (DexSwitchDataInsnNode)ins)
                .forEach(switchIns -> {
                    SmaliInfo smaliInfo = smaliInfos.get(switchIns);
                    writer.write(SMALI_LABEL_PACK_SWITCH_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.packDataIdx);
                    writer.newLine();

                    writer.write(SMALI_DIRECTIVE_PACK_SWITCH);
                    writer.write(' ');
                    writer.write("0x");
                    writer.write(Integer.toHexString(switchIns.getKeys()[0]));
                    writer.newLine();

                    writer.indent(4);
                    for (DexLabelNode switchLabel : switchIns.getCasesLabel()) {
                        writer.write(SMALI_LABEL_PACK_SWITCH_ITEM_PREFIX);
                        writer.printSignedIntAsDec(smaliInfos.get(switchLabel).packedSwitchIdx);
                        writer.newLine();
                    }
                    writer.deindent(4);

                    writer.write(SMALI_DIRECTIVE_PACK_SWITCH_END);
                    writer.newLine();
                });

        // # write sparse switch data
        insns.stream()
                .filter(ins -> ins instanceof DexSwitchDataInsnNode &&
                        ((DexSwitchDataInsnNode) ins).getOpcode() == Dops.SPARSE_SWITCH)
                .map(ins -> (DexSwitchDataInsnNode)ins)
                .forEach(switchIns -> {
                    SmaliInfo smaliInfo = smaliInfos.get(switchIns);
                    writer.write(SMALI_LABEL_SPARSE_SWITCH_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.sparseDataIdx);
                    writer.newLine();

                    writer.write(SMALI_DIRECTIVE_SPARSE_SWITCH);
                    writer.newLine();

                    writer.indent(4);
                    for (int i = 0; i < switchIns.getKeys().length; i++) {
                        int key = switchIns.getKeys()[i];
                        DexLabelNode switchLabel = switchIns.getCasesLabel()[i];
                        writer.write(String.format("0x%x", key));
                        writer.write(" -> ");
                        writer.write(SMALI_LABEL_SPARSE_SWITCH_ITEM_PREFIX);
                        writer.printSignedIntAsDec(smaliInfos.get(switchLabel).sparseSwitchIdx);
                        writer.newLine();
                    }

                    writer.deindent(4);

                    writer.write(SMALI_DIRECTIVE_SPARSE_SWITCH_END);
                    writer.newLine();
                });

        // # write array data
        insns.stream()
                .filter(ins -> ins instanceof DexConstInsnNode)
                .map(ins -> (DexConstInsnNode)ins)
                .filter(ins -> ins.getOpcode() == Dops.FILL_ARRAY_DATA)
                .forEach(arrayDataIns -> {
                    DexConst.ArrayData arrayDataConst = (DexConst.ArrayData)arrayDataIns.getConst();
                    SmaliInfo smaliInfo = smaliInfos.get(arrayDataIns);
                    writer.write(SMALI_LABEL_ARRAY_DATA_PREFIX);
                    writer.printSignedIntAsDec(smaliInfo.arrayDataIdx);
                    writer.newLine();
                    arrayDataConst.smaliTo(writer);
                });
    }

    private void writeDexOpcodeInsnNodeCode(SmaliWriter writer, DexOpcodeInsnNode opcodeInsnNode, Dop dop) {
        String opCodeName = dop.getOpcodeName();
        DexRegisterList regList = opcodeInsnNode.getRegisters();


        // 显示 p 字命名法
        if (dop.isInvokeRange()) {
            writer.write("# register: ");
            for (int i = 0; i < regList.count(); i++) {
                if (i != 0) {
                    writer.write(", ");
                }
                writer.write(regList.get(i).toSmaliString());
            }
            writer.newLine();
        }

        writer.write(opCodeName);

        if (dop.getFormat() != FORMAT_10X) {
            writer.write(' ');
        }

        // # write register
        if (dop.isInvokeKind()) {
            writer.write('{');

            if (dop.isInvokeRange()) {
                if (regList.count() > 0) {
                    String firstReg = regList.get(0).toSmaliString();
                    String lastReg = regList.get(regList.count() - 1).toSmaliString();
                    writer.write(String.format("%s .. %s", firstReg, lastReg));
                }

            } else {
                for (int i = 0; i < regList.count(); i++) {
                    if (i != 0) {
                        writer.write(", ");
                    }
                    writer.write(regList.get(i).toSmaliString());
                }
            }


            writer.write('}');
        } else {
            for (int i = 0; i < regList.count(); i++) {
                if (i != 0) {
                    writer.write(", ");
                }
                writer.write(regList.get(i).toSmaliString());
            }
        }
    }

}