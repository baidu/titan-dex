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

package com.baidu.titan.dex.reader;

import com.baidu.titan.dex.DexAccessFlags;
import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexConstant;
import com.baidu.titan.dex.DexItemFactory;
import com.baidu.titan.dex.DexRegister;
import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.Dop;
import com.baidu.titan.dex.DopFormat;
import com.baidu.titan.dex.DopFormats;
import com.baidu.titan.dex.Dops;
import com.baidu.titan.dex.internal.util.Bits;
import com.baidu.titan.dex.visitor.DexCodeVisitor;
import com.baidu.titan.dex.visitor.DexLabel;
import com.baidu.titan.dexlib.dex.Code;
import com.baidu.titan.dexlib.dex.Dex;
import com.baidu.titan.dexlib.dex.FieldId;
import com.baidu.titan.dexlib.dex.MethodId;
import com.baidu.titan.dexlib.dex.ProtoId;
import com.baidu.titan.dexlib.dex.TypeList;

import java.util.ArrayList;
import java.util.List;

/**
 * DexCodeReader <br>
 * 内部使用，非公开API <br>
 * Dex 指令集参考：http://source.android.com/devices/tech/dalvik/dalvik-bytecode.html <br>
 * http://source.android.com/devices/tech/dalvik/instruction-formats.html
 *
 * @author zhangdi07@baidu.com
 * @since 2017/1/15
 */
class DexCodeReader {

    private DexCodeVisitor mCodeVisitor;

    private Code mCode;

    private Dex mDex;

    /**
     * 字节码预访问集合
     */
    private int[] mWorkSet;
    /**
     * 字节码活跃集合
     */
    private int[] mActiveSet;

    private DexLabel[] mDexLabels;

    private ArrayList<Integer>[] mLineNums;

    private int mRegisterSize;

    private int mParameterRegSize;

    private boolean mUseRegisterRef = true;

    private DexType mOwner;

    private DexTypeList mParameterList;

    private DexAccessFlags mAccess;

    private DexItemFactory mFactory;

    /**
     * special pseudo-opcode value for packed-switch data payload
     * instructions
     */
    public static final int PACKED_SWITCH_PAYLOAD = 0x100;

    /** special pseudo-opcode value for packed-switch data payload
     * instructions
     */
    public static final int SPARSE_SWITCH_PAYLOAD = 0x200;

    /** special pseudo-opcode value for fill-array-data data payload
     * instructions
     */
    public static final int FILL_ARRAY_DATA_PAYLOAD = 0x300;


    public DexCodeReader(Dex dex, DexType owner, DexTypeList parameter, DexAccessFlags accessFlags,
                         Code code, DexCodeVisitor visitor, DexItemFactory factory) {
        this.mDex = dex;
        this.mOwner = owner;
        this.mCode = code;
        this.mCodeVisitor = visitor;
        this.mParameterList = parameter;
        this.mAccess = accessFlags;
        this.mFactory = factory;
    }

    public void readCode() {
        this.mRegisterSize = mCode.getRegistersSize();
        this.mParameterRegSize = mCode.getInsSize();
        mCodeVisitor.visitBegin();
        mCodeVisitor.visitRegisters(this.mRegisterSize - this.mParameterRegSize,
                this.mParameterRegSize);
        short[] insns = mCode.getInstructions();
        mWorkSet = Bits.makeBitSet(insns.length + 1);
        mActiveSet = Bits.makeBitSet(insns.length + 1);
        mDexLabels = new DexLabel[insns.length + 1];
        mLineNums = new ArrayList[insns.length + 1];
        readDebugInfo();
        readAndVisitTryCatches();
        readControlFlow();
        visitCodes();
        mCodeVisitor.visitEnd();
    }

    /**
     * 获取字节码指定偏移位置的Label，如果不存在则创建
     *
     * @param offset
     * @return
     */
    private DexLabel getOrCreateLabel(int offset) {
        DexLabel dexLabel = mDexLabels[offset];
        if (dexLabel == null) {
            dexLabel = new DexLabel();
            mDexLabels[offset] = dexLabel;
        }
        return dexLabel;
    }

    /**
     * 获取字节码指定偏移位置的Label，如果不存在则返回NULL
     *
     * @param offset
     * @return
     */
    private DexLabel getLabelOrNull(int offset) {
        return mDexLabels[offset];
    }

    private void visitCodes() {
        int offset;
        // 指令数组
        short[] insns = mCode.getInstructions();
        DalvikInstBuffer instBuffer = new DalvikInstBuffer(insns);

        if (mLocals.size() > 0) {
            for (LocalEntry localEntry : mLocals) {
                mCodeVisitor.visitLocal(localEntry.reg, localEntry.name, localEntry.type,
                        localEntry.signature, localEntry.start, localEntry.end);
            }
        }

        int nextLabelOffset = 0;
        // 这里只访问Active的指令集合，忽略访问dead code
        for (offset = Bits.findFirst(mActiveSet, 0); offset >= 0;
                    offset = Bits.findFirst(mActiveSet, offset + 1)) {

            int fullOp = insns[offset];
            int op = fullOp & 0xFF;
            Dop dop = Dops.dopFor(op);

            ArrayList<Integer> lines = mLineNums[offset];
            if (lines != null && lines.size() > 0) {
                for (Integer line : lines) {
                    mCodeVisitor.visitLineNumber(line, getLabelOrNull(offset));
                }
            }

            // 处理try catch对应的start、end对应的开始Label位置的代码，如果是dead code的情况:
            while (nextLabelOffset <= offset) {
                DexLabel label = getLabelOrNull(nextLabelOffset);
                nextLabelOffset++;
                if (label != null) {
                    mCodeVisitor.visitLabel(label);
                }
            }

            // 如果访问的是常量指令集：
            boolean handled = visitConstOp(dop, instBuffer, offset);

            if (!handled) {
                // 访问跳转指令
                handled = visitTargetOp(dop, instBuffer, offset);
            }

            if (!handled) {
                handled = visitFillArrayDataOp(dop, instBuffer, offset);
            }

            if (!handled) {
                handled = visitSwitchOp(dop, instBuffer, offset);
            }

            if (!handled) {
                handled = visitForm12xOp(dop, instBuffer, offset);
            }

            if (!handled) {
                handled = visitForm22xOp(dop, instBuffer, offset);
            }

            if (!handled) {
                handled = visitForm32xOp(dop, instBuffer, offset);
            }

            if (!handled) {
                handled = visitForm11xOp(dop, instBuffer, offset);
            }

            if (!handled) {
                handled = visitForm10x(dop, instBuffer, offset);
            }

            if (!handled) {
                handled = visitForm23xOp(dop, instBuffer, offset);
            }

            if (!handled) {
                throw new IllegalStateException("unkown op " + dop);
            }

        }

        // 处理label在非活跃集合中的情况：
        while (nextLabelOffset <= insns.length) {
            DexLabel label = getLabelOrNull(nextLabelOffset);
            nextLabelOffset++;
            if (label != null) {
                mCodeVisitor.visitLabel(label);
            }
        }
    }


    /**
     * 访问常量操作指令
     *
     * @param dop dalvik opcode
     * @param instBuffer 指令helper
     * @param offset 偏移量，指令在数据中的index
     * @return 是否处理的指令
     */
    private boolean visitConstOp(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        DexConst dexConst;
        DexRegisterList regList;

        switch (dop.opcode) {
            // 11n    const/4 vA, #+B    将给定的字面值（符号扩展为 32 位）移到指定的寄存器中
            case Dops.CONST_4: {
                // 指令中高8位中低4位为寄存器编号
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                // 高8位中高4位为字面值
                dexConst = mFactory.dexConsts.createLiteralBits32(instBuffer.sinstB(offset));
                break;
            }
            // 21s    const/16 vAA, #+BBBB    将给定的字面值（符号扩展为 32 位）移到指定的寄存器中
            case Dops.CONST_16: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createLiteralBits32(instBuffer.sshort(offset + 1));
                break;
            }
            // 31i    const vAA, #+BBBBBBBB    将给定的字面值移到指定的寄存器中
            case Dops.CONST: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createLiteralBits32(instBuffer.uint(offset + 1));
                break;
            }
            // 21h    const/high16 vAA, #+BBBB0000    将给定的字面值（右零扩展为 32 位）移到指定的寄存器中
            case Dops.CONST_HIGH16: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createLiteralBits32(
                        instBuffer.sshort(offset + 1) << 16);
                break;
            }
            // 21s    const-wide/16 vAA, #+BBBB    将给定的字面值（符号扩展为 64 位）移到指定的寄存器对中
            case Dops.CONST_WIDE_16: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_DOUBLE_WORD));
                dexConst = mFactory.dexConsts.createLiteralBits64(instBuffer.sshort(offset + 1));
                break;
            }
            // 31i const-wide/32 vAA, #+BBBBBBBB    将给定的字面值（符号扩展为 64 位）移到指定的寄存器对中
            case Dops.CONST_WIDE_32: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_DOUBLE_WORD));
                dexConst = mFactory.dexConsts.createLiteralBits64(instBuffer.sint(offset + 1));
                break;
            }
            // 51l const-wide vAA, #+BBBBBBBBBBBBBBBB    将给定的字面值移到指定的寄存器对中
            case Dops.CONST_WIDE: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_DOUBLE_WORD));
                dexConst = mFactory.dexConsts.createLiteralBits64(instBuffer.ulong(offset + 1));
                break;
            }
            // 21h    const-wide/high16 vAA, #+BBBB000000000000    将给定的字面值（右零扩展为 64 位）移到指定的寄存器对中
            case Dops.CONST_WIDE_HIGH16: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_DOUBLE_WORD));
                dexConst = mFactory.dexConsts.createLiteralBits64(
                        ((long) instBuffer.ushort(offset + 1)) << 48L);
                break;
            }
            // 21c    const-string vAA, string@BBBB    将通过给定的索引获取的字符串引用移到指定的寄存器中
            case Dops.CONST_STRING: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createConstString(
                        mDex.strings().get(instBuffer.ushortWithInt(offset + 1)));
                break;
            }
            // 31c    const-string/jumbo vAA, string@BBBBBBBB    将通过给定的索引获取的字符串引用移到指定的寄存器中
            case Dops.CONST_STRING_JUMBO: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createConstString(
                        mDex.strings().get(instBuffer.uint(offset + 1)));
                break;
            }
            // 21c    const-class vAA, type@BBBB
            // 将通过给定的索引获取的类引用移到指定的寄存器中。如果指定的类型是原始类型，则将存储对原始类型的退化类的引用
            case Dops.CONST_CLASS: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createConstType(mFactory.createType(
                        mDex.typeNames().get(instBuffer.ushortWithInt(offset + 1))));
                break;
            }
            // 21c    check-cast vAA, type@BBBB    如果给定寄存器中的引用不能转型为指定的类型，则抛出 ClassCastException
            case Dops.CHECK_CAST: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createConstType(mFactory.createType(
                        mDex.typeNames().get(instBuffer.ushortWithInt(offset + 1))));
                break;
            }
            // 22c    instance-of vA, vB, type@CCCC
            // 如果指定的引用是给定类型的实例，则为给定目标寄存器赋值 1，否则赋值 0
            // 高八位中低4位为vA， 高4位为vB
            case Dops.INSTANCE_OF: {
                regList = DexRegisterList.make(
                        makeDexRegister(instBuffer.uinstA(offset),
                                DexRegister.REG_WIDTH_ONE_WORD),
                        makeDexRegister(instBuffer.uinstB(offset),
                                DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createConstType(mFactory.createType(
                        mDex.typeNames().get(instBuffer.ushortWithInt(offset + 1))));
                break;
            }
            // 21c new-instance vAA, type@BBBB
            // 根据指定的类型构造新实例，并将对该新实例的引用存储到目标寄存器中。该类型必须引用非数组类。
            case Dops.NEW_INSTANCE: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createConstType(mFactory.createType(
                        mDex.typeNames().get(instBuffer.ushortWithInt(offset + 1))));
                break;
            }
            // 22c    new-array vA, vB, type@CCCC    根据指定的类型和大小构造新数组。该类型必须是数组类型
            // A: 目标寄存器（4 位) B: 大小寄存器  C: 类型索引
            case Dops.NEW_ARRAY: {
                regList = DexRegisterList.make(
                        makeDexRegister(instBuffer.uinstA(offset),
                                DexRegister.REG_WIDTH_ONE_WORD),
                        makeDexRegister(instBuffer.uinstB(offset),
                                DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createConstType(mFactory.createType(
                        mDex.typeNames().get(instBuffer.ushortWithInt(offset + 1))));
                break;
            }
            // 35c filled-new-array {vC, vD, vE, vF, vG}, type@BBBB
            // 根据给定类型和大小构造数组，并使用提供的内容填充该数组。
            // 该类型必须是数组类型。数组的内容必须是单字类型（即不接受 long 或 double 类型的数组，但接受引用类型的数组）。
            // 构造的实例会存储为一个“结果”，方式与方法调用指令存储其结果的方式相同，因此构造的实例必须移到后面紧跟 move-result-object 指令（如果要使用的话）的寄存器
            // A: 数组大小和参数字数（4 位） B: 类型索引（16 位） C..G: 参数寄存器（每个寄存器各占 4 位）
            case Dops.FILLED_NEW_ARRAY: {
                int[] regNumArray = createRegNumListFrom35c(instBuffer, offset);
                regList = new DexRegisterList(regNumArray.length);
                for (int i = 0; i < regNumArray.length; i++) {
                    regList.setReg(i, makeDexRegister(regNumArray[i],
                            DexRegister.REG_WIDTH_ONE_WORD));
                }
                dexConst = mFactory.dexConsts.createConstType(mFactory.createType(
                        mDex.typeNames().get(instBuffer.ushortWithInt(offset + 1))));
                break;
            }
            // 3rc    filled-new-array/range {vCCCC .. vNNNN}, type@BBBB
            // 根据给定类型和大小构造数组，并使用提供的内容填充该数组。相关的说明和限制与上文所述 filled-new-array 的相同。
            case Dops.FILLED_NEW_ARRAY_RANGE: {
                int[] regNumArray = createRegNumListFrom3rc(instBuffer, offset);
                regList = new DexRegisterList(regNumArray.length);
                for (int i = 0; i < regNumArray.length; i++) {
                    regList.setReg(i, makeDexRegister(regNumArray[i],
                            DexRegister.REG_WIDTH_ONE_WORD));
                }
                dexConst = mFactory.dexConsts.createConstType(mFactory.createType(
                        mDex.typeNames().get(instBuffer.ushortWithInt(offset + 1))));
                break;
            }
            // 22c    iinstanceop vA, vB, field@CCCC
            // 对已标识的字段执行已确定的对象实例字段运算，并将结果加载或存储到值寄存器中
            // A: 值寄存器或寄存器对；可以是源寄存器，也可以是目标寄存器（4 位）
            // B: 对象寄存器（4 位）
            // C: 实例字段引用索引（16 位）
            case Dops.IGET:
            case Dops.IGET_WIDE:
            case Dops.IGET_OBJECT:
            case Dops.IGET_BOOLEAN:
            case Dops.IGET_BYTE:
            case Dops.IGET_CHAR:
            case Dops.IGET_SHORT:
            case Dops.IPUT:
            case Dops.IPUT_WIDE:
            case Dops.IPUT_OBJECT:
            case Dops.IPUT_BOOLEAN:
            case Dops.IPUT_BYTE:
            case Dops.IPUT_CHAR:
            case Dops.IPUT_SHORT: {
                regList = DexRegisterList.make(
                        makeDexRegister(
                                instBuffer.uinstA(offset), DexRegister.REG_WIDTH_ONE_WORD),
                        makeDexRegister(
                                instBuffer.uinstB(offset), DexRegister.REG_WIDTH_ONE_WORD));
                FieldId fieldId = mDex.fieldIds().get(
                        instBuffer.ushortWithInt(offset + 1));
                dexConst = mFactory.dexConsts.createConstFieldRef(
                        mFactory.createType(mDex.typeNames().get(fieldId.getDeclaringClassIndex())),
                        mFactory.createType(mDex.typeNames().get(fieldId.getTypeIndex())),
                        mFactory.createString(mDex.strings().get(fieldId.getNameIndex())));
                break;
            }
            // 21c    sstaticop vAA, field@BBBB
            // 对已标识的静态字段执行已确定的对象静态字段运算，并将结果加载或存储到值寄存器中
            // A: 值寄存器或寄存器对；可以是源寄存器，也可以是目标寄存器（8 位）
            // B: 静态字段引用索引（16 位）
            case Dops.SGET:
            case Dops.SGET_WIDE:
            case Dops.SGET_OBJECT:
            case Dops.SGET_BOOLEAN:
            case Dops.SGET_BYTE:
            case Dops.SGET_CHAR:
            case Dops.SGET_SHORT:
            case Dops.SPUT:
            case Dops.SPUT_WIDE:
            case Dops.SPUT_OBJECT:
            case Dops.SPUT_BOOLEAN:
            case Dops.SPUT_BYTE:
            case Dops.SPUT_CHAR:
            case Dops.SPUT_SHORT: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                FieldId fieldId = mDex.fieldIds().get(
                        instBuffer.ushortWithInt(offset + 1));
                dexConst = mFactory.dexConsts.createConstFieldRef(
                        mFactory.createType(mDex.typeNames().get(fieldId.getDeclaringClassIndex())),
                        mFactory.createType(mDex.typeNames().get(fieldId.getTypeIndex())),
                        mFactory.createString(mDex.strings().get(fieldId.getNameIndex())));
                break;
            }
            // 35c    invoke-kind {vC, vD, vE, vF, vG}, meth@BBBB
            // 调用指定的方法。所得结果（如果有的话）可能与紧跟其后的相应 move-result* 变体指令一起存储。
            // A: 参数字数（4 位）
            // B: 方法引用索引（16 位）
            // C..G: 参数寄存器（每个寄存器各占 4 位）
            case Dops.INVOKE_VIRTUAL:
            case Dops.INVOKE_SUPER:
            case Dops.INVOKE_DIRECT:
            case Dops.INVOKE_STATIC:
            case Dops.INVOKE_INTERFACE: {
                int[] regNumArray = createRegNumListFrom35c(instBuffer, offset);
                MethodId methodId = mDex.methodIds().get(
                        instBuffer.ushortWithInt(offset + 1));
                dexConst = createConstMethodRef(methodId);
                regList = createRegListFromInvoke(dop.opcode, regNumArray,
                        (DexConst.ConstMethodRef) dexConst);
                break;
            }

            // 3rc    invoke-kind/range {vCCCC .. vNNNN}, meth@BBBB
            // 调用指定的方法。有关详情、注意事项和建议，请参阅上文第一个 invoke-kind 说明。
            // A: 参数字数（8 位）
            // B: 方法引用索引（16 位）
            // C: 第一个参数寄存器（16 位）
            // N = A + C - 1
            case Dops.INVOKE_VIRTUAL_RANGE:
            case Dops.INVOKE_SUPER_RANGE:
            case Dops.INVOKE_DIRECT_RANGE:
            case Dops.INVOKE_STATIC_RANGE:
            case Dops.INVOKE_INTERFACE_RANGE: {
                int[] regNumArray = createRegNumListFrom3rc(instBuffer, offset);
                MethodId methodId = mDex.methodIds().get(
                        instBuffer.ushortWithInt(offset + 1));
                dexConst = createConstMethodRef(methodId);
                regList = createRegListFromInvoke(dop.opcode, regNumArray,
                        (DexConst.ConstMethodRef) dexConst);
                break;
            }
            // 22s binop/lit16 vA, vB, #+CCCC
            // 对指定的寄存器（第一个参数）和字面值（第二个参数）执行指定的二元运算，并将结果存储到目标寄存器中
            // A: 目标寄存器（4 位）
            // B: 源寄存器（4 位）
            // C: 有符号整数常量（16 位）
            case Dops.ADD_INT_LIT16:
            case Dops.RSUB_INT:
            case Dops.MUL_INT_LIT16:
            case Dops.DIV_INT_LIT16:
            case Dops.REM_INT_LIT16:
            case Dops.AND_INT_LIT16:
            case Dops.OR_INT_LIT16:
            case Dops.XOR_INT_LIT16: {
                regList = DexRegisterList.make(
                        makeDexRegister(
                                instBuffer.uinstA(offset), DexRegister.REG_WIDTH_ONE_WORD),
                        makeDexRegister(
                                instBuffer.uinstB(offset), DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createLiteralBits32(instBuffer.sshort(offset + 1));
                break;
            }
            // 22b binop/lit8 vAA, vBB, #+CC
            // 对指定的寄存器（第一个参数）和字面值（第二个参数）执行指定的二元运算，并将结果存储到目标寄存器中。
            // A: 目标寄存器（8 位）
            // B: 源寄存器（8 位）
            // C: 有符号整数常量（8 位）
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
                regList = DexRegisterList.make(
                        makeDexRegister(
                                instBuffer.uinstAA(offset), DexRegister.REG_WIDTH_ONE_WORD),
                        makeDexRegister(
                                instBuffer.sshort(offset + 1) & 0xFF,
                                DexRegister.REG_WIDTH_ONE_WORD));
                dexConst = mFactory.dexConsts.createLiteralBits32(
                        instBuffer.sshort(offset + 1) >> 8);
                break;
            }

            default: {
                return false;
            }
        }
        mCodeVisitor.visitConstInsn(dop.opcode, regList, dexConst);
        return true;
    }

    /**
     * 访问跳转指令
     *
     * @param dop dalvik op
     * @param instBuffer inst helper
     * @param offset 指令index
     * @return 是否对指令进行了处理
     */
    private boolean visitTargetOp(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        DexLabel branchLabel;
        DexRegisterList regList;
        switch (dop.opcode) {
            // 10t    goto +AA    无条件地跳转到指定的指令
            // A: 有符号分支偏移量（8 位）
            case Dops.GOTO: {
                branchLabel = getLabelOrNull(offset + instBuffer.sinstAA(offset));
                regList = DexRegisterList.EMPTY;
                break;
            }
            // 20t    goto/16 +AAAA    无条件地跳转到指定的指令
            // A: 有符号分支偏移量（16 位）
            case Dops.GOTO_16: {
                regList = DexRegisterList.EMPTY;
                branchLabel = getLabelOrNull(offset + instBuffer.sshort(offset + 1));
                break;
            }
            // 30t goto/32 +AAAAAAAA    无条件地跳转到指定的指令
            // A: 有符号分支偏移量（32 位）
            case Dops.GOTO_32: {
                regList = DexRegisterList.EMPTY;
                branchLabel = getLabelOrNull(offset + instBuffer.sint(offset + 1));
                break;
            }
            // 22t if-test vA, vB, +CCCC    如果两个给定寄存器的值比较结果符合预期，则分支到给定目标偏移量
            // A: 要测试的第一个寄存器（4 位）
            // B: 要测试的第二个寄存器（4 位）
            // C: 有符号分支偏移量（16 位）
            case Dops.IF_EQ:
            case Dops.IF_NE:
            case Dops.IF_LT:
            case Dops.IF_GE:
            case Dops.IF_GT:
            case Dops.IF_LE: {
                regList = DexRegisterList.make(
                        makeDexRegister(
                                instBuffer.uinstA(offset), DexRegister.REG_WIDTH_ONE_WORD),
                        makeDexRegister(
                                instBuffer.uinstB(offset), DexRegister.REG_WIDTH_ONE_WORD));
                branchLabel = getLabelOrNull(offset + instBuffer.sshort(offset + 1));
                break;
            }
            // 21t    if-testz vAA, +BBBB    如果给定寄存器的值与 0 的比较结果符合预期，则分支到给定目标偏移量
            // A: 要测试的寄存器（8 位）
            // B: 有符号分支偏移量（16 位）
            case Dops.IF_EQZ:
            case Dops.IF_NEZ:
            case Dops.IF_LTZ:
            case Dops.IF_GEZ:
            case Dops.IF_GTZ:
            case Dops.IF_LEZ: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                branchLabel = getLabelOrNull(offset + instBuffer.sshort(offset + 1));
                break;
            }
            default: {
                return false;
            }
        }
        if (branchLabel == null) {
            throw new DexReadErrorException("branch not match");
        }
        mCodeVisitor.visitTargetInsn(dop.opcode, regList, branchLabel);
        return true;
    }

    private boolean visitFillArrayDataOp(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        DexRegisterList regList = null;
        switch (dop.opcode) {
            // 31t    fill-array-data vAA, +BBBBBBBB
            // 用指定的数据填充给定数组。必须引用原始类型的数组，且数据表格的类型必须与数组匹配；
            // 此外，数据表格所包含的元素个数不得超出数组中的元素个数。也就是说，数组可能比表格大；
            // 如果是这样，仅设置数组的初始元素，而忽略剩余元素。
            // A: 数组引用（8 位）
            // B: 到表格数据伪指令的有符号“分支”偏移量（32 位）
            case Dops.FILL_ARRAY_DATA: {
                regList = DexRegisterList.make(
                        makeDexRegister(instBuffer.uinstAA(offset),
                                DexRegister.REG_WIDTH_ONE_WORD));
                int arrayDataPayloadOff = offset + instBuffer.sint(offset + 1);
                int elementWidth = instBuffer.ushortWithInt(arrayDataPayloadOff + 1);
                int elementSize = instBuffer.sint(arrayDataPayloadOff + 2);
                byte[] arrayData = new byte[elementWidth * elementSize];
                int dataUnitCount = (elementSize * elementWidth + 1) / 2;
                for (int i = 0; i < dataUnitCount; i++) {
                    short unit = instBuffer.ushort(arrayDataPayloadOff + 4 + i);
                    arrayData[2 * i] = (byte) (unit & 0xFF);
                    if (2 * i + 1 < arrayData.length) {
                        arrayData[2 * i + 1] = (byte) (unit >>> 8 & 0xFF);
                    }
                }
                mCodeVisitor.visitConstInsn(
                        dop.opcode,
                        regList,
                        DexConst.ArrayData.make(elementWidth, elementSize, arrayData));
                break;
            }
            default: {
                return false;
            }

        }
        return true;
    }

    private boolean visitSwitchOp(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        DexRegisterList regList = null;
        int[] caseKeys = null;
        DexLabel[] casesLabel = null;
        switch (dop.opcode) {
            // 31t    packed-switch vAA, +BBBBBBBB
            // 通过使用与特定整数范围内的每个值相对应的偏移量表，基于给定寄存器中的值跳转到新指令；如果没有匹配项，则跳转到下一条指令。
            // A: 要测试的寄存器
            // B: 到表格数据伪指令的有符号“分支”偏移量（32 位）
            case Dops.PACKED_SWITCH: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                int packedSwitchPayloadOff = offset + instBuffer.uint(offset + 1);
                int ident = instBuffer.ushort(packedSwitchPayloadOff);
                int size = instBuffer.sshort(packedSwitchPayloadOff + 1);
                int currentKey = instBuffer.uint(packedSwitchPayloadOff + 2);
                casesLabel = new DexLabel[size];
                caseKeys = new int[size];
                for (int i = 0; i < size; i++) {
                    caseKeys[i] = currentKey++;
                    int target = offset + instBuffer.sint(packedSwitchPayloadOff + 4 + 2 * i);
                    casesLabel[i] = getLabelOrNull(target);
                }
                break;
            }
            // 31t    packed-switch vAA, +BBBBBBBB
            // 通过使用与特定整数范围内的每个值相对应的偏移量表，基于给定寄存器中的值跳转到新指令；如果没有匹配项，则跳转到下一条指令。
            // A: 要测试的寄存器
            // B: 到表格数据伪指令的有符号“分支”偏移量（32 位）
            case Dops.SPARSE_SWITCH: {
                regList = DexRegisterList.make(makeDexRegister(instBuffer.uinstAA(offset),
                        DexRegister.REG_WIDTH_ONE_WORD));
                int sparseSwitchPayloadOff = offset + instBuffer.uint(offset + 1);
                int ident = instBuffer.ushort(sparseSwitchPayloadOff);
                int size = instBuffer.sshort(sparseSwitchPayloadOff + 1);
                int keysOff = sparseSwitchPayloadOff + 2;
                int casesOff = keysOff + size * 2;
                caseKeys = new int[size];
                casesLabel = new DexLabel[size];
                for (int i = 0; i < size; i++) {
                    caseKeys[i] = instBuffer.sint(keysOff + i * 2);
                    casesLabel[i] = getLabelOrNull(
                            offset + instBuffer.sint(casesOff + i * 2));
                }
                break;
            }
            default: {
                return false;
            }
        }
        mCodeVisitor.visitSwitch(dop.opcode, regList, caseKeys, casesLabel);
        return true;
    }

    private boolean visitForm12xOp(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        DexRegisterList regList;
        int destRegWidth = DexRegister.REG_WIDTH_ONE_WORD;
        int srcRegWidth = DexRegister.REG_WIDTH_ONE_WORD;

        switch (dop.opcode) {
            case Dops.MOVE:
            case Dops.MOVE_OBJECT:
            case Dops.ARRAY_LENGTH:
            case Dops.NEG_INT:
            case Dops.NOT_INT:
            case Dops.NEG_FLOAT:
            case Dops.INT_TO_FLOAT:
            case Dops.FLOAT_TO_INT:
            case Dops.INT_TO_BYTE:
            case Dops.INT_TO_CHAR:
            case Dops.INT_TO_SHORT:
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
            case Dops.USHR_INT_2ADDR:
            case Dops.ADD_FLOAT_2ADDR:
            case Dops.SUB_FLOAT_2ADDR:
            case Dops.MUL_FLOAT_2ADDR:
            case Dops.DIV_FLOAT_2ADDR:
            case Dops.REM_FLOAT_2ADDR:

                {
                destRegWidth = DexRegister.REG_WIDTH_ONE_WORD;
                srcRegWidth = DexRegister.REG_WIDTH_ONE_WORD;
                break;
            }
            case Dops.MOVE_WIDE:
            case Dops.NEG_LONG:
            case Dops.NOT_LONG:
            case Dops.NEG_DOUBLE:
            case Dops.LONG_TO_DOUBLE :
            case Dops.DOUBLE_TO_LONG:
            case Dops.ADD_LONG_2ADDR:
            case Dops.SUB_LONG_2ADDR:
            case Dops.MUL_LONG_2ADDR:
            case Dops.DIV_LONG_2ADDR:
            case Dops.REM_LONG_2ADDR:
            case Dops.AND_LONG_2ADDR:
            case Dops.OR_LONG_2ADDR:
            case Dops.XOR_LONG_2ADDR:
            case Dops.ADD_DOUBLE_2ADDR:
            case Dops.SUB_DOUBLE_2ADDR:
            case Dops.MUL_DOUBLE_2ADDR:
            case Dops.DIV_DOUBLE_2ADDR:
            case Dops.REM_DOUBLE_2ADDR: {
                destRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                srcRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                break;
            }
            case Dops.SHL_LONG_2ADDR:
            case Dops.SHR_LONG_2ADDR:
            case Dops.USHR_LONG_2ADDR:{
                destRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                srcRegWidth = DexRegister.REG_WIDTH_ONE_WORD;
                break;
            }
            case Dops.INT_TO_LONG:
            case Dops.INT_TO_DOUBLE:
            case Dops.FLOAT_TO_LONG:
            case Dops.FLOAT_TO_DOUBLE: {
                destRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                break;
            }
            case Dops.LONG_TO_INT:
            case Dops.LONG_TO_FLOAT:
            case Dops.DOUBLE_TO_INT:
            case Dops.DOUBLE_TO_FLOAT: {
                srcRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                break;
            }
            default: {
                return false;
            }
        }

        // 12x op vA, vB
        // 对两个寄存器进行操作，根据opcode指定寄存器宽度
        // A: 目标寄存器（4 位）
        // B: 源寄存器（4 位）
        regList = DexRegisterList.make(
                makeDexRegister(instBuffer.uinstA(offset), destRegWidth),
                makeDexRegister(instBuffer.uinstB(offset), srcRegWidth));

        mCodeVisitor.visitSimpleInsn(dop.opcode, regList);
        return true;
    }

    private boolean visitForm22xOp(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        int destRegWidth;
        int srcRegWidth;

        DexRegisterList regList;
        switch (dop.opcode) {
            case Dops.MOVE_FROM16:
            case Dops.MOVE_OBJECT_FROM16: {
                destRegWidth = DexRegister.REG_WIDTH_ONE_WORD;
                srcRegWidth = DexRegister.REG_WIDTH_ONE_WORD;
                break;
            }
            case Dops.MOVE_WIDE_FROM16: {
                destRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                srcRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                break;
            }
            default: {
                return false;
            }
        }

        // 22x    op vAA, vBBBB  对两个寄存器操作
        // A: 目标寄存器（8 位）
        // B: 源寄存器（16 位）
        regList = DexRegisterList.make(
                makeDexRegister(instBuffer.uinstAA(offset), destRegWidth),
                makeDexRegister(instBuffer.ushortWithInt(offset + 1), srcRegWidth));
        mCodeVisitor.visitSimpleInsn(dop.opcode, regList);
        return true;
    }

    private boolean visitForm32xOp(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        int destRegWidth;
        int srcRegWidth;
        DexRegisterList regList;
        switch (dop.opcode) {
            case Dops.MOVE_16:
            case Dops.MOVE_OBJECT_16: {
                destRegWidth = DexRegister.REG_WIDTH_ONE_WORD;
                srcRegWidth = DexRegister.REG_WIDTH_ONE_WORD;
                break;
            }
            case Dops.MOVE_WIDE_16: {
                destRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                srcRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                break;
            }
            default: {
                return false;
            }
        }
        // 32x    op vAAAA, vBBBB    两个寄存器间操作
        // A: 目标寄存器（16 位）
        // B: 源寄存器（16 位）
        regList = DexRegisterList.make(
                makeDexRegister(
                        instBuffer.ushortWithInt(offset + 1), destRegWidth),
                makeDexRegister(instBuffer.ushortWithInt(offset + 2), srcRegWidth));
        mCodeVisitor.visitSimpleInsn(dop.opcode, regList);
        return true;
    }

    private boolean visitForm11xOp(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        int destRegWidth;
        DexRegisterList regList;
        switch (dop.opcode) {
            case Dops.MOVE_RESULT:
            case Dops.MOVE_RESULT_OBJECT:
            case Dops.MOVE_EXCEPTION:
            case Dops.RETURN:
            case Dops.RETURN_OBJECT:
            case Dops.MONITOR_ENTER:
            case Dops.MONITOR_EXIT:
            case Dops.THROW: {
                destRegWidth = DexRegister.REG_WIDTH_ONE_WORD;
                break;
            }
            case Dops.MOVE_RESULT_WIDE:
            case Dops.RETURN_WIDE: {
                destRegWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                break;
            }
            default: {
                return false;
            }
        }
        // 11x    op vAA    对单寄存器操作
        // A: 目标寄存器对（8 位）
        regList = DexRegisterList.make(
                makeDexRegister(instBuffer.uinstAA(offset), destRegWidth));

        mCodeVisitor.visitSimpleInsn(dop.opcode, regList);
        return true;
    }

    private boolean visitForm10x(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        switch (dop.opcode) {
            case Dops.NOP:
            case Dops.RETURN_VOID: {
                break;
            }
            default: {
                return false;
            }
        }
        // 10x 无寄存器操作指令
        mCodeVisitor.visitSimpleInsn(dop.opcode, DexRegisterList.empty());
        return true;
    }

    private boolean visitForm23xOp(Dop dop, DalvikInstBuffer instBuffer, int offset) {
        int regAWidth;
        int regBWidth;
        int regCWidth;

        DexRegisterList regList;
        switch (dop.opcode) {
            case Dops.CMPL_DOUBLE:
            case Dops.CMPG_DOUBLE:
            case Dops.CMP_LONG: {
                regAWidth = DexRegister.REG_WIDTH_ONE_WORD;
                regBWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                regCWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                break;
            }
            case Dops.AGET_WIDE:
            case Dops.APUT_WIDE: {
                regAWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                regBWidth = DexRegister.REG_WIDTH_ONE_WORD;
                regCWidth = DexRegister.REG_WIDTH_ONE_WORD;
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
            case Dops.ADD_DOUBLE:
            case Dops.SUB_DOUBLE:
            case Dops.MUL_DOUBLE:
            case Dops.DIV_DOUBLE:
            case Dops.REM_DOUBLE: {
                regAWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                regBWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                regCWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                break;
            }
            case Dops.SHL_LONG:
            case Dops.SHR_LONG:
            case Dops.USHR_LONG: {
                regAWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                regBWidth = DexRegister.REG_WIDTH_DOUBLE_WORD;
                regCWidth = DexRegister.REG_WIDTH_ONE_WORD;
                break;
            }
            /** cmpkind */
            case Dops.CMPL_FLOAT:
            case Dops.CMPG_FLOAT:
            /** arrayop */
            case Dops.AGET:
            case Dops.AGET_OBJECT:
            case Dops.AGET_BOOLEAN:
            case Dops.AGET_BYTE:
            case Dops.AGET_CHAR:
            case Dops.AGET_SHORT:
            case Dops.APUT:
            case Dops.APUT_OBJECT:
            case Dops.APUT_BOOLEAN:
            case Dops.APUT_BYTE:
            case Dops.APUT_CHAR:
            case Dops.APUT_SHORT:
                /** binop */
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
            case Dops.USHR_INT:
            case Dops.ADD_FLOAT:
            case Dops.SUB_FLOAT:
            case Dops.MUL_FLOAT:
            case Dops.DIV_FLOAT:
            case Dops.REM_FLOAT: {
                regAWidth = DexRegister.REG_WIDTH_ONE_WORD;
                regBWidth = DexRegister.REG_WIDTH_ONE_WORD;
                regCWidth = DexRegister.REG_WIDTH_ONE_WORD;
                break;
            }
            default: {
                return false;
            }
        }
        // 23x    op vAA, vBB, vCC    三寄存器操作
        // 每个寄存器8位， A为第一个short高8位， B为第二个short低8位, C为第三个short高8位
        regList = DexRegisterList.make(
                makeDexRegister(instBuffer.uinstAA(offset), regAWidth),
                makeDexRegister(instBuffer.ushort(offset + 1) & 0xFF, regBWidth),
                makeDexRegister((instBuffer.ushort(offset + 1) & 0xFFFF) >> 8,
                        regCWidth));
        mCodeVisitor.visitSimpleInsn(dop.opcode, regList);
        return true;
    }

    private DexConst.ConstMethodRef createConstMethodRef(MethodId methodId) {
        String owner = mDex.typeNames().get(methodId.getDeclaringClassIndex());
        String name = mDex.strings().get(methodId.getNameIndex());
        ProtoId protoId = mDex.protoIds().get(methodId.getProtoIndex());
        String returnType = mDex.typeNames().get(protoId.getReturnTypeIndex());
        TypeList typeList = mDex.readTypeList(protoId.getParametersOffset());
        short[] typesIdx = typeList.getTypes();
        String[] parameterTypes = new String[typesIdx.length];
        for (int j = 0; j < typesIdx.length; j++) {
            parameterTypes[j] = mDex.typeNames().get(typesIdx[j]);
        }
        return mFactory.dexConsts.createConstMethodRef(
                mFactory.createType(owner),
                mFactory.createString(name),
                mFactory.createType(returnType),
                mFactory.createTypes(parameterTypes));
    }

    /**
     * 为35c类型指令创建寄存器数组
     *
     * 第一个short高8位中低4位为第5个寄存器值，高4位为寄存器数组大小
     * 第二个Short这里用不到
     * 第三个short是前4个寄存器值，每一个寄存器占4位，从低到高获取。
     *
     * @param instBuffer inst helper
     * @param offset 指令index
     * @return 寄存器数组
     */
    private int[] createRegNumListFrom35c(DalvikInstBuffer instBuffer, int offset) {
        int arg5 = instBuffer.uinstA(offset);
        int arraySize = instBuffer.uinstB(offset);
        short args = instBuffer.ushort(offset + 2);

        int[] regNumArray = new int[arraySize];

        if (arraySize == 5) {
            regNumArray[4] = arg5;
            arraySize--;
        }
        for (int i = 0; i < arraySize; i++) {
            regNumArray[i] = args & 0x0F;
            args >>= 4;
        }
        return regNumArray;
    }

    /**
     * 为3rc类型指令创建寄存器数组
     *
     * 第一个short高8位为数组大小
     * 第二个Short这里用不到
     * 第三个short是第一个寄存器值，作为数组第一个元素，数组后面元素从第一个值递增。
     *
     * @param instBuffer inst helper
     * @param offset 指令index
     * @return 寄存器数组
     */
    private int[] createRegNumListFrom3rc(DalvikInstBuffer instBuffer, int offset) {
        int arraySize = instBuffer.uinstAA(offset);
        int firstReg = instBuffer.ushortWithInt(offset + 2);
        int[] regNumArray = new int[arraySize];

        for (int i = 0; i < arraySize; i++) {
            regNumArray[i] = firstReg + i;
        }
        return regNumArray;
    }

    /**
     * 为invoke-kind指令创建reg list
     *
     * @param op 指令码
     * @param regNums 寄存器数组
     * @param methodRef 被调用的方法
     * @return 寄存器list
     */
    private DexRegisterList createRegListFromInvoke(int op, int[] regNums,
                                                    DexConst.ConstMethodRef methodRef) {
        boolean staticMethod = (op == Dops.INVOKE_STATIC || op == Dops.INVOKE_STATIC_RANGE);
        DexRegisterList regList = new DexRegisterList(
                (staticMethod ? 0 : 1) + methodRef.getParameterTypes().count());

        int nextRegListIdx = 0;
        int nextRegNumIdx = 0;

        if (!staticMethod) {
            regList.setReg(nextRegListIdx++, makeDexRegister(regNums[nextRegNumIdx++],
                    DexRegister.REG_WIDTH_ONE_WORD));
        }

        for (DexType type : methodRef.getParameterTypes().types()) {
            switch (type.toTypeDescriptor().charAt(0)) {
                case DexItemFactory.LongClass.SHORT_DESCRIPTOR:
                case DexItemFactory.DoubleClass.SHORT_DESCRIPTOR: {
                    regList.setReg(nextRegListIdx++, makeDexRegister(regNums[nextRegNumIdx],
                            DexRegister.REG_WIDTH_DOUBLE_WORD));
                    nextRegNumIdx += 2;
                    break;
                }
                default: {
                    regList.setReg(nextRegListIdx++, makeDexRegister(regNums[nextRegNumIdx],
                            DexRegister.REG_WIDTH_ONE_WORD));
                    nextRegNumIdx++;
                    break;
                }
            }
        }
        return regList;
    }

    /**
     * 读取和访问所有try catch item
     */
    private void readAndVisitTryCatches() {
        Code.Try[] tries = mCode.getTries();
        if (tries != null && tries.length > 0) {
            for (int i = 0; i < tries.length; i++) {
                Code.Try tryItem = tries[i];
                int start = tryItem.getStartAddress();
                DexLabel startLabel = getOrCreateLabel(start);
                int end = start + tryItem.getInstructionCount();
                DexLabel endLabel = getOrCreateLabel(end);
                Code.CatchHandler catchHandler =
                        mCode.getCatchHandlers()[tryItem.getCatchHandlerIndex()];
                int handlerCount = catchHandler.getAddresses().length;
//                if (catchHandler.getCatchAllAddress() >= 0) {
//                    handlerCount++;
//                }
                DexLabel[] catchLabels = new DexLabel[handlerCount];
                DexType[] types = new DexType[handlerCount];
                for (int j = 0; j < catchHandler.getAddresses().length; j++) {
                    catchLabels[j] = getOrCreateLabel(catchHandler.getAddresses()[j]);
                    types[j] = mFactory.createType(
                            mDex.typeNames().get(catchHandler.getTypeIndexes()[j]));
                }
                DexLabel catchAllLabel = null;
                if (catchHandler.getCatchAllAddress() >= 0) {
                    catchAllLabel = getOrCreateLabel(catchHandler.getCatchAllAddress());
                }
                mCodeVisitor.visitTryCatch(
                        startLabel, endLabel, new DexTypeList(types), catchLabels, catchAllLabel);
            }
        }
    }

    /**
     * 加入预处理集合
     *
     * @param offset
     */
    private void addWorkSetIfNotActive(int offset) {
        if (!Bits.get(mActiveSet, offset)) {
            Bits.set(mWorkSet, offset);
        }
    }

    /**
     * 读取代码逻辑控制流程，跳转逻辑等
     */
    private void readControlFlow() {
        // 获取所有指令
        short[] insns = mCode.getInstructions();
        // dalvik字节码读取Helper
        DalvikInstBuffer instBuffer = new DalvikInstBuffer(insns);
        // 将第一位置为1，以开始循环
        Bits.set(mWorkSet, 0);
        while (!Bits.isEmpty(mWorkSet)) {
            while (true) {
                int offset = Bits.findFirst(mWorkSet, 0);
                if (offset < 0) {
                    break;
                }
                // 找到预访问的指令，标记为active
                Bits.set(mActiveSet, offset);
                Bits.clear(mWorkSet, offset);

                // 取出offset对应完整指令
                int fullOp = insns[offset];
                // 获得操作码
                int op = fullOp & 0xFF;
                // 转为dalvik opcode
                Dop dop = Dops.dopFor(op);
                // 指令格式 参考http://source.android.com/devices/tech/dalvik/instruction-formats.html
                DopFormat dopFormat = DopFormats.formatFor(dop.getFormat());
                int formatId = dop.getFormat();
                // 分支语句
                if (dop.canBranch()) {
                    int target;
                    switch (dop.getFormat()) {
                        case DopFormats.FORMAT_10T: {
                            // AA|op   op +AAAA   goto
                            target = offset + instBuffer.sinstAA(offset);
                            break;
                        }
                        // ØØ|op AAAA
                        // 20t op +AAAA    goto/16
                        case DopFormats.FORMAT_20T:
                            // AA|op BBBB     21t op vAA, +BBBB
                        case DopFormats.FORMAT_21T:
                            // B|A|op CCCC    22t op vA, vB, +CCCC
                        case DopFormats.FORMAT_22T: {
                            target = offset + instBuffer.sshort(offset + 1);
                            break;
                        }
                        // ØØ|op AAAAlo AAAAh
                            // 30t    op +AAAAAAAA    goto/32
                        case DopFormats.FORMAT_30T:
                            // AA|op BBBBlo BBBBhi
                            // 31t    op vAA, +BBBBBBBB
                        case DopFormats.FORMAT_31T: {
                            target = offset + instBuffer.sint(offset + 1);
                            break;
                        }
                        default: {
                            throw new DexReadErrorException("unkown format");
                        }
                    }
                    // 将目标指令加入预处理指令集合
                    addWorkSetIfNotActive(target);
                    // 在target处创建label
                    getOrCreateLabel(target);
                }

                // switch指令
                if (dop.canSwitch()) {
                    // default 见Dops类中，只有PACKED_SWITCH与SPARSE_SWITCH，指令都是31t,codesize = 3
                    // AA|op BBBBlo BBBBhi
                    // 31t    op vAA, +BBBBBBBB
                    getOrCreateLabel(offset + dopFormat.codeSize());
                    // offset + BBBBBBBB
                    int switchDataOff = offset + instBuffer.sint(offset + 1);
                    // 区分是sparse_switch还是packed_switch
                    short ident = instBuffer.ushort(switchDataOff);
                    int switchSize = instBuffer.sshort(switchDataOff + 1);
                    if (ident == PACKED_SWITCH_PAYLOAD) {
                        for (int i = 0; i < switchSize; i++) {
                            int target = offset + instBuffer.sint(switchDataOff + 4 + 2 * i);
                            getOrCreateLabel(target);
                            addWorkSetIfNotActive(target);
                        }
                    } else if (ident == SPARSE_SWITCH_PAYLOAD) {
                        for (int i = 0; i < switchSize; i++) {
                            int target = offset +
                                    instBuffer.sint(switchDataOff + 2 + 2 * switchSize + 2 * i);
                            getOrCreateLabel(target);
                            addWorkSetIfNotActive(target);
                        }
                    }
                }

                // 大部分操作码通过这个判断，进入下一个循环
                if (dop.canContinue()) {
                    addWorkSetIfNotActive(offset + dopFormat.codeSize());
                }
            }

            Code.Try[] tries = mCode.getTries();
            if (tries != null && tries.length > 0) {
                for (Code.Try tryItem : tries) {
                    int tryStart = tryItem.getStartAddress();
                    int tryEnd = tryStart + tryItem.getInstructionCount();
                    // 如果start到end区间是活跃代码，则需要将handler加入预处理集合
                    if (Bits.anyInRange(mActiveSet, tryStart, tryEnd)) {
                        Code.CatchHandler handler =
                                mCode.getCatchHandlers()[tryItem.getCatchHandlerIndex()];
                        ArrayList<Integer> handleAddrs = new ArrayList<>();
                        for (Integer addr : handler.getAddresses()) {
                            handleAddrs.add(addr);
                        }
                        if (handler.getCatchAllAddress() >= 0) {
                            handleAddrs.add(handler.getCatchAllAddress());
                        }

                        for (Integer addr : handleAddrs) {
                            addWorkSetIfNotActive(addr);
                            getOrCreateLabel(addr);
                        }
                    }
                }
            }
        }
    }


    static class LocalEntry {
        public LocalEntry(int reg, DexString name, DexType type, DexString signature) {
            this.reg = reg;
            this.type = type;
            this.name = name;
            this.signature = signature;
        }

        int reg;
        DexType type;
        DexString name;
        DexString signature;
        DexLabel start;
        DexLabel end;

        public static final int STATUS_NONE = 0;
        public static final int STATUS_START = 1;
        public static final int STATUS_END = 2;
        public int status = STATUS_NONE;

    }

    private List<LocalEntry> mLocals = new ArrayList<>();

    private void readDebugInfo() {
        int debugOffset = mCode.getDebugInfoOffset();
        if (debugOffset <= 0) {
            return;
        }

        LocalEntry[] lastLocalEntry = new LocalEntry[mRegisterSize];
        // insSize是参数个数，参数寄存器使用分配的寄存器最后几个
        int firstReg = mCode.getRegistersSize() - mCode.getInsSize();
        int paraReg = firstReg;
        // 如果不是静态方法，第一个参数是this
        if (mAccess.containsNoneOf(DexAccessFlags.ACC_STATIC)) {
            lastLocalEntry[paraReg] = new LocalEntry(
                    paraReg,
                    mFactory.createString("this"),
                    mOwner,
                    null);
            lastLocalEntry[paraReg].start = getOrCreateLabel(0);
            paraReg++;
        }

        Dex.Section debugInfoSection = mDex.open(debugOffset);
        // 代码段开始行号
        int lineStart = debugInfoSection.readUleb128();
        // 参数数量
        int parametersSize = debugInfoSection.readUleb128();

//        String[] parameterTypes = mDexMethodInfo.getParametersType();
        // 生成localentry
        if (parametersSize > 0) {
            DexString[] parameterNames = new DexString[parametersSize];
            for (int i = 0; i < parametersSize; i++) {
                String paraType = mParameterList.types()[i].toTypeDescriptor();
                int pNameIdx = debugInfoSection.readUleb128p1();
                if (pNameIdx != -1) {
                    parameterNames[i] = mFactory.createString(mDex.strings().get(pNameIdx));
                }
                lastLocalEntry[paraReg] = new LocalEntry(
                        paraReg, parameterNames[i], mFactory.createType(paraType), null);
                lastLocalEntry[paraReg].start = getOrCreateLabel(0);
                switch (paraType.charAt(0)) {
                    case 'J':
                    case 'D': {
                        paraReg += 2;
                        break;
                    }
                    default: {
                        paraReg += 1;
                        break;
                    }
                }
            }
            // 把parametername保存到dexcodenode中
            mCodeVisitor.visitParameters(parameterNames);
        }

        int codeAddress = 0;
        int lineNum = lineStart;
        boolean end = false;

        while (!end) {
            int debugOpCode = debugInfoSection.readByte() & 0xFF;
            switch (debugOpCode) {
                case DexConstant.DebugOpcodes.DBG_END_SEQUENCE: {
                    end = true;
                    break;
                }
                case DexConstant.DebugOpcodes.DBG_ADVANCE_PC: {
                    codeAddress += debugInfoSection.readUleb128();
                    break;
                }
                case DexConstant.DebugOpcodes.DBG_ADVANCE_LINE: {
                    int advanceLine = debugInfoSection.readSleb128();
                    lineNum += advanceLine;
                    break;
                }
                case DexConstant.DebugOpcodes.DBG_START_LOCAL: {
                    int regNum = debugInfoSection.readUleb128();
                    int nameIdx = debugInfoSection.readUleb128p1();
                    int typeIdx = debugInfoSection.readUleb128p1();
                    lastLocalEntry[regNum] = new LocalEntry(
                            regNum,
                            mFactory.createString(mDex.strings().get(nameIdx)),
                            mFactory.createType(mDex.typeNames().get(typeIdx)),
                            null);
                    lastLocalEntry[regNum].start = getOrCreateLabel(codeAddress);
                    lastLocalEntry[regNum].status = LocalEntry.STATUS_START;
                    break;
                }
                case DexConstant.DebugOpcodes.DBG_START_LOCAL_EXTENDED: {
                    int regNum = debugInfoSection.readUleb128();
                    int nameIdx = debugInfoSection.readUleb128p1();
                    int typeIdx = debugInfoSection.readUleb128p1();
                    int sigIdx = debugInfoSection.readUleb128p1();
                    lastLocalEntry[regNum] = new LocalEntry(regNum,
                            mFactory.createString(mDex.strings().get(nameIdx)),
                            mFactory.createType(mDex.typeNames().get(typeIdx)),
                            mFactory.createString(mDex.strings().get(sigIdx)));
                    lastLocalEntry[regNum].start = getOrCreateLabel(codeAddress);
                    lastLocalEntry[regNum].status = LocalEntry.STATUS_START;
                    break;
                }
                case DexConstant.DebugOpcodes.DBG_END_LOCAL: {
                    int regNum = debugInfoSection.readUleb128();
                    LocalEntry endLocalEntry = lastLocalEntry[regNum];
                    if (endLocalEntry == null) {
                        throw new DexReadErrorException();
                    }
                    endLocalEntry.end = getOrCreateLabel(codeAddress);
                    lastLocalEntry[regNum].status = LocalEntry.STATUS_END;
                    mLocals.add(endLocalEntry);
                    break;
                }
                case DexConstant.DebugOpcodes.DBG_RESTART_LOCAL: {
                    int regNum = debugInfoSection.readUleb128();
                    LocalEntry lastLocal = lastLocalEntry[regNum];
                    lastLocalEntry[regNum] = new LocalEntry(regNum, lastLocal.name,
                            lastLocal.type, lastLocal.signature);
                    lastLocalEntry[regNum].start = getOrCreateLabel(codeAddress);
                    lastLocalEntry[regNum].status = LocalEntry.STATUS_START;
                    break;
                }
                case DexConstant.DebugOpcodes.DBG_SET_PROLOGUE_END: {

                    break;
                }
                case DexConstant.DebugOpcodes.DBG_SET_EPILOGUE_BEGIN: {
                    break;
                }
                case DexConstant.DebugOpcodes.DBG_SET_FILE: {
                    int fileNameIdx = debugInfoSection.readUleb128p1();

                    break;
                }

                default: {
                    int adjustedOpcode = debugOpCode - DexConstant.DebugOpcodes.DBG_FIRST_SPECIAL;
                    int deltaAddress = adjustedOpcode / DexConstant.DebugOpcodes.DBG_LINE_RANGE;
                    codeAddress = codeAddress + deltaAddress;
                    int deltaLine = DexConstant.DebugOpcodes.DBG_LINE_BASE +
                            (adjustedOpcode % DexConstant.DebugOpcodes.DBG_LINE_RANGE);
                    lineNum = lineNum + deltaLine;
                    getOrCreateLabel(codeAddress);
                    ArrayList<Integer> lines = mLineNums[codeAddress];
                    if (lines == null) {
                        lines = new ArrayList<>();
                        mLineNums[codeAddress] = lines;
                    }
                    lines.add(lineNum);
                }
            }
        }

        for (int i = 0; i < firstReg; i++) {
            LocalEntry local = lastLocalEntry[i];
            if (local != null) {
                if (local.status == LocalEntry.STATUS_START) {
                    local.end = getOrCreateLabel(mCode.getInstructions().length);
                    local.status = LocalEntry.STATUS_END;
                    mLocals.add(local);
                }
            }
        }
    }

    private DexRegister makeDexRegister(int reg, int width) {
        if (reg < 0 || reg >= this.mRegisterSize) {
            throw new IllegalArgumentException("bad reg number : " + reg);
        }
        if (mUseRegisterRef) {
            int firstParaReg = this.mRegisterSize - this.mParameterRegSize;
            if (reg < firstParaReg) {
                return DexRegister.make(reg, width, DexRegister.REG_REF_LOCAL);
            } else {
                return DexRegister.make(reg - firstParaReg, width, DexRegister.REG_REF_PARAMETER);
            }
        } else {
            return DexRegister.make(reg, width, DexRegister.REG_REF_UNSPECIFIED);
        }
    }

    /**
     * dalvik字节码读取Helper
     */
    static class DalvikInstBuffer {

        private short[] mInsts;

        DalvikInstBuffer(short[] insts) {
            this.mInsts = insts;
        }

        /**
         * 取指令高8位无符号值
         *
         * @param offset 指令offset
         * @return 指令高8位无符号值
         */
        public int uinstAA(int offset) {
            return (mInsts[offset] & 0xFFFF) >> 8;
        }

        /**
         * 取指令高8位有符号值
         *
         * @param offset 指令offset
         * @return 指令高8位有符号值
         */
        public int sinstAA(int offset) {
            return mInsts[offset] >> 8;
        }

        /**
         * 取指令高8位中低4位无符号值
         *
         * @param offset 指令offset
         * @return 指令高8位中低4位无符号值
         */
        public int uinstA(int offset) {
            return (mInsts[offset] >> 8) & 0x0F;
        }

        // TODO
        public int sinstA(int offset) {
            return (mInsts[offset] >> 8) & 0x0F;
        }

        /**
         * 取指令高8位中高4位无符号值
         *
         * @param offset 指令offset
         * @return 指令高8位中高4位无符号值
         */
        public int uinstB(int offset) {
            return ((mInsts[offset]) >> 12) & 0x0F;
        }

        /**
         * 取指令高8位中高4位有符号值
         *
         * @param offset 指令offset
         * @return 指令高8位中高4位有符号值
         */
        public int sinstB(int offset) {
            return mInsts[offset] >> 12;
        }

        /**
         * 取指令有符号short值
         *
         * @param offset 指令offset
         * @return 指令有符号short值
         */
        public short sshort(int offset) {
            return mInsts[offset];
        }

        /**
         * 取指令无符号short值
         *
         * @param offset 指令offset
         * @return 指令无符号short值
         */
        public short ushort(int offset) {
            return sshort(offset);
        }

        /**
         * 取指令无符号short值并转成int
         *
         * @param offset 指令offset
         * @return 指令无符号short值并转成int
         */
        public int ushortWithInt(int offset) {
            return ushort(offset) & 0xFFFF;
        }

        /**
         * 取指令有符号int值，取数组中两个元素拼接
         *
         * @param offset 指令offset
         * @return 指令有符号int值
         */
        public int sint(int offset) {
            return (mInsts[offset] & 0xFFFF)
                    | ((mInsts[offset + 1] & 0xFFFF) << 16);
        }

        /**
         * 取指令无符号int值，取数组中两个元素拼接
         *
         * @param offset 指令offset
         * @return 指令无符号int值
         */
        public int uint(int offset) {
            return sint(offset);
        }

        /**
         * 取指令无符号long值，取数组中四个元素拼接
         *
         * @param offset 指令offset
         * @return 指令无符号long值
         */
        public long ulong(int offset) {
            return (mInsts[offset] & 0xFFFFL)
                    | ((mInsts[offset + 1] & 0xFFFFL) << 16)
                    | ((mInsts[offset + 2] & 0xFFFFL) << 32)
                    | ((mInsts[offset + 3] & 0xFFFFL) << 48);
        }
    }

}