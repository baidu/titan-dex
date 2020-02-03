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

import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexRegister;
import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dex.analyze.types.RegType;
import com.baidu.titan.dex.analyze.types.RegTypeCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * 存储所有寄存器的类型信息
 *
 * @author zhangdi07@baidu.com
 * @since 2017/12/14
 */
public class RegisterPc {

    public static final int TYPE_CATEGORY_UNKOWN = 0;
    /** boolean, byte, char, short, int, float */
    public static final int TYPE_CATEGORY_1NR = 1;
    /** long, double */
    public static final int TYPE_CATEGORY_2 = 2;
    /** object reference */
    public static final int TYPE_CATEGORY_REF = 3;
    /** 局部变量寄存器 */
    public final int[] mLocalRegs;
    /** 寄存器类型历史，TODO 后继使用基本类型容器替代Set */
    private Set<Integer>[] mHistoryLocalRegs;
    /** 参数变量寄存器 */
    public final int[] mParaRegs;
    /** 寄存器类型历史，TODO 后继使用基本类型容器替代Set */
    private Set<Integer>[] mHistoryParaRegs;
    /** 结果寄存器 */
    private final int[] mResultRegs;
    /** 适用于实例方法，标识当前方法的this参数是否初始化 */
    private boolean mThisInitialized = false;
    /** 关联的RegType Cache */
    private RegTypeCache mRegTypeCache;

    private MethodAnalyzer mCodeAnalyzer;

    private boolean mStoreHistoryRegType = true;

    public RegisterPc(MethodAnalyzer analyzer, int localRegCount, int paraRegCount, RegTypeCache
            regTypeCache) {
        this.mCodeAnalyzer = analyzer;
        this.mRegTypeCache = regTypeCache;
        this.mLocalRegs = new int[localRegCount];
        if (mStoreHistoryRegType) {
            this.mHistoryLocalRegs = new Set[localRegCount];
        }
        for (int i = 0; i < localRegCount; i++) {
            this.mLocalRegs[i] = regTypeCache.undefinedType().getId();
            if (mStoreHistoryRegType) {
                this.mHistoryLocalRegs[i] = new HashSet<>();
            }
        }

        this.mParaRegs = new int[paraRegCount];
        if (mStoreHistoryRegType) {
            this.mHistoryParaRegs = new Set[paraRegCount];
        }
        for (int i = 0; i < paraRegCount; i++) {
            this.mParaRegs[i] = regTypeCache.undefinedType().getId();
            if (mStoreHistoryRegType) {
                this.mHistoryParaRegs[i] = new HashSet<>();
            }
        }

        this.mResultRegs = new int[2];
        setResultTypeToUnknown();
    }

    public boolean setLocalRegType(int reg, RegType newType) {
        this.mLocalRegs[reg] = newType.getId();
        if (mStoreHistoryRegType) {
            this.mHistoryLocalRegs[reg].clear();
            this.mHistoryLocalRegs[reg].add(newType.getId());
        }

        return true;
    }

    public boolean setParameterRegType(int reg, RegType newType) {
        checkRegRange(reg, this.mParaRegs.length);
        this.mParaRegs[reg] = newType.getId();
        if (mStoreHistoryRegType) {
            this.mHistoryParaRegs[reg].clear();
            this.mHistoryParaRegs[reg].add(newType.getId());
        }

        return true;
    }

    public boolean setLocalRegTypeWide(int reg, RegType newType1, RegType newType2) {
        this.mLocalRegs[reg] = newType1.getId();
        if (mStoreHistoryRegType) {
            this.mHistoryLocalRegs[reg].clear();
            this.mHistoryLocalRegs[reg].add(newType1.getId());
        }

        if (reg + 1 < this.mLocalRegs.length) {
            this.mLocalRegs[reg + 1] = newType2.getId();
            if (mStoreHistoryRegType) {
                this.mHistoryLocalRegs[reg + 1].clear();
                this.mHistoryLocalRegs[reg + 1].add(newType2.getId());
            }
        } else {
            this.mParaRegs[0] = newType2.getId();
            if (mStoreHistoryRegType) {
                this.mHistoryParaRegs[0].clear();
                this.mHistoryParaRegs[0].add(newType2.getId());
            }
        }
        return true;
    }

    public boolean setParameterRegTypeWide(int reg, RegType newType1, RegType newType2) {
        this.mParaRegs[reg] = newType1.getId();
        if (mStoreHistoryRegType) {
            this.mHistoryParaRegs[reg].clear();
            this.mHistoryParaRegs[reg].add(newType1.getId());
        }
        this.mParaRegs[reg + 1] = newType2.getId();
        if (mStoreHistoryRegType) {
            this.mHistoryParaRegs[reg + 1].clear();
            this.mHistoryParaRegs[reg + 1].add(newType2.getId());
        }
        return true;
    }


    public void setThisInitialized() {
        mThisInitialized = true;
    }

    public void copyFromLine(RegisterPc src) {
        System.arraycopy(src.mLocalRegs, 0, this.mLocalRegs, 0, src.mLocalRegs.length);
        if (mStoreHistoryRegType) {
            for (int i = 0; i < this.mLocalRegs.length; i++) {
                this.mHistoryLocalRegs[i].addAll(src.mHistoryLocalRegs[i]);
            }
        }

        System.arraycopy(src.mParaRegs, 0, this.mParaRegs, 0, src.mParaRegs.length);
        if (mStoreHistoryRegType) {
            for (int i = 0; i < this.mParaRegs.length; i++) {
                this.mHistoryParaRegs[i].addAll(src.mHistoryParaRegs[i]);
            }
        }

        mThisInitialized = src.mThisInitialized;
    }

    public void copyRegister1(DexRegister dst, DexRegister src, int cat) {
        mCodeAnalyzer.ensure(cat == TYPE_CATEGORY_1NR || cat == TYPE_CATEGORY_REF);
        RegType type = getRegTypeFromDexRegister(src);
        if (!setRegTypeFromDexRegister(dst, type)) {
            return;
        }
        if (!type.isConflict() &&
                ((cat == TYPE_CATEGORY_1NR && !type.isCategory1Types()) ||
                        (cat == TYPE_CATEGORY_REF && !type.isReferenceTypes()))) {
            mCodeAnalyzer.fail(0,
                    "copy1 " + dst + " < " + src + " type = " + type + " cat = " + cat);
        } else if (cat == TYPE_CATEGORY_REF) {

        }
    }

    public void copyRegister2(DexRegister dst, DexRegister src) {
        RegType typeLo = getRegTypeFromDexRegister(src);
        int hiReg = src.getRef() == DexRegister.REG_REF_LOCAL ? src.getReg()
                : src.getReg() + mLocalRegs.length;
        RegType typeHi = getRegType(hiReg + 1);

        if (!typeLo.checkWidePair(typeHi)) {
            mCodeAnalyzer.fail(0, "copy2 " + dst + " < " + src
                    + " type = " + typeLo + "/" + typeHi);
        } else {
            setRegTypeWideFromDexRegister(dst, typeLo, typeHi);
        }
    }

    public void copyResultRegister1(DexRegister dst, boolean isRef) {
        RegType type = getResultType1();
        if ((!isRef && !type.isCategory1Types()) || (isRef && !type.isReferenceTypes())){
            mCodeAnalyzer.fail(0, "copyRes1 + " + dst + " <- result0" + " type = " + type);
        } else {
            mCodeAnalyzer.ensure(getResultType(1).isUndefined());
            setRegTypeFromDexRegister(dst, type);
            setResultType1(mRegTypeCache.undefinedType());
        }

    }

    public void copyResultRegister2(DexRegister dst) {
        RegType typeLo = getResultType(0);
        RegType typeHi = getResultType(1);
        setRegTypeWideFromDexRegister(dst, typeLo, typeHi);
    }



    public boolean setRegTypeFromDexRegister(DexRegister reg, RegType newType) {
        if (newType.isLowHalf() || newType.isHighHalf()) {
            mCodeAnalyzer.fail(0, "Expected category1 register type not " + newType);
            return false;
        }

        switch (reg.getRef()) {
            case DexRegister.REG_REF_LOCAL: {
                setLocalRegType(reg.getReg(), newType);
                break;
            }
            case DexRegister.REG_REF_PARAMETER: {
                setParameterRegType(reg.getReg(), newType);
                break;
            }
            case DexRegister.REG_REF_UNSPECIFIED: {
                if (reg.getReg() < mLocalRegs.length) {
                    setLocalRegType(reg.getReg(), newType);
                } else {
                    setParameterRegType(reg.getReg() - mLocalRegs.length, newType);
                }
                break;
            }
        }
        return true;
    }

    public void setRegTypeWideFromDexRegister(DexRegister reg, RegType newType1, RegType newType2) {
        switch (reg.getRef()) {
            case DexRegister.REG_REF_LOCAL: {
                setLocalRegTypeWide(reg.getReg(), newType1, newType2);
                break;
            }
            case DexRegister.REG_REF_PARAMETER: {
                setParameterRegTypeWide(reg.getReg(), newType1, newType2);
                break;
            }
            case DexRegister.REG_REF_UNSPECIFIED: {
                if (reg.getReg() < mLocalRegs.length) {
                    setLocalRegTypeWide(reg.getReg(), newType1, newType2);
                } else {
                    setParameterRegTypeWide(reg.getReg() - mLocalRegs.length, newType1, newType2);
                }
                break;
            }
        }
    }


    public RegType getRegTypeFromDexRegister(DexRegister reg) {
        switch (reg.getRef()) {
            case DexRegister.REG_REF_LOCAL: {
                return mRegTypeCache.getFromId(mLocalRegs[reg.getReg()]);
            }
            case DexRegister.REG_REF_PARAMETER: {
                return mRegTypeCache.getFromId(mParaRegs[reg.getReg()]);
            }
            case DexRegister.REG_REF_UNSPECIFIED: {
                if (reg.getReg() < mLocalRegs.length) {
                    return mRegTypeCache.getFromId(mLocalRegs[reg.getReg()]);
                } else {
                    return mRegTypeCache.getFromId(mParaRegs[reg.getReg() - mLocalRegs.length]);
                }
            }
        }
        throw new IllegalArgumentException(String.format("unexpected reg %s", reg.toString()));
    }


    public List<RegType> getHistoryRegTypeFromDexRegister(DexRegister reg) {

        Set<Integer> resultTypeIds = null;
        switch (reg.getRef()) {
            case DexRegister.REG_REF_LOCAL: {
                resultTypeIds = mHistoryLocalRegs[reg.getReg()];
                break;
            }
            case DexRegister.REG_REF_PARAMETER: {
                resultTypeIds = mHistoryParaRegs[reg.getReg()];
                break;
            }
            case DexRegister.REG_REF_UNSPECIFIED: {
                if (reg.getReg() < mLocalRegs.length) {
                    resultTypeIds = mHistoryParaRegs[reg.getReg()];
                } else {
                    resultTypeIds = mHistoryParaRegs[reg.getReg() - mLocalRegs.length];
                }
                break;
            }
            default: {
                throw new IllegalArgumentException(String.format("unexpected reg %s", reg.toString()));
            }
        }

        ArrayList<RegType> result = new ArrayList<>();
        resultTypeIds.forEach(regId -> {
            result.add(mRegTypeCache.getFromId(regId));
        });
        return result;
    }

    public RegType getRegTypeHiFromDexRegister(DexRegister reg) {
        int regNum = reg.getRef() == DexRegister.REG_REF_PARAMETER ?
                mLocalRegs.length + reg.getReg() :
                reg.getReg();
        return getRegType(regNum + 1);
    }

    public RegType getRegType(int reg) {
        if (reg < mLocalRegs.length) {
            return mRegTypeCache.getFromId(mLocalRegs[reg]);
        } else {
            return mRegTypeCache.getFromId(mParaRegs[reg - mLocalRegs.length]);
        }
    }

    public RegType getResultType1() {
        return mRegTypeCache.getFromId(mResultRegs[0]);
    }

    public RegType getResultType(int slot) {
        return mRegTypeCache.getFromId(mResultRegs[slot]);
    }

    public void setResultType1(RegType type) {
        mResultRegs[0] = type.getId();
    }

    public void setResultTypeWide(RegType lo, RegType hi) {
        mResultRegs[0] = lo.getId();
        mResultRegs[1] = hi.getId();
    }

    public void markUninitRefsAdInvalid(RegType uninitType) {
        for (int i = 0; i < mLocalRegs.length; i++) {
            RegType regType = mRegTypeCache.getFromId(mLocalRegs[i]);
            if (regType.equals(uninitType)) {
                mLocalRegs[i] = mRegTypeCache.conflictType().getId();
            }
        }
        for (int i = 0; i < mParaRegs.length; i++) {
            RegType regType = mRegTypeCache.getFromId(mParaRegs[i]);
            if (regType.equals(uninitType)) {
                mParaRegs[i] = mRegTypeCache.conflictType().getId();
            }
        }
    }

    public boolean checkConstructorReturn() {
        if (!mThisInitialized) {
            mCodeAnalyzer.fail(0, "Constructor returning without " +
                    "calling superclass constructor");
        }
        return mThisInitialized;
    }

    public boolean verifyRegisterType(DexRegister reg, RegType checkType) {
        RegType srcType = getRegTypeFromDexRegister(reg);
        if (!checkType.isAssignableFrom(mCodeAnalyzer, srcType)) {
            if (checkType.isUnresolvedTypes() || srcType.isUnresolvedTypes()) {
                return true;
            }


            if (!checkType.isNonZeroReferenceTypes() || !srcType.isNonZeroReferenceTypes()) {

            }

            mCodeAnalyzer.fail(0, "register " + reg + " has type " + srcType
                    + " but expected " + checkType);
            return false;
        }

        if (checkType.isLowHalf()) {

        }
        return true;
    }

    public boolean verifyRegisterTypeWide(DexRegister reg, RegType checkType1, RegType checkType2) {
        RegType srcType = getRegTypeFromDexRegister(reg);
        if (!checkType1.isAssignableFrom(mCodeAnalyzer, srcType)) {
            mCodeAnalyzer.fail(MethodAnalyzer.VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                    "register " + reg + " has type " + srcType + " but expected " + checkType1);
            return false;
        }
        RegType srcTypeHi = getRegTypeHiFromDexRegister(reg);
        if (!srcType.checkWidePair(srcTypeHi)) {
            mCodeAnalyzer.fail(MethodAnalyzer.VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                    "wide register " + reg + " has type " + srcType + "/" + srcTypeHi);
            return false;
        }
        return true;
    }

    public void markRefsAsInitialized(RegType uninitType) {
        RegType initType = mRegTypeCache.fromUninitialized(uninitType);
        int changed = 0;
        for (int i = 0; i < mLocalRegs.length; i++) {
            if (mRegTypeCache.getFromId(mLocalRegs[i]).equals(uninitType)) {
                mLocalRegs[i] = initType.getId();
                changed++;
            }
        }
        for (int i = 0; i < mParaRegs.length; i++) {
            if (mRegTypeCache.getFromId(mParaRegs[i]).equals(uninitType)) {
                mParaRegs[i] = initType.getId();
                changed++;
            }
        }

        if (uninitType.isUninitializedThisReference() ||
                uninitType.isUnresolvedAndUninitializedThisReference()) {
            this.mThisInitialized = true;
        }
    }

    public void checkUnaryOp(DexRegisterList regs, RegType dstType, RegType srcType) {
        if (verifyRegisterType(regs.get(1), srcType)) {
            setRegTypeFromDexRegister(regs.get(0), dstType);
        }
    }

    public void checkUnaryOpWide(DexRegisterList regs, RegType dstType1, RegType dstType2,
                             RegType srcType1, RegType srcType2) {
        if (verifyRegisterTypeWide(regs.get(1), srcType1, srcType2)) {
            setRegTypeWideFromDexRegister(regs.get(0), dstType1, dstType2);
        }
    }


    public void checkUnaryOpToWide(DexRegisterList regs, RegType dstType1, RegType dstType2,
                                   RegType srcType) {
        if (verifyRegisterType(regs.get(1), srcType)) {
            setRegTypeWideFromDexRegister(regs.get(0), dstType1, dstType2);
        }
    }

    public void checkUnaryOpFromWide(DexRegisterList regs, RegType dstType,
                                   RegType srcType1, RegType srcType2) {
        if (verifyRegisterTypeWide(regs.get(1), srcType1, srcType2)) {
            setRegTypeFromDexRegister(regs.get(0), dstType);
        }
    }

    public void checkBinaryOp(DexRegisterList regs, RegType dstType, RegType srcType1, RegType
            srcType2, boolean checkBooleanOp) {
        if (verifyRegisterType(regs.get(1), srcType1) && verifyRegisterType(regs.get(2), srcType2)) {
            if (checkBooleanOp) {
                if (getRegTypeFromDexRegister(regs.get(1)).isBooleanTypes() &&
                        getRegTypeFromDexRegister(regs.get(2)).isBooleanTypes()) {
                    setRegTypeFromDexRegister(regs.get(0), mRegTypeCache.booleanType());
                    return;
                }
            }
        }
        setRegTypeFromDexRegister(regs.get(0), dstType);
    }

    public void checkBinaryOpWide(DexRegisterList regs, RegType dstType1, RegType dstType2,
                                  RegType srcType1_1, RegType srcType1_2,
                                  RegType srcType2_1, RegType srcType2_2) {
        if (verifyRegisterTypeWide(regs.get(1), srcType1_1, srcType1_2) &&
                verifyRegisterTypeWide(regs.get(2), srcType2_1, srcType2_2)) {
            setRegTypeWideFromDexRegister(regs.get(0), dstType1, dstType2);
        }
    }

    public void checkBinaryOpWideShift(DexRegisterList regs, RegType longLoType,
                                       RegType longHiType, RegType intType) {
        if (verifyRegisterTypeWide(regs.get(1), longLoType, longHiType) &&
                verifyRegisterType(regs.get(2), intType)) {
            setRegTypeWideFromDexRegister(regs.get(0), longLoType, longHiType);
        }
    }

    public void checkBinaryOp2addr(DexRegisterList regs, RegType dstType, RegType srcType1,
                                   RegType srcType2, boolean checkBooleanOp) {
        if (verifyRegisterType(regs.get(0), srcType1) &&
                verifyRegisterType(regs.get(1), srcType2)) {
            if (checkBooleanOp) {
                if (getRegTypeFromDexRegister(regs.get(0)).isBooleanTypes() &&
                        getRegTypeFromDexRegister(regs.get(1)).isBooleanTypes()) {
                    setRegTypeFromDexRegister(regs.get(0), mRegTypeCache.booleanType());
                    return;
                }
            }
            setRegTypeFromDexRegister(regs.get(0), dstType);
        }
    }

    public void checkBinaryOp2addrWide(DexRegisterList regs, RegType dstType1, RegType dstType2,
                                       RegType srcType1_1, RegType srcType1_2,
                                       RegType srcType2_1, RegType srcType2_2) {
        if (verifyRegisterTypeWide(regs.get(0), srcType1_1, srcType1_2) &&
                verifyRegisterTypeWide(regs.get(1), srcType2_1, srcType2_2)) {
            setRegTypeWideFromDexRegister(regs.get(0), dstType1, dstType2);
        }
    }

    public void checkBinaryOp2addrWideShift(DexRegisterList regs, RegType longLoType, RegType
            longHiType, RegType intType) {
        if (verifyRegisterTypeWide(regs.get(0), longLoType, longHiType) &&
                verifyRegisterType(regs.get(1), intType)) {
            setRegTypeWideFromDexRegister(regs.get(0), longLoType, longHiType);
        }
    }

    public void checkLiteralOp(DexRegisterList regs, RegType dstType, RegType srcType,
                               boolean checkBooleanOp, boolean isLit16,
                               DexConst.LiteralBits constant) {
        if (verifyRegisterType(regs.get(1), srcType)) {
            if (checkBooleanOp) {
                int val = constant.getIntBits();
                if (getRegTypeFromDexRegister(regs.get(1)).isBooleanTypes() && (val == 0 || val
                        == 1)) {
                    setRegTypeFromDexRegister(regs.get(0), mRegTypeCache.booleanType());
                    return;
                }

            }
            setRegTypeFromDexRegister(regs.get(0), dstType);
        }
    }

    public void setResultTypeToUnknown() {
        mResultRegs[0] = mResultRegs[1] = mRegTypeCache.undefinedType().getId();
    }

    public boolean mergeRegisters(RegisterPc mergePc, MethodAnalyzer ma) {
        boolean changed = false;
        for (int i = 0; i < mLocalRegs.length; i++) {
            if (mLocalRegs[i] != mergePc.mLocalRegs[i]) {
                RegType mergeRegType = mRegTypeCache.getFromId(mergePc.mLocalRegs[i]);
                RegType curRegType = mRegTypeCache.getFromId(mLocalRegs[i]);
                RegType newType = curRegType.merge(mergeRegType, ma, mRegTypeCache);
                changed = changed || !curRegType.equals(newType);
                mLocalRegs[i] = newType.getId();
            }
        }

        for (int i = 0; i < mParaRegs.length; i++) {
            if (mParaRegs[i] != mergePc.mParaRegs[i]) {
                RegType mergeRegType = mRegTypeCache.getFromId(mergePc.mParaRegs[i]);
                RegType curRegType = mRegTypeCache.getFromId(mParaRegs[i]);
                RegType newType = curRegType.merge(mergeRegType, ma, mRegTypeCache);
                changed = changed || !curRegType.equals(newType);
                mParaRegs[i] = newType.getId();
            }
        }

        // Check whether "this" was initialized in both paths.
        if (mThisInitialized && !mergePc.mThisInitialized) {
            mThisInitialized = false;
            changed = true;
        }
        return changed;
    }

    private static void checkRegRange(int reg, int length) {
        if (reg < 0 || reg >= length) {
            throw new IllegalArgumentException("regs length is " +
                    length + " but current reg num" + reg);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(".local " + mLocalRegs.length);
        sb.append(" ");
        sb.append(".para " + mParaRegs.length);
        sb.append(" ");

        for (int i = 0; i < mLocalRegs.length; i++) {
            RegType regType = mRegTypeCache.getFromId(mLocalRegs[i]);
            sb.append(".v" + i + " " + regType.dump());
            sb.append(", ");
        }

        for (int i = 0; i < mParaRegs.length; i++) {
            RegType regType = mRegTypeCache.getFromId(mParaRegs[i]);
            sb.append("p" + i + " " + regType.dump());
            sb.append(", ");
        }
        return sb.toString();
    }
}
