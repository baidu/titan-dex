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
import com.baidu.titan.dex.DexRegister;
import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.node.DexCodeNode;
import com.baidu.titan.dex.node.DexMethodNode;
import com.baidu.titan.dex.visitor.DexCodeVisitor;
import com.baidu.titan.dex.visitor.DexLabel;

/**
 *
 * 自动计算local和parameter寄存器个数，支持Visitor和Node两种形式
 *
 * @author zhangdi07@baidu.com
 * @since 2018/4/17
 */
public class DexCodeRegisterCalculator extends DexCodeVisitor {

    private int mLocalRegCount = 0;

    private int mParaRegCount = 0;

    private boolean mStaticMethod;

    private DexTypeList mParaTypes;

    public DexCodeRegisterCalculator(boolean staticMethod, DexTypeList paraTypes) {
        this(staticMethod, paraTypes, null);
    }

    public DexCodeRegisterCalculator(boolean staticMethod, DexTypeList paraTypes,
                                     DexCodeVisitor delegate) {
        super(delegate);
        this.mStaticMethod = staticMethod;
        this.mParaTypes = paraTypes;
        calculatorParaRegCount();
    }

    public DexCodeRegisterCalculator(DexMethodNode methodNode) {
        this(methodNode.isStatic(), methodNode.parameters);
    }

    public DexCodeRegisterCalculator(DexMethodNode methodNode, DexCodeVisitor delegate) {
        this(methodNode.isStatic(), methodNode.parameters, delegate);
    }

    public int getLocalRegCount() {
        return mLocalRegCount;
    }

    public int getParaRegCount() {
        return mParaRegCount;
    }

    public static void autoSetRegisterCountForMethodNode(DexMethodNode methodNode) {
        DexCodeRegisterCalculator calculator = new DexCodeRegisterCalculator(methodNode);
        DexCodeNode codeNode = methodNode.getCode();
        if (codeNode != null) {
            codeNode.accept(calculator);
            codeNode.setRegisters(calculator.getLocalRegCount(), calculator.getParaRegCount());
        }
    }

    private void calculatorParaRegCount() {
        int count = mStaticMethod ? 0 : 1;
        for (DexType type : mParaTypes) {
            switch (type.toShortDescriptor()) {
                case 'J':
                case 'D': {
                    count += 2;
                    break;
                }
                default: {
                    count++;
                    break;
                }
            }
        }
        mParaRegCount = count;
    }

    private void traceRegister(DexRegisterList regs) {
        regs.forEach(r -> {
            switch (r.getRef()) {
                case DexRegister.REG_REF_LOCAL: {
                    if (r.isOneWordWidth()) {
                        if (r.getReg() > mLocalRegCount - 1) {
                            mLocalRegCount = r.getReg() + 1;
                        }
                    } else if (r.isDoubleWordWidth()) {
                        if (r.getReg() + 1 > mLocalRegCount - 1) {
                            mLocalRegCount = r.getReg() + 2;
                        }
                    } else {
                        throw new IllegalArgumentException("unexpected reg width " + r.getWidth());
                    }
                    break;
                }
                case DexRegister.REG_REF_PARAMETER: {
                    if (r.isOneWordWidth()) {
                        if (r.getReg() > mParaRegCount - 1) {
                            throw new IllegalStateException("unexpected reg p" + r.getReg() +
                                    ", but total para reg count is " + mParaRegCount);
                        }
                        break;
                    } else if (r.isDoubleWordWidth()) {
                        if (r.getReg() + 1 > mParaRegCount - 1) {
                            throw new IllegalStateException("unexpected double reg p" + r.getReg() +
                                    ", but total para reg count is " + mParaRegCount);
                        }
                        break;
                    }

                    break;
                }
                case DexRegister.REG_REF_UNSPECIFIED: {
                    throw new IllegalArgumentException("unexpected reg ref " + r.getRef());
                }
            }

        });
    }

    public void fillRegisterCount() {
        super.visitRegisters(getLocalRegCount(), getParaRegCount());
    }

    @Override
    public void visitBegin() {
        super.visitBegin();
    }

    @Override
    public void visitRegisters(int localRegCount, int parameterRegCount) {
        super.visitRegisters(localRegCount, parameterRegCount);
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

    @Override
    public void visitConstInsn(int op, DexRegisterList regs, DexConst dexConst) {
        traceRegister(regs);
        super.visitConstInsn(op, regs, dexConst);
    }

    @Override
    public void visitTargetInsn(int op, DexRegisterList regs, DexLabel label) {
        traceRegister(regs);
        super.visitTargetInsn(op, regs, label);
    }

    @Override
    public void visitSimpleInsn(int op, DexRegisterList regs) {
        traceRegister(regs);
        super.visitSimpleInsn(op, regs);
    }

    @Override
    public void visitSwitch(int op, DexRegisterList regs, int[] keys, DexLabel[] targets) {
        traceRegister(regs);
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
    }
}
