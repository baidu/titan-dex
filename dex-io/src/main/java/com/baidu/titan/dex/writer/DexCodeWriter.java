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

import com.baidu.titan.dex.DexRegister;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.Dops;
import com.baidu.titan.dex.visitor.DexMethodVisitorInfo;
import com.baidu.titan.dexlib.dx.dex.DexOptions;
import com.baidu.titan.dexlib.dx.dex.code.ArrayData;
import com.baidu.titan.dexlib.dx.dex.code.CatchBuilder;
import com.baidu.titan.dexlib.dx.dex.code.DalvCode;
import com.baidu.titan.dexlib.dx.dex.code.LocalList;
import com.baidu.titan.dexlib.dx.dex.code.PositionList;
import com.baidu.titan.dexlib.dx.dex.code.SimpleInsn;
import com.baidu.titan.dexlib.dx.dex.code.SwitchData;
import com.baidu.titan.dexlib.dx.rop.code.RegisterSpec;
import com.baidu.titan.dexlib.dx.rop.code.RegisterSpecList;
import com.baidu.titan.dexlib.dx.rop.code.SourcePosition;
import com.baidu.titan.dexlib.dx.rop.cst.Constant;
import com.baidu.titan.dexlib.dx.rop.cst.CstFieldRef;
import com.baidu.titan.dexlib.dx.rop.cst.CstInteger;
import com.baidu.titan.dexlib.dx.rop.cst.CstLiteral32;
import com.baidu.titan.dexlib.dx.rop.cst.CstMethodRef;
import com.baidu.titan.dexlib.dx.rop.cst.CstNat;
import com.baidu.titan.dexlib.dx.rop.cst.CstType;
import com.baidu.titan.dexlib.dx.rop.type.Type;
import com.baidu.titan.dexlib.dx.util.IntList;
import com.baidu.titan.dex.visitor.DexCodeVisitor;
import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexConstant;
import com.baidu.titan.dex.visitor.DexLabel;
import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dexlib.dx.dex.code.CatchHandlerList;
import com.baidu.titan.dexlib.dx.dex.code.CatchTable;
import com.baidu.titan.dexlib.dx.dex.code.CodeAddress;
import com.baidu.titan.dexlib.dx.dex.code.CstInsn;
import com.baidu.titan.dexlib.dx.dex.code.OddSpacer;
import com.baidu.titan.dexlib.dx.dex.code.OutputCollector;
import com.baidu.titan.dexlib.dx.dex.code.TargetInsn;
import com.baidu.titan.dexlib.dx.rop.cst.CstLong;
import com.baidu.titan.dexlib.dx.rop.cst.CstString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * <p>
 * 用于生成DexCode, 当前实现中是通过AOSP dx lib生成最终二进制Dex格式，但是这样的话有个限制，就是需要
 * 在visitor的过程中，优先调用visitor.visitRegister()方法来获取寄存器个数，但是在DexCodeVisitor的访问
 * 顺序上做此类限制会灵活性，所以通过DexCodeNode作为存储来解决该问题。
 * </p>
 *
 * <em>内部使用，非公开API</em>
 *
 * <p>
 * Dex 指令集参考：http://source.android.com/devices/tech/dalvik/dalvik-bytecode.html <br>
 * http://source.android.com/devices/tech/dalvik/instruction-formats.html
 * </p>
 *
 * @author zhangdi07@baidu.com
 * @since 2017/3/15
 */
 abstract class DexCodeWriter extends DexCodeVisitor {

    private OutputCollector mOutputCollector;

    private HashMap<DexLabel, CodeAddress> mLabelMapping = new HashMap<>();

    private DexMethodVisitorInfo mDexMethodInfo;

    private DexCatchBuilder mDexCatchBuilder;

    private InstructionInterpter mInsnInterpter;

    private DexLabel mCurrentLineNumLabel;

    private ArrayList<Integer> mCurrentLineNums = new ArrayList<>();

    private List<LocalList.LocalEntry> mLocals = new ArrayList<>();

    private DexString[] mParameterNames;

    private int mRegSize;

    private int mParameterRegSize;


    public DexCodeWriter(DexMethodVisitorInfo dexMethodInfo) {
        super();
        this.mDexMethodInfo = dexMethodInfo;
        mDexCatchBuilder = new DexCatchBuilder();
        mInsnInterpter = new InstructionInterpter();
    }

    private CodeAddress newCodeAddressFromLabel(DexLabel dexLabel) {
        CodeAddress codeAddress = mLabelMapping.get(dexLabel);
        if (codeAddress == null) {
            codeAddress = new CodeAddress(new SourcePosition(null, -1, -1));
            mLabelMapping.put(dexLabel, codeAddress);
        }
        return codeAddress;
    }

    private CodeAddress getCodeAddressFromLabel(DexLabel dexLabel) {
        return mLabelMapping.get(dexLabel);
    }

    @Override
    public void visitRegisters(int localRegCount, int parameterRegCount) {
        this.mRegSize = localRegCount + parameterRegCount;
        this.mParameterRegSize = parameterRegCount;
        mOutputCollector = new OutputCollector(new DexOptions(), 30, 10,
                this.mRegSize, this.mParameterRegSize);
    }

    @Override
    public void visitTryCatch(DexLabel start, DexLabel end, DexTypeList types,
                              DexLabel[] handlers, DexLabel catchAllHandler) {
        mDexCatchBuilder.newTryCatch(start, end, types, handlers, catchAllHandler);
    }

    @Override
    public void visitParameters(DexString[] parameters) {
        this.mParameterNames = parameters;
    }

    @Override
    public void visitLocal(int reg, DexString name, DexType type, DexString signature,
                           DexLabel start, DexLabel end) {
        CodeAddress startAddr = newCodeAddressFromLabel(start);
        CodeAddress endAddr = newCodeAddressFromLabel(end);

        LocalList.LocalEntry localEntry = new LocalList.LocalEntry(
                reg, name.toString(), type.toTypeDescriptor(),
                signature != null ? signature.toString() : null, startAddr,
                endAddr);
        mLocals.add(localEntry);
    }


    @Override
    public void visitLineNumber(int line, DexLabel start) {
        if (mCurrentLineNumLabel != start) {
            mCurrentLineNumLabel = start;
            mCurrentLineNums.clear();
        }
        mCurrentLineNums.add(line);
    }

    @Override
    public void visitLabel(DexLabel label) {
        CodeAddress codeAddress = newCodeAddressFromLabel(label);
        mOutputCollector.add(codeAddress);
    }

    @Override
    public void visitConstInsn(int op, DexRegisterList regs, DexConst dexConst) {
        if (dexConst instanceof DexConst.ArrayData) {
            visitFillArrayData(op, regs, (DexConst.ArrayData)dexConst);
            return;
        }


        Constant constant;
        if (dexConst instanceof DexConst.LiteralBits32) {
            constant = CstInteger.make(((DexConst.LiteralBits32)dexConst).getIntBits());
        } else if (dexConst instanceof DexConst.LiteralBits64) {
            constant = CstLong.make(((DexConst.LiteralBits64)dexConst).getLongBits());
        } else if (dexConst instanceof DexConst.ConstString) {
            constant = new CstString(((DexConst.ConstString) dexConst).value());
        } else if (dexConst instanceof DexConst.ConstType) {
            constant = CstType.intern(
                    Type.intern(((DexConst.ConstType) dexConst).value().toTypeDescriptor()));
        } else if (dexConst instanceof DexConst.ConstFieldRef) {
            DexConst.ConstFieldRef constFieldRef = (DexConst.ConstFieldRef) dexConst;
            constant = new CstFieldRef(
                    CstType.intern(Type.intern(constFieldRef.getOwner().toTypeDescriptor())),
                    new CstNat(new CstString(constFieldRef.getName().toString()),
                            new CstString(constFieldRef.getType().toTypeDescriptor())));
        } else if (dexConst instanceof DexConst.ConstMethodRef) {
            DexConst.ConstMethodRef constMethodRef = (DexConst.ConstMethodRef) dexConst;

            StringBuilder dp = new StringBuilder();
            dp.append("(");
            constMethodRef.getParameterTypes().forEach(t -> dp.append(t.toTypeDescriptor()));
            dp.append(")");
            dp.append(constMethodRef.getReturnType().toTypeDescriptor());

            constant = new CstMethodRef(
                    CstType.intern(Type.intern(constMethodRef.getOwner().toTypeDescriptor())),
                    new CstNat(new CstString(constMethodRef.getName().toString()),
                            new CstString(dp.toString())));
        } else {
            throw new IllegalStateException("unkown const " + dexConst + " op = " + op);
        }
        RegisterSpecList regList = mInsnInterpter.visitConstInsn(op, regs, dexConst);

        mOutputCollector.add(
                new CstInsn(getDop(op), createCurSourcePosition(), regList, constant));
    }

    private SourcePosition createCurSourcePosition() {
        if (mCurrentLineNums.size() > 0) {
            ArrayList<Integer> lineList = new ArrayList<>(mCurrentLineNums);
//            Collections.sort(lineList, new Comparator<Integer>() {
//                @Override
//                public int compare(Integer l, Integer r) {
//                    return r - l;
//                }
//            });
            int[] lines = new int[lineList.size()];
            for (int i = 0; i < lines.length; i++) {
                lines[i] = lineList.get(i);
            }
            return new SourcePosition(null, -1, lines);
        }

        return new SourcePosition(null, -1, -1);
    }

    @Override
    public void visitTargetInsn(int op, DexRegisterList regs, DexLabel label) {
        CodeAddress codeAddress = newCodeAddressFromLabel(label);
        RegisterSpecList regList = mInsnInterpter.visitTargetInsn(op, regs, label);
        mOutputCollector.add(
                new TargetInsn(getDop(op), createCurSourcePosition(), regList, codeAddress));
        mOutputCollector.add(new CodeAddress(new SourcePosition(null, -1, -1)));
    }

    @Override
    public void visitSimpleInsn(int op, DexRegisterList regs) {
        RegisterSpecList regList = mInsnInterpter.visitSimpleInsn(op, regs);
        mOutputCollector.add(new SimpleInsn(getDop(op), createCurSourcePosition(), regList));
    }


    private void visitFillArrayData(int op, DexRegisterList regs, DexConst.ArrayData arrayData) {
        int width = arrayData.getWidth();
        int length = arrayData.getLength();
        byte[] data = arrayData.getData();
        SourcePosition fillArrayInsPos = createCurSourcePosition();
        SourcePosition arrayDataInsPos = createCurSourcePosition();
        CodeAddress dataAddress = new CodeAddress(arrayDataInsPos);
        CodeAddress curAddress = new CodeAddress(fillArrayInsPos);
        ArrayData dataInsn = new ArrayData(fillArrayInsPos, curAddress, width, length, data);
        mOutputCollector.add(curAddress);
        RegisterSpecList regList = mInsnInterpter.visitFillArrayData(op, regs, width, length, data);
        mOutputCollector.add(new TargetInsn(getDop(op), fillArrayInsPos, regList, dataAddress));

        mOutputCollector.addSuffix(new OddSpacer(arrayDataInsPos));
        mOutputCollector.addSuffix(dataAddress);
        mOutputCollector.addSuffix(dataInsn);
    }

    @Override
    public void visitSwitch(int op, DexRegisterList regs, int[] keys, DexLabel[] targets) {
        SourcePosition switchPos = createCurSourcePosition();
        SourcePosition switchDataPos = createCurSourcePosition();

        CodeAddress curAddress = new CodeAddress(switchPos);
        CodeAddress dataAddress = new CodeAddress(switchDataPos);

        IntList caseList = new IntList(keys.length);
        for (int i = 0; i < keys.length; i++) {
            caseList.add(keys[i]);
        }
        CodeAddress[] targetAdds = new CodeAddress[targets.length];
        for (int i = 0; i < targets.length; i++) {
            DexLabel targetLabel = targets[i];
            targetAdds[i] = newCodeAddressFromLabel(targetLabel);
        }
        SwitchData dataInsn = new SwitchData(switchDataPos, curAddress, caseList, targetAdds,
                op == Dops.PACKED_SWITCH);
        mOutputCollector.add(curAddress);
        RegisterSpecList regList = mInsnInterpter.visitSwitch(op, regs, keys, targets);
        mOutputCollector.add(new TargetInsn(getDop(op), switchPos, regList, dataAddress));
        mOutputCollector.addSuffix(new OddSpacer(switchDataPos));
        mOutputCollector.addSuffix(dataAddress);
        mOutputCollector.addSuffix(dataInsn);
    }

    @Override
    public void visitEnd() {
        DalvCode dalvCode = new DalvCode(PositionList.LINES, mOutputCollector.getFinisher(),
                mDexCatchBuilder);
        dalvCode.setLocalEntrys(mLocals);
        String[] parameterNames = null;
        if (mParameterNames != null) {
            parameterNames = new String[mParameterNames.length];
            for (int i = 0; i < parameterNames.length; i++) {
                parameterNames[i] = mParameterNames[i].toString();
            }
        }
        dalvCode.setParameterNames(parameterNames);
        writeDexCodeEnd(dalvCode);
    }

    public abstract void writeDexCodeEnd(DalvCode dalvCode);


    class DexCatchBuilder implements CatchBuilder {

        class DexTryCatchItem {
            DexLabel start;
            DexLabel end;
            DexTypeList types;
            DexLabel[] handlers;
            DexLabel catchAllHandler;

            DexTryCatchItem(DexLabel start, DexLabel end, DexTypeList types, DexLabel[] handlers,
                    DexLabel catchAllHandler) {
                this.start = start;
                this.end = end;
                this.types = types;
                this.handlers = handlers;
                this.catchAllHandler = catchAllHandler;
            }
        }

        private HashSet<Type> mCatchTypes = new HashSet<>();
        private List<DexTryCatchItem> mDexTryCatches = new ArrayList<>();

        void newTryCatch(DexLabel start, DexLabel end, DexTypeList types, DexLabel[] handlers,
                         DexLabel catchAllHandler) {
            if (types != null) {
                for (DexType type : types.types()) {
                    mCatchTypes.add(Type.intern(type.toTypeDescriptor()));
                }
            }

            if (catchAllHandler != null) {
                mCatchTypes.add(Type.OBJECT);
            }
            mDexTryCatches.add(new DexTryCatchItem(start, end, types, handlers, catchAllHandler));
        }


        @Override
        public CatchTable build() {
            CatchTable catchTable = new CatchTable(mDexTryCatches.size());
            for (int i = 0; i < mDexTryCatches.size(); i++) {
                DexTryCatchItem tryCatchItem = mDexTryCatches.get(i);

                int handlersCount = tryCatchItem.handlers == null ? 0 : tryCatchItem.handlers.length;
                if (tryCatchItem.catchAllHandler != null) {
                    handlersCount++;
                }
                CatchHandlerList catchHandlerList = new CatchHandlerList(handlersCount);
                if (tryCatchItem.handlers != null) {
                    for (int j = 0; j < tryCatchItem.handlers.length; j++) {
                        DexLabel handleLabel = tryCatchItem.handlers[j];
                        CodeAddress handleCodeAddr = getCodeAddressFromLabel(handleLabel);
                        CstType handleType;
                        DexType dexType = tryCatchItem.types.types()[j];
//                    if (dexType == null) {
//                        handleType = CstType.OBJECT;
//                    } else {
                        handleType = CstType.intern(Type.intern(dexType.toTypeDescriptor()));
//                    }
                        CatchHandlerList.Entry catchHandleEntry = new CatchHandlerList.Entry(
                                handleType, handleCodeAddr.getAddress());
                        catchHandlerList.set(j, catchHandleEntry);
                    }
                }
                if (tryCatchItem.catchAllHandler != null) {
                    catchHandlerList.set(handlersCount - 1, new CatchHandlerList.Entry(
                            CstType.OBJECT,
                            getCodeAddressFromLabel(tryCatchItem.catchAllHandler).getAddress()));
                }

                catchHandlerList.setImmutable();

                CatchTable.Entry catchTableEntry = new CatchTable.Entry(
                        getCodeAddressFromLabel(tryCatchItem.start).getAddress(),
                        getCodeAddressFromLabel(tryCatchItem.end).getAddress(), catchHandlerList);
                catchTable.set(i, catchTableEntry);
            }
            return catchTable;
        }

        @Override
        public boolean hasAnyCatches() {
            return mDexTryCatches.size() > 0;
        }

        @Override
        public HashSet<Type> getCatchTypes() {
            return mCatchTypes;
        }
    }


    class InstructionInterpter {

        private int getRegNumber(DexRegister dexRegister) {
            switch (dexRegister.getRef()) {
                case DexRegister.REG_REF_UNSPECIFIED: {
                    return dexRegister.getReg();
                }
                case DexRegister.REG_REF_LOCAL: {
                    return dexRegister.getReg();
                }
                case DexRegister.REG_REF_PARAMETER: {
                    return (mRegSize - mParameterRegSize) + dexRegister.getReg();
                }
                default: {
                    throw new IllegalArgumentException("unkown reg number : " +
                            dexRegister.getReg());
                }

            }
        }

        RegisterSpecList getRegs() {
            return null;
        }

        RegisterSpecList visitConstInsn(int op, DexRegisterList regs, DexConst dexConst) {
            RegisterSpecList regList = new RegisterSpecList(regs.count());
            switch (op) {
                case Dops.CONST_4:
                case Dops.CONST_16:
                case Dops.CONST:
                case Dops.CONST_HIGH16: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    break;
                }
                case Dops.CONST_WIDE_16:
                case Dops.CONST_WIDE_32:
                case Dops.CONST_WIDE:
                case Dops.CONST_WIDE_HIGH16: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    break;
                }
                case Dops.CONST_STRING:
                case Dops.CONST_STRING_JUMBO: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.STRING));
                    break;
                }
                case Dops.CONST_CLASS: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.CLASS));
                    break;
                }
                case Dops.CHECK_CAST: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    break;
                }
                case Dops.INSTANCE_OF: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                case Dops.NEW_INSTANCE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    break;
                }
                case Dops.NEW_ARRAY: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                case Dops.FILLED_NEW_ARRAY:
                case Dops.FILLED_NEW_ARRAY_RANGE:{
                    Type eleType = Type.intern(
                           ((DexConst.ConstType) dexConst).value().toTypeDescriptor().substring(1));
                    for (int i = 0; i < regs.count(); i++) {
                        regList.set(i, RegisterSpec.make(getRegNumber(regs.get(i)), eleType));
                    }
                    break;
                }

                // iinstanceop vA, vB, field@CCCC
                case Dops.IGET:
                case Dops.IPUT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                case Dops.IGET_WIDE:
                case Dops.IPUT_WIDE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                case Dops.IGET_OBJECT:
                case Dops.IPUT_OBJECT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                case Dops.IGET_BOOLEAN:
                case Dops.IPUT_BOOLEAN: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.BOOLEAN));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                case Dops.IGET_BYTE:
                case Dops.IPUT_BYTE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.BYTE));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                case Dops.IGET_CHAR:
                case Dops.IPUT_CHAR: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.CHAR));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                case Dops.IGET_SHORT:
                case Dops.IPUT_SHORT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.SHORT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                // sstaticop vAA, field@BBBB
                case Dops.SGET:
                case Dops.SPUT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    break;
                }
                case Dops.SGET_WIDE:
                case Dops.SPUT_WIDE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    break;
                }
                case Dops.SGET_OBJECT:
                case Dops.SPUT_OBJECT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    break;
                }
                case Dops.SGET_BOOLEAN:
                case Dops.SPUT_BOOLEAN: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.BOOLEAN));
                    break;
                }
                case Dops.SGET_BYTE:
                case Dops.SPUT_BYTE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.BYTE));
                    break;
                }
                case Dops.SGET_CHAR:
                case Dops.SPUT_CHAR: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.CHAR));
                    break;
                }
                case Dops.SGET_SHORT:
                case Dops.SPUT_SHORT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.SHORT));
                    break;
                }
                case Dops.INVOKE_VIRTUAL:
                case Dops.INVOKE_VIRTUAL_RANGE:
                case Dops.INVOKE_SUPER:
                case Dops.INVOKE_SUPER_RANGE:
                case Dops.INVOKE_DIRECT:
                case Dops.INVOKE_DIRECT_RANGE:
                case Dops.INVOKE_INTERFACE:
                case Dops.INVOKE_INTERFACE_RANGE:{
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    DexTypeList types = ((DexConst.ConstMethodRef) dexConst).getParameterTypes();

                    for (int i = 0; i < types.count(); i++) {
                        String typeDesc = types.getType(i).toTypeDescriptor();
                        regList.set(i + 1, RegisterSpec.make(
                                    getRegNumber(regs.get(i + 1)), Type.intern(typeDesc)));
                    }
                    break;
                }
                case Dops.INVOKE_STATIC:
                case Dops.INVOKE_STATIC_RANGE: {
                    DexTypeList types = ((DexConst.ConstMethodRef) dexConst).getParameterTypes();

                    for (int i = 0; i < types.count(); i++) {
                        String typeDesc = types.getType(i).toTypeDescriptor();
                        regList.set(i, RegisterSpec.make(
                                getRegNumber(regs.get(i)), Type.intern(typeDesc)));
                    }
                    break;
                }

                // binop/lit16 vA, vB, #+CCCC
                // binop/lit8 vAA, vBB, #+CC
                case Dops.ADD_INT_LIT16:
                case Dops.RSUB_INT:
                case Dops.MUL_INT_LIT16:
                case Dops.DIV_INT_LIT16:
                case Dops.REM_INT_LIT16:
                case Dops.AND_INT_LIT16:
                case Dops.OR_INT_LIT16:
                case Dops.XOR_INT_LIT16:
                case Dops.ADD_INT_LIT8:
                case Dops.RSUB_INT_LIT8:
                case Dops.MUL_INT_LIT8:
                case Dops.DIV_INT_LIT8:
                case Dops.REM_INT_LIT8:
                case Dops.AND_INT_LIT8:
                case Dops.OR_INT_LIT8:
                case Dops.XOR_INT_LIT8:
                case Dops.SHL_INT_LIT8:
                case Dops.SHR_INT_LIT8:
                case Dops.USHR_INT_LIT8: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                default: {
                    throw new IllegalStateException(String.format("unknown op = 0x%x " + mDexMethodInfo.owner + " " + mDexMethodInfo.name , op));
                }
            }
            return regList;
        }

        RegisterSpecList visitTargetInsn(int op, DexRegisterList regs, DexLabel label) {
            RegisterSpecList regList = new RegisterSpecList(regs.count());
            switch (op) {
                case Dops.GOTO :
                case Dops.GOTO_16 :
                case Dops.GOTO_32 : {
                    regList = RegisterSpecList.EMPTY;
                    break;
                }
                case Dops.IF_EQ :
                case Dops.IF_NE :
                case Dops.IF_LT :
                case Dops.IF_GE :
                case Dops.IF_GT :
                case Dops.IF_LE :
                case Dops.IF_EQZ :
                case Dops.IF_NEZ :
                case Dops.IF_LTZ :
                case Dops.IF_GEZ :
                case Dops.IF_GTZ :
                case Dops.IF_LEZ :{
                    for (int i = 0; i < regs.count(); i++) {
                        regList.set(i, RegisterSpec.make(getRegNumber(regs.get(i)), Type.INT));
                    }
                    break;
                }

                default: {
                    throw new IllegalStateException("unkown op = " + op);
                }
            }
            return regList;

        }

        RegisterSpecList visitSimpleInsn(int op, DexRegisterList regs) {
            RegisterSpecList regList = new RegisterSpecList(regs.count());
            switch (op) {
                case Dops.NOP: {
                    break;
                }

                case Dops.MOVE:
                case Dops.MOVE_FROM16:
                case Dops.MOVE_16: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                case Dops.MOVE_WIDE:
                case Dops.MOVE_WIDE_FROM16:
                case Dops.MOVE_WIDE_16: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.LONG));
                    break;
                }
                case Dops.MOVE_OBJECT:
                case Dops.MOVE_OBJECT_FROM16:
                case Dops.MOVE_OBJECT_16: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                case Dops.MOVE_RESULT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    break;
                }
                case Dops.MOVE_RESULT_WIDE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    break;
                }
                case Dops.MOVE_RESULT_OBJECT:
                case Dops.MOVE_EXCEPTION: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    break;
                }
                case Dops.RETURN_VOID: {
                    break;
                }
                case Dops.RETURN: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    break;
                }
                case Dops.RETURN_WIDE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    break;
                }
                case Dops.RETURN_OBJECT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    break;
                }
                case Dops.MONITOR_ENTER:
                case Dops.MONITOR_EXIT:
                case Dops.THROW:    {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    break;
                }
                case Dops.ARRAY_LENGTH: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.OBJECT));
                    break;
                }
                case Dops.CMPL_FLOAT:
                case Dops.CMPG_FLOAT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.FLOAT));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.FLOAT));
                    break;
                }
                case Dops.CMPL_DOUBLE:
                case Dops.CMPG_DOUBLE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.DOUBLE));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.DOUBLE));
                    break;
                }
                case Dops.CMP_LONG: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.LONG));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.LONG));
                    break;
                }
                // arrayop vAA, vBB, vCC
                case Dops.AGET:
                case Dops.APUT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)),
                            Type.INT.getArrayType()));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.INT));
                    break;
                }
                case Dops.AGET_WIDE:
                case Dops.APUT_WIDE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)),
                            Type.LONG.getArrayType()));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.LONG));
                    break;
                }
                case Dops.AGET_OBJECT:
                case Dops.APUT_OBJECT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.OBJECT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)),
                            Type.OBJECT.getArrayType()));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.INT));
                    break;
                }
                case Dops.AGET_BOOLEAN:
                case Dops.APUT_BOOLEAN: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.BOOLEAN));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)),
                            Type.BOOLEAN.getArrayType()));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.INT));
                    break;
                }
                case Dops.AGET_BYTE:
                case Dops.APUT_BYTE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.BYTE));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)),
                            Type.BYTE.getArrayType()));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.INT));
                    break;
                }
                case Dops.AGET_CHAR:
                case Dops.APUT_CHAR: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.CHAR));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)),
                            Type.CHAR.getArrayType()));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.INT));
                    break;
                }
                case Dops.AGET_SHORT:
                case Dops.APUT_SHORT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.SHORT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)),
                            Type.SHORT.getArrayType()));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.INT));
                    break;
                }


                // unop
                case Dops.NEG_INT:
                case Dops.NOT_INT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                case Dops.NEG_LONG:
                case Dops.NOT_LONG: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.LONG));
                    break;
                }
                case Dops.NEG_FLOAT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.FLOAT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.FLOAT));
                    break;
                }
                case Dops.NEG_DOUBLE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.DOUBLE));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.DOUBLE));
                    break;
                }
                case Dops.INT_TO_LONG: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                case Dops.INT_TO_FLOAT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.FLOAT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                case Dops.INT_TO_DOUBLE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.DOUBLE));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                case Dops.LONG_TO_INT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.LONG));
                    break;
                }
                case Dops.LONG_TO_FLOAT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.FLOAT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.LONG));
                    break;
                }
                case Dops.LONG_TO_DOUBLE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.DOUBLE));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.LONG));
                    break;
                }
                case Dops.FLOAT_TO_INT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.FLOAT));
                    break;
                }
                case Dops.FLOAT_TO_LONG: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.FLOAT));
                    break;
                }
                case Dops.FLOAT_TO_DOUBLE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.DOUBLE));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.FLOAT));
                    break;
                }
                case Dops.DOUBLE_TO_INT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.DOUBLE));
                    break;
                }
                case Dops.DOUBLE_TO_LONG: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.DOUBLE));
                    break;
                }
                case Dops.DOUBLE_TO_FLOAT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.FLOAT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.DOUBLE));
                    break;
                }
                case Dops.INT_TO_BYTE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.BYTE));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                case Dops.INT_TO_CHAR: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.CHAR));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                case Dops.INT_TO_SHORT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.SHORT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                // binop
                case Dops.ADD_INT:
                case Dops.SUB_INT:
                case Dops.MUL_INT:
                case Dops.DIV_INT:
                case Dops.REM_INT:
                case Dops.AND_INT:
                case Dops.OR_INT:
                case Dops.XOR_INT:
                case Dops.SHL_INT:
                case Dops.SHR_INT:
                case Dops.USHR_INT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.INT));
                    break;
                }
                case Dops.ADD_LONG:
                case Dops.SUB_LONG:
                case Dops.MUL_LONG:
                case Dops.DIV_LONG:
                case Dops.REM_LONG:
                case Dops.AND_LONG:
                case Dops.OR_LONG:
                case Dops.XOR_LONG:
                case Dops.SHL_LONG:
                case Dops.SHR_LONG:
                case Dops.USHR_LONG: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.LONG));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.LONG));
                    break;
                }
                case Dops.ADD_FLOAT:
                case Dops.SUB_FLOAT:
                case Dops.MUL_FLOAT:
                case Dops.DIV_FLOAT:
                case Dops.REM_FLOAT: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.FLOAT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.FLOAT));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.FLOAT));
                    break;
                }
                case Dops.ADD_DOUBLE:
                case Dops.SUB_DOUBLE:
                case Dops.MUL_DOUBLE:
                case Dops.DIV_DOUBLE:
                case Dops.REM_DOUBLE: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.DOUBLE));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.DOUBLE));
                    regList.set(2, RegisterSpec.make(getRegNumber(regs.get(2)), Type.DOUBLE));
                    break;
                }

                // binop/2addr vA, vB
                case Dops.ADD_INT_2ADDR:
                case Dops.SUB_INT_2ADDR:
                case Dops.MUL_INT_2ADDR:
                case Dops.DIV_INT_2ADDR:
                case Dops.REM_INT_2ADDR:
                case Dops.AND_INT_2ADDR:
                case Dops.OR_INT_2ADDR:
                case Dops.XOR_INT_2ADDR:
                case Dops.SHL_INT_2ADDR:
                case Dops.SHR_INT_2ADDR:
                case Dops.USHR_INT_2ADDR: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.INT));
                    break;
                }
                case Dops.ADD_LONG_2ADDR:
                case Dops.SUB_LONG_2ADDR:
                case Dops.MUL_LONG_2ADDR:
                case Dops.DIV_LONG_2ADDR:
                case Dops.REM_LONG_2ADDR:
                case Dops.AND_LONG_2ADDR:
                case Dops.OR_LONG_2ADDR:
                case Dops.XOR_LONG_2ADDR:
                case Dops.SHL_LONG_2ADDR:
                case Dops.SHR_LONG_2ADDR:
                case Dops.USHR_LONG_2ADDR: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.LONG));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.LONG));
                    break;
                }
                case Dops.ADD_FLOAT_2ADDR:
                case Dops.SUB_FLOAT_2ADDR:
                case Dops.MUL_FLOAT_2ADDR:
                case Dops.DIV_FLOAT_2ADDR:
                case Dops.REM_FLOAT_2ADDR: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.FLOAT));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.FLOAT));
                    break;
                }
                case Dops.ADD_DOUBLE_2ADDR:
                case Dops.SUB_DOUBLE_2ADDR:
                case Dops.MUL_DOUBLE_2ADDR:
                case Dops.DIV_DOUBLE_2ADDR:
                case Dops.REM_DOUBLE_2ADDR: {
                    regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.DOUBLE));
                    regList.set(1, RegisterSpec.make(getRegNumber(regs.get(1)), Type.DOUBLE));
                    break;
                }

                default: {
                    throw new IllegalStateException("unkown op = " + op);
                }
            }
            return regList;
        }

        RegisterSpecList visitFillArrayData(int op, DexRegisterList regs, int width,
                                            int length, byte[] data) {
            Type type;
            switch (width) {
                case 1: {
                    type = Type.BYTE;
                    break;
                }
                case 2: {
                    type = Type.CHAR;
                    break;
                }
                case 4: {
                    type = Type.INT;
                    break;
                }
                case 8: {
                    type = Type.LONG;
                    break;
                }
                default: {
                    throw new IllegalStateException("unkown width = " + width);
                }
            }
            RegisterSpecList regList = new RegisterSpecList(regs.count());
            regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), type));
            return regList;
        }

        RegisterSpecList visitSwitch(int op, DexRegisterList regs, int[] keys, DexLabel[] targets) {
            RegisterSpecList regList = new RegisterSpecList(regs.count());
            regList.set(0, RegisterSpec.make(getRegNumber(regs.get(0)), Type.INT));
            return regList;
        }
    }

    private static com.baidu.titan.dexlib.dx.dex.code.Dop getDop(int dop) {
        return com.baidu.titan.dexlib.dx.dex.code.Dops.get(dop);
    }

}
