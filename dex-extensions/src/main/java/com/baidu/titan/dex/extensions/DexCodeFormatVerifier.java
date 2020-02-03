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

import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexItemFactory;
import com.baidu.titan.dex.DexRegister;
import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.Dop;
import com.baidu.titan.dex.Dops;
import com.baidu.titan.dex.visitor.DexCodeVisitor;
import com.baidu.titan.dex.visitor.DexLabel;

/**
 *
 * 检查DexCode格式，工作量比较大，后继不断扩充检查case
 *
 * @author zhangdi07@baidu.com
 * @since 2018/4/27
 */
public class DexCodeFormatVerifier extends DexCodeVisitor {

    private VerifyCallback mVerifyCallback;

    private boolean mRegisterVisited = false;

    private int mMaxParameterRegIndex = -1;

    private int mMaxLocalRegIndex = -1;

    private int mMaxUnspecifiedRegIndex = -1;

    private int mParaRegCount;

    private int mLocalRegCount;

    public DexCodeFormatVerifier() {
        this(null);
    }

    public DexCodeFormatVerifier(DexCodeVisitor delegate) {
        this(delegate, new DefaultVerifyCallback());
    }

    public DexCodeFormatVerifier(DexCodeVisitor delegate, VerifyCallback verifyCallback) {
        super(delegate);
        this.mVerifyCallback = verifyCallback;
    }

    public interface VerifyCallback {

        void onError(String msg);

    }

    public static class DefaultVerifyCallback implements VerifyCallback {

        @Override
        public void onError(String msg) {
            throw new IllegalArgumentException(msg);
        }

    }

    @Override
    public void visitBegin() {
        super.visitBegin();
    }

    @Override
    public void visitRegisters(int localRegCount, int parameterRegCount) {
        mRegisterVisited = true;
        super.visitRegisters(localRegCount, parameterRegCount);
        this.mParaRegCount = parameterRegCount;
        this.mLocalRegCount = localRegCount;
    }

    private void statInsRegsCount(DexRegisterList regs) {
        for (DexRegister reg : regs) {
            int regIdx = reg.getReg();
            switch (reg.getRef()) {
                case DexRegister.REG_REF_LOCAL: {
                    if (regIdx > mMaxLocalRegIndex) {
                        mMaxLocalRegIndex = regIdx;
                    }
                    break;
                }
                case DexRegister.REG_REF_PARAMETER: {
                    if (regIdx > mMaxParameterRegIndex) {
                        mMaxParameterRegIndex = regIdx;
                    }
                    break;
                }
                case DexRegister.REG_REF_UNSPECIFIED: {
                    if (regIdx > mMaxUnspecifiedRegIndex) {
                        mMaxUnspecifiedRegIndex = regIdx;
                    }
                    break;
                }
                default: {
                    throw new IllegalArgumentException("unknown reg ref " + reg.getRef());
                }
            }
        }
    }

    private void verifyRegsCount() {
        if (mMaxUnspecifiedRegIndex + 1 > mLocalRegCount + mParaRegCount) {
            mVerifyCallback.onError(
                    String.format("recode max unspecified reg index is %d, "
                                    + "but localReg count %d and paraReg count %d",
                            mMaxUnspecifiedRegIndex, mLocalRegCount, mParaRegCount));
        }

        if (mMaxLocalRegIndex + 1 > mLocalRegCount) {
            mVerifyCallback.onError(
                    String.format("recode max local reg index is %d, "
                                    + "but localReg count %d and paraReg count %d",
                            mMaxLocalRegIndex, mLocalRegCount, mParaRegCount));
        }

        if (mMaxParameterRegIndex + 1 > mParaRegCount) {
            mVerifyCallback.onError(
                    String.format("recode max parameter reg index is %d, "
                                    + "but localReg count %d and paraReg count %d",
                            mMaxParameterRegIndex, mLocalRegCount, mParaRegCount));
        }
    }

    @Override
    public void visitTryCatch(DexLabel start, DexLabel end, DexTypeList types, DexLabel[] handlers,
                              DexLabel catchAllHandler) {
        super.visitTryCatch(start, end, types, handlers, catchAllHandler);
    }

    @Override
    public void visitLabel(DexLabel label) {
        super.visitLabel(label);
    }

    private boolean isInvokeKind(int op) {
        switch (op) {
            case Dops.INVOKE_DIRECT:
            case Dops.INVOKE_DIRECT_RANGE:
            case Dops.INVOKE_INTERFACE:
            case Dops.INVOKE_INTERFACE_RANGE:
            case Dops.INVOKE_STATIC:
            case Dops.INVOKE_STATIC_RANGE:
            case Dops.INVOKE_SUPER:
            case Dops.INVOKE_SUPER_RANGE:
            case Dops.INVOKE_VIRTUAL:
            case Dops.INVOKE_VIRTUAL_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    private boolean isInvokeStatic(int op) {
        switch (op) {
            case Dops.INVOKE_STATIC:
            case Dops.INVOKE_STATIC_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    private boolean isInvokeRange(int op) {
        switch (op) {
            case Dops.INVOKE_DIRECT_RANGE:
            case Dops.INVOKE_INTERFACE_RANGE:
            case Dops.INVOKE_STATIC_RANGE:
            case Dops.INVOKE_SUPER_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    private boolean verifyRegsNonNullable(int op, DexRegisterList regs) {
        int count = regs.count();
        for (int i = 0; i < count; i++) {
            DexRegister reg = regs.get(i);
            if (reg == null) {
                mVerifyCallback.onError(
                        String.format("%s opcode's regs at index %d is empty.",
                                Dops.dopFor(op),
                                i));
                return false;
            }
        }
        return true;
    }

    private boolean verifyInvoke(int op, DexRegisterList regs, DexConst.ConstMethodRef methodRef) {
        boolean staticCall = isInvokeStatic(op);
        int expectedRegCount = (staticCall ? 0 : 1) + methodRef.getParameterTypes().count();
        if (expectedRegCount != regs.count()) {
            mVerifyCallback.onError(
                    String.format("%s call %s expect %d regs, but %d regs",
                            Dops.dopFor(op).getOpcodeName(),
                            methodRef.toString(),
                            expectedRegCount,
                            regs.count()));
            return false;
        }

        boolean rangeCall = isInvokeRange(op);

        DexRegister lastReg = null;
        int nextRegIdx = 0;
        if (!staticCall) {
            DexRegister thisReg = regs.get(nextRegIdx++);
            lastReg = thisReg;
            if (thisReg.getWidth() != DexRegister.REG_WIDTH_ONE_WORD) {
                mVerifyCallback.onError(
                        String.format("this reg for %s call expected one word width", Dops.dopFor(op)));
                return false;
            }
        }

        for (int i = 0; i < methodRef.getParameterTypes().count(); i++) {
            DexRegister reg = regs.get(nextRegIdx);

            DexType paraType = methodRef.getParameterTypes().getType(i);
            switch (paraType.toShortDescriptor()) {
                case DexItemFactory.LongClass.SHORT_DESCRIPTOR:
                case DexItemFactory.DoubleClass.SHORT_DESCRIPTOR: {
                    if (reg.getWidth() != DexRegister.REG_WIDTH_DOUBLE_WORD) {
                        mVerifyCallback.onError(
                                String.format("%s call for para type (idx = %d) require reg pair",
                                        Dops.dopFor(op),
                                        i));
                        return false;
                    }
                    break;
                }
                default: {
                    if (reg.getWidth() != DexRegister.REG_WIDTH_ONE_WORD) {
                        mVerifyCallback.onError(
                                String.format("%s call for para type (idx = %d) require only one " +
                                                "reg",
                                        Dops.dopFor(op),
                                        i));
                        return false;
                    }
                    break;
                }
            }

            if (rangeCall) {
                if (lastReg != null) {
                    // 目前只处理ref相同的
                    if (lastReg.getRef() == reg.getRef()) {
                        if (lastReg.getWidth() == DexRegister.REG_WIDTH_ONE_WORD) {
                            if (lastReg.getReg() + 1 != reg.getReg()) {
                                mVerifyCallback.onError(
                                        String.format("rang call %s except seq regs, but %s",
                                                Dops.dopFor(op),
                                                regs));
                                return false;
                            }
                        } else if (lastReg.getWidth() == DexRegister.REG_WIDTH_DOUBLE_WORD){
                            if (lastReg.getReg() + 2 != reg.getReg()) {
                                mVerifyCallback.onError(
                                        String.format("rang call %s except seq regs, but %s",
                                                Dops.dopFor(op),
                                                regs));
                                return false;
                            }
                        }
                    }
                }
            }
            lastReg = reg;

            nextRegIdx++;
        }

        return true;
    }



    @Override
    public void visitConstInsn(int op, DexRegisterList regs, DexConst dexConst) {
        Dop dop = Dops.dopFor(op);
        if (!dop.isConstOpcode()) {
            mVerifyCallback.onError(
                    String.format("%s expect const opcode, but %s", dop, dop.getOpcodeCategory()));
            return;
        }
        statInsRegsCount(regs);
        if (verifyRegsNonNullable(op, regs)) {
            if (isInvokeKind(op)) {
                verifyInvoke(op, regs, (DexConst.ConstMethodRef)dexConst);
            }
        }

        super.visitConstInsn(op, regs, dexConst);
    }

    @Override
    public void visitTargetInsn(int op, DexRegisterList regs, DexLabel label) {
        Dop dop = Dops.dopFor(op);
        if (!dop.isTargetOpcode()) {
            mVerifyCallback.onError(
                    String.format("%s expect target opcode, but %s", dop, dop.getOpcodeCategory()));
            return;
        }
        statInsRegsCount(regs);
        if (verifyRegsNonNullable(op, regs)) {

        }
        super.visitTargetInsn(op, regs, label);
    }

    @Override
    public void visitSimpleInsn(int op, DexRegisterList regs) {
        Dop dop = Dops.dopFor(op);
        if (!dop.isSimpleOpcode()) {
            mVerifyCallback.onError(
                    String.format("%s expect simple opcode, but %s", dop, dop.getOpcodeCategory()));
            return;
        }
        statInsRegsCount(regs);
        if (verifyRegsNonNullable(op, regs)) {

        }
        super.visitSimpleInsn(op, regs);
    }

    @Override
    public void visitSwitch(int op, DexRegisterList regs, int[] keys, DexLabel[] targets) {
        Dop dop = Dops.dopFor(op);
        if (!dop.isSwitchOpcode()) {
            mVerifyCallback.onError(
                    String.format("%s expect switch opcode, but %s", dop, dop.getOpcodeCategory()));
            return;
        }
        statInsRegsCount(regs);
        if (verifyRegsNonNullable(op, regs)) {

        }
        super.visitSwitch(op, regs, keys, targets);
    }

    @Override
    public void visitParameters(DexString[] parameters) {
        super.visitParameters(parameters);
    }

    @Override
    public void visitLocal(int reg, DexString name, DexType type, DexString signature,
                           DexLabel start, DexLabel end) {
        super.visitLocal(reg, name, type, signature, start, end);
    }

    @Override
    public void visitLineNumber(int line, DexLabel start) {
        super.visitLineNumber(line, start);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        if (!mRegisterVisited) {
            mVerifyCallback.onError("end of code visit, but no register count is set");
            return;
        }
        verifyRegsCount();
    }

}
