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

package com.baidu.titan.dex.analyze.types;

import com.baidu.titan.dex.DexAccessFlags;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.linker.DexClassLoader;
import com.baidu.titan.dex.node.DexClassNode;

import java.util.ArrayList;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/12/28
 */
public class RegTypeCache {

    private ArrayList<RegType> mCachedRegTypes = new ArrayList<>();

    static class ClassNodeTypeHolder {

        public ClassNodeTypeHolder(DexType dexType, RegType regType) {
            this.type = dexType;
            this.regType = regType;
        }

        public DexType type;
        public RegType regType;
    }

    private ArrayList<ClassNodeTypeHolder> mClassEntries = new ArrayList<>();

    private UndefinedType mUndefinedTypeInstance;

    private ConflictType mConflictTypeInstance;

    private BooleanType mBooleanTypeInstance;

    private ByteType mByteTypeInstance;

    private ShortType mShortTypeInstance;

    private CharType mCharTypeInstance;

    private IntegerType mIntegerTypeInstance;

    private FloatType mFloatTypeInstance;

    private LongLoType mLongLoTypeInstance;

    private LongHiType mLongHiTypeInstance;

    private DoubleLoType mDoubleLoTypeInstance;

    private DoubleHiType mDoubleHiTypeInstance;

    private static final int MIN_SMALL_CONSTANT = -1;

    private static final int MAX_SMALL_CONSTANT = 4;

    private int mPrimitiveCount;

    private PreciseConstType[] mSmallPreciseConsts = new PreciseConstType[MAX_SMALL_CONSTANT -
            MIN_SMALL_CONSTANT + 1];

    private DexClassLoader mClassLoader;

    public RegTypeCache(DexClassLoader classLoader) {
        this.mClassLoader = classLoader;
        initPrimitiveTypeAndSmallConstantTypes();
    }

    private void initPrimitiveTypeAndSmallConstantTypes() {
        int preserveId = 0;
        addEntry(mUndefinedTypeInstance = new UndefinedType(preserveId++));
        addEntry(mConflictTypeInstance = new ConflictType(preserveId++));
        addEntry(mBooleanTypeInstance = new BooleanType(preserveId++));
        addEntry(mByteTypeInstance = new ByteType(preserveId++));
        addEntry(mShortTypeInstance = new ShortType(preserveId++));
        addEntry(mCharTypeInstance = new CharType(preserveId++));
        addEntry(mIntegerTypeInstance = new IntegerType(preserveId++));
        addEntry(mLongLoTypeInstance = new LongLoType(preserveId++));
        addEntry(mLongHiTypeInstance = new LongHiType(preserveId++));
        addEntry(mFloatTypeInstance = new FloatType(preserveId++));
        addEntry(mDoubleLoTypeInstance = new DoubleLoType(preserveId++));
        addEntry(mDoubleHiTypeInstance = new DoubleHiType(preserveId++));

        for (int i = MIN_SMALL_CONSTANT; i <= MAX_SMALL_CONSTANT; i++) {
            PreciseConstType type = new PreciseConstType(preserveId++, i);
            addEntry(type);
            mSmallPreciseConsts[i - MIN_SMALL_CONSTANT] = type;
        }
        mPrimitiveCount = preserveId;
    }

    public RegType getFromId(int id) {
        return mCachedRegTypes.get(id);
    }



    public ConstantType fromCat1Const(int value, boolean precise) {
        return fromCat1NonSmallConstant(value, precise);
    }

    public ConstantType fromCat2ConstLo(long value, boolean precise) {
        int loValue = (int)value;

        for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
            RegType entry = mCachedRegTypes.get(i);
            if (entry.isConstantLo() && entry.isPrecise()
                    && ((ConstantType)entry).constantValueLo() == loValue) {
                return (ConstantType)entry;
            }
        }
        ConstantType type;
        if (precise) {
            type = new PreciseConstLoType(mCachedRegTypes.size(), loValue);
        } else {
            type = new ImpreciseConstLoType(mCachedRegTypes.size(), loValue);
        }
        addEntry(type);
        return type;
    }

    public ConstantType fromCat2ConstHi(long value, boolean precise) {
        int hiValue = (int)(value >> 32);

        for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
            RegType entry = mCachedRegTypes.get(i);
            if (entry.isConstantHi() && entry.isPrecise() == precise
                    && ((ConstantType)entry).constantValueHi() == hiValue) {
                return (ConstantType)entry;
            }
        }
        ConstantType type;
        if (precise) {
            type = new PreciseConstHiType(mCachedRegTypes.size(), hiValue);
        } else {
            type = new ImpreciseConstHiType(mCachedRegTypes.size(), hiValue);
        }
        addEntry(type);
        return type;
    }


    public synchronized ConstantType fromCat1NonSmallConstant(int value, boolean precise) {
        for (int i = 0; i < mCachedRegTypes.size(); i++) {
            RegType curType = mCachedRegTypes.get(i);
            if (curType.isConstant()) {
                ConstantType curConstantType = (ConstantType)curType;

                if (curConstantType.isConstant() && curConstantType.isPreciseConstant() == precise &&
                        curConstantType.constantValue() == value) {
                    return curConstantType;
                }
            }
        }

        ConstantType constantType;
        if (precise) {
            constantType = new PreciseConstType(mCachedRegTypes.size(), value);
        } else {
            constantType = new ImpreciseConstType(mCachedRegTypes.size(), value);
        }
        addEntry(constantType);
        return constantType;
    }

    public ImpreciseConstType byteConstant() {
        return (ImpreciseConstType)fromCat1Const(Byte.MIN_VALUE, false);
    }

    public ImpreciseConstType posByteConstant() {
        return (ImpreciseConstType)fromCat1Const(Byte.MAX_VALUE, false);
    }

    public ImpreciseConstType charConstant() {
        return (ImpreciseConstType)fromCat1Const(Character.MAX_VALUE, false);
    }

    public ImpreciseConstType shortConstant() {
        return (ImpreciseConstType)fromCat1Const(Short.MIN_VALUE, false);
    }

    public ImpreciseConstType posShortConstant() {
        return (ImpreciseConstType)fromCat1Const(Short.MAX_VALUE, false);
    }

    public ImpreciseConstType intConstant() {
        return (ImpreciseConstType)fromCat1Const(Integer.MIN_VALUE, false);
    }

    public UndefinedType undefinedType() {
        return mUndefinedTypeInstance;
    }

    public BooleanType booleanType() {
        return mBooleanTypeInstance;
    }

    public RegType javaLangObject() {
        return fromDescriptor(mClassLoader, new DexType("Ljava/lang/Object;"), false);
    }

    public RegType javaLangClass() {
        return fromDescriptor(
                mClassLoader, new DexType("Ljava/lang/Class;"), false);
    }

    public RegType javaLangThrowable() {
        return fromDescriptor(
                mClassLoader, new DexType("Ljava/lang/Throwable;"), false);
    }

    public RegType javaLangString() {
        return fromDescriptor(
                mClassLoader, new DexType("Ljava/lang/String;"), true);
    }

    public CharType charType() {
        return mCharTypeInstance;
    }

    public ByteType byteType() {
        return mByteTypeInstance;
    }

    public IntegerType integerType() {
        return mIntegerTypeInstance;
    }

    public ShortType shortType() {
        return mShortTypeInstance;
    }

    public FloatType floatType() {
        return mFloatTypeInstance;
    }

    public LongLoType longLoType() {
        return mLongLoTypeInstance;
    }

    public LongHiType longHiType() {
        return mLongHiTypeInstance;
    }

    public DoubleLoType doubleLoType() {
        return mDoubleLoTypeInstance;
    }

    public DoubleHiType doubleHiType() {
        return mDoubleHiTypeInstance;
    }

    public ConflictType conflictType() {
        return mConflictTypeInstance;
    }

    public RegType zero() {
        return fromCat1Const(0, true);
    }

    public RegType one() {
        return fromCat1Const(1, true);
    }

    public UninitializedType uninitialized(RegType type, int allocationPc) {
        UninitializedType entry = null;
        if (type.isUnresolvedTypes()) {
            for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
                RegType curEntry = mCachedRegTypes.get(i);
                if (curEntry.isUnresolvedAndUninitializedReference() &&
                        ((UnresolvedUninitializedRefType)curEntry).getAllocationPc() == allocationPc &&
                        curEntry.getDexType().equals(type.getDexType())) {
                    return (UnresolvedUninitializedRefType)curEntry;
                }
            }
            entry = new UnresolvedUninitializedRefType(mCachedRegTypes.size(), type.getDexType(),
                    allocationPc);
        } else {
            for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
                RegType curEntry = mCachedRegTypes.get(i);
                if (curEntry.isUninitializedReference() &&
                        ((UninitializedReferenceType)curEntry).getAllocationPc() == allocationPc &&
                        type.getDexType().equals(curEntry.getDexType())) {
                    return (UninitializedReferenceType)curEntry;
                }
            }
            entry = new UninitializedReferenceType(mCachedRegTypes.size(), type.getDexType(),
                    type.getClassNode(), allocationPc);
        }
        addEntry(entry);
        return entry;
    }

    public DexClassNode resolveClass(DexClassLoader loader, DexType type) {
        return loader.findClass(type);
    }

    private boolean matchDexType(RegType testType, DexType expectedType, boolean precise) {
        if (!expectedType.equals(testType.getDexType())) {
            return false;
        }
        if (testType.hasClass()) {
            return matchingPrecisionForClass(testType, precise);
        }
        return true;
    }

    private boolean matchingPrecisionForClass(RegType testType, boolean precise) {
        if (testType.isPreciseReference() == precise) {
            return true;
        }
        return false;
    }

    public RegType from(DexClassLoader loader, DexType type, boolean precise) {
        // TODO
        for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
            RegType curEntry = mCachedRegTypes.get(i);
            if (matchDexType(curEntry, type, precise)) {
                return curEntry;
            }
        }

        DexClassNode dcn = resolveClass(loader, type);
        RegType regType = null;
        if (dcn != null) {
            if (precise) {
                regType = new PreciseReferenceType(mCachedRegTypes.size(), type, dcn);
            } else {
                regType = new ReferenceType(mCachedRegTypes.size(), type, dcn);
            }
            addEntry(regType);
        } else { // Class not resolved
            regType = new UnresolvedReferenceType(mCachedRegTypes.size(), type);
            addEntry(regType);
        }
        return regType;
    }

    public RegType fromClass(DexType type, DexClassNode dcn, boolean precise) {
        RegType regType = findClassNode(dcn, precise);
        if (regType == null) {
            regType = insertClass(dcn, precise);
        }
        return regType;
    }

    public RegType fromDescriptor(DexClassLoader loader, DexType type, boolean precise) {
        switch (type.toTypeDescriptor().charAt(0)) {
            case 'Z': {
                return booleanType();
            }
            case 'B': {
                return byteType();
            }
            case 'S': {
                return shortType();
            }
            case 'C': {
                return charType();
            }
            case 'I': {
                return integerType();
            }
            case 'J': {
                return longLoType();
            }
            case 'F': {
                return floatType();
            }
            case 'D': {
                return doubleLoType();
            }
            case 'V': {
                return conflictType();
            }
            case 'L':
            case '[': {
                return from(loader, type, precise);
            }
            default: {
                return conflictType();
            }
        }
    }

    public RegType findClassNode(DexClassNode dcn, boolean precise) {
        if (dcn.isPrimitiveType()) {
            return regTypeFromPrimitiveType(dcn.type);
        }

        for (ClassNodeTypeHolder cnth : mClassEntries) {
            if (cnth.type.equals(dcn.type) && cnth.regType.isPreciseReference() == precise) {
                return cnth.regType;
            }
        }
        return null;
    }

    public RegType regTypeFromPrimitiveType(DexType type) {
        switch (type.toTypeDescriptor().charAt(0)) {
            case 'Z': {
                return mBooleanTypeInstance;
            }
            case 'B': {
                return mByteTypeInstance;
            }
            case 'S': {
                return mShortTypeInstance;
            }
            case 'C': {
                return mCharTypeInstance;
            }
            case 'I': {
                return mIntegerTypeInstance;
            }
            case 'J': {
                return mLongLoTypeInstance;
            }
            case 'F': {
                return mFloatTypeInstance;
            }
            case 'D': {
                return mDoubleLoTypeInstance;
            }
            case 'V': {
                return mConflictTypeInstance;
            }
        }
        throw new IllegalStateException();
    }

    public RegType insertClass(DexClassNode dcn, boolean precise) {
        RegType regType = precise ?
                new PreciseReferenceType(mCachedRegTypes.size(), dcn.type, dcn) :
                new ReferenceType(mCachedRegTypes.size(), dcn.type, dcn);
        addEntry(regType);
        return regType;
    }

//    public RegType fromDexType(DexType dexType, boolean precise) {
//        switch (dexType.toTypeDescriptor().charAt(0)) {
//            case 'Z': {
//                return mBooleanTypeInstance;
//            }
//            case 'B': {
//                return mByteTypeInstance;
//            }
//            case 'S': {
//                return mShortTypeInstance;
//            }
//            case 'C': {
//                return mCharTypeInstance;
//            }
//            case 'I': {
//                return mIntegerTypeInstance;
//            }
//            case 'J': {
//                return mLongLoTypeInstance;
//            }
//            case 'F': {
//                return mFloatTypeInstance;
//            }
//            case 'D': {
//                return mDoubleLoTypeInstance;
//            }
//            case 'V': {
//                // conflict
//            }
//            case 'L':
//            case '[': {
//                RegType refType;
//                if (precise) {
//                    refType = new PreciseReferenceType(mCachedRegTypes.size(), dexType);
//                } else {
//                    refType = new ReferenceType(mCachedRegTypes.size(), dexType);
//                }
//                return refType;
//            }
//
//        }
//        return conflictType();
//    }

//    public synchronized RegType fromReferenceType(DexType dexType ,boolean precise) {
//        for (int i = 0; i < mCachedRegTypes.size(); i++) {
//            RegType regType = mCachedRegTypes.get(i);
//            if (regType.hasClass()) {
//                if (dexType.equals(regType.getDexType())
//                        && regType.isPreciseReference() == precise) {
//                    return regType;
//                }
//            }
//        }
//
//        RegType refType;
//        if (precise) {
//            refType = new PreciseReferenceType(mCachedRegTypes.size(), dexType);
//        } else {
//            refType = new ReferenceType(mCachedRegTypes.size(), dexType);
//        }
//
//        addEntry(refType);
//
//        return refType;
//
//    }

    public RegType fromUninitialized(RegType uninitType) {
        RegType resultType = null;
        if (uninitType.isUnresolvedTypes()) {
            for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
                RegType curEntry = mCachedRegTypes.get(i);
                if (curEntry.isUnresolvedReference() &&
                        curEntry.getDexType().equals(uninitType.getDexType())) {
                    return curEntry;
                }
            }
            resultType = new UnresolvedReferenceType(
                    mCachedRegTypes.size(), uninitType.getDexType());
        } else {
            DexClassNode classNode = uninitType.getClassNode();
            if (uninitType.isUninitializedThisReference() &&
                    !classNode.accessFlags.containsNoneOf(DexAccessFlags.ACC_FINAL)) {
                // For uninitialized "this reference" look for reference types that are not precise.
                for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
                    RegType curEntry = mCachedRegTypes.get(i);
                    if (curEntry.isReferenceTypes()
                            && curEntry.getClassNode().type.equals(classNode.type)) {
                        return curEntry;
                    }
                }
                resultType = new ReferenceType(mCachedRegTypes.size(), null, classNode);
            } else if (classNode != null) { // TODO primitive
                for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
                    RegType curEntry = mCachedRegTypes.get(i);
                    if (curEntry.isPreciseReference() &&
                            curEntry.getClassNode().type.equals(classNode.type)) {
                        return curEntry;
                    }
                }
                resultType =
                        new PreciseReferenceType(mCachedRegTypes.size(), classNode.type, classNode);
            } else {
                return conflictType();
            }
        }
        return addEntry(resultType);
    }


    private RegType addEntry(RegType regType) {
        mCachedRegTypes.add(regType);
        if (regType.hasClass()) {
            mClassEntries.add(new ClassNodeTypeHolder(regType.getDexType(), regType));
        }
        return regType;
    }


    public UninitializedType uninitializedThisArgument(RegType regType) {
        UninitializedType entry = null;
        if (regType.isUnresolvedTypes()) {
            for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
                RegType curEntry = mCachedRegTypes.get(i);
                if (curEntry.isUnresolvedAndUninitializedThisReference() &&
                        regType.getDexType().equals(curEntry.getDexType())) {
                    return (UninitializedType) curEntry;
                }
            }
            entry = new UnresolvedUninitializedThisRefType(mCachedRegTypes.size(), regType
                    .getDexType());
        } else {
            DexClassNode classNode = regType.getClassNode();
            for (int i = mPrimitiveCount; i < mCachedRegTypes.size(); i++) {
                RegType curEntry = mCachedRegTypes.get(i);
                if (curEntry.isUninitializedThisReference() && regType.getDexType().equals(curEntry.getDexType())) {
                    return (UninitializedType)curEntry;
                }
            }
            entry = new UninitializedThisReferenceType(mCachedRegTypes.size(),
                    regType.getDexType(), classNode);
        }
        addEntry(entry);
        return entry;
    }

    public RegType getComponentType(RegType array, DexClassLoader loader) {
        DexType arrayDexType = array.getDexType();
        String arrayDesc = arrayDexType.toTypeDescriptor();
        DexType componentDexType = new DexType(arrayDesc.substring(1));
        if (!array.isArrayTypes()) {
            return conflictType();
        } else if (array.isUnresolvedTypes()) {
            return fromDescriptor(loader, componentDexType, false);
        } else {
            return fromDescriptor(loader, componentDexType, false);
        }
    }


}
