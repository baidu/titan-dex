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

import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.analyze.MethodAnalyzer;
import com.baidu.titan.dex.linker.DexClassLoader;
import com.baidu.titan.dex.node.DexClassNode;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/12/14
 */
public abstract class RegType {

    private int mId;

    private DexType mDexType;

    private DexClassNode mClassNode;

    public RegType(int id, DexType dexType, DexClassNode classNode) {
        this.mId = id;
        this.mDexType = dexType;
        this.mClassNode = classNode;
    }

    public DexType getDexType() {
        return mDexType;
    }

    public DexClassNode getClassNode() {
        return mClassNode;
    }

    public int getId() {
        return this.mId;
    }

    public boolean isUndefined() {
        return false;
    }

    public boolean isConflict() {
        return false;
    }

    public boolean isBoolean() {
        return false;
    }

    public boolean isByte() {
        return false;
    }

    public boolean isChar() {
        return false;
    }

    public boolean isShort() {
        return false;
    }

    public boolean isInteger() {
        return false;
    }

    public boolean isLongLo() {
        return false;
    }

    public boolean isLongHi() {
        return false;
    }

    public boolean isFloat() {
        return false;
    }

    public boolean isDouble() {
        return false;
    }

    public boolean isDoubleLo() {
        return false;
    }

    public boolean isDoubleHi() {
        return false;
    }

    public boolean isUnresolvedReference() {
        return false;
    }

    public boolean isUninitializedReference() {
        return false;
    }

    public boolean isUninitializedThisReference() {
        return false;
    }

    public boolean isUnresolvedAndUninitializedReference() {
        return false;
    }

    public boolean isUnresolvedAndUninitializedThisReference() {
        return false;
    }

    public boolean isUnresolvedMergedReference() {
        return false;
    }

    public boolean isUnresolvedSuperClass() {
        return false;
    }

    public boolean isReference() {
        return false;
    }

    public boolean isPreciseReference() {
        return false;
    }

    public boolean isPreciseConstant() {
        return false;
    }

    public boolean isPreciseConstantLo() {
        return false;
    }

    public boolean isPreciseConstantHi() {
        return false;
    }

    public boolean isImpreciseConstantLo() {
        return false;
    }

    public boolean isImpreciseConstantHi() {
        return false;
    }

    public boolean isImpreciseConstant() {
        return false;
    }

    public boolean isConstantTypes() {
        return false;
    }

    public boolean isConstant() {
        return isImpreciseConstant() || isPreciseConstant();
    }

    public boolean isConstantLo() {
        return isImpreciseConstantLo() || isPreciseConstantLo();
    }

    public boolean isPrecise() {
        return isPreciseConstantLo() || isPreciseConstant() ||
                isPreciseConstantHi();
    }

    boolean isLongConstant() {
        return isConstantLo();
    }

    boolean isConstantHi() {
        return (isPreciseConstantHi() || isImpreciseConstantHi());
    }

    boolean isLongConstantHigh() {
        return isConstantHi();
    }

    public boolean isUninitializedTypes() {
        return false;
    }

    public boolean isUnresolvedTypes() {
        return false;
    }

    public boolean isLowHalf() {
        return (isLongLo() || isDoubleLo() || isPreciseConstantLo() || isImpreciseConstantLo());
    }

    public boolean isHighHalf() {
        return (isLongHi() || isDoubleHi() || isPreciseConstantHi() || isImpreciseConstantHi());
    }

    public boolean isLongOrDoubleTypes() {
        return isLowHalf();
    }

    // Check this is the low half, and that type_h is its matching high-half.
    public boolean checkWidePair(RegType typeHi){
        if (isLowHalf()) {
            return ((isImpreciseConstantLo() && typeHi.isPreciseConstantHi()) ||
                    (isImpreciseConstantLo() && typeHi.isImpreciseConstantHi()) ||
                    (isPreciseConstantLo() && typeHi.isPreciseConstantHi()) ||
                    (isPreciseConstantLo() && typeHi.isImpreciseConstantHi()) ||
                    (isDoubleLo() && typeHi.isDoubleHi()) ||
                    (isLongLo() && typeHi.isLongHi()));
        }
        return false;
    }
    // The high half that corresponds to this low half
    public RegType highHalf(RegTypeCache cache) {
        if (isLongLo()) {
            return cache.longHiType();
        } else if (isDoubleLo()) {
            return cache.doubleHiType();
        } else {
            return cache.fromCat2ConstHi(((ConstantType)this).constantValue(), false);
        }
    }


    boolean isConstantBoolean() {
        if (!isConstant()) {
            return false;
        } else {
            ConstantType constVal = (ConstantType)this;
            return constVal.constantValue() >= 0 && constVal.constantValue() <= 1;
        }
    }

    public boolean isConstantChar() {
        return false;
    }

    public boolean isConstantByte() {
        return false;
    }

    public boolean isConstantShort() {
        return false;
    }

    public boolean isOne() {
        return false;
    }

    public boolean isZero() {
        return false;
    }

    public boolean isNull() {
        return false;
    }

    public boolean isReferenceTypes() {
        return isNonZeroReferenceTypes() || isZero();
    }

    public boolean isZeroOrNull() {
        return isZero() || isNull();
    }

    public boolean isNonZeroReferenceTypes() {
        return false;
    }

    public boolean isCategory1Types() {
        return isChar() || isInteger() || isFloat() || isConstant() || isByte() ||
                isShort() || isBoolean();
    }

    public boolean isCategory2Types() {
        return isLowHalf();  // Don't expect explicit testing of high halves
    }

    public boolean isBooleanTypes() {
        return isBoolean() || isConstantBoolean();
    }

    public boolean isByteTypes() {
        return isConstantByte() || isByte() || isBoolean();
    }

    public boolean isShortTypes() {
        return isShort() || isByte() || isBoolean() || isConstantShort();
    }

    public boolean isCharTypes() {
        return isChar() || isBooleanTypes() || isConstantChar();
    }

    public boolean isIntegralTypes() {
        return isInteger() || isConstant() || isByte() || isShort() || isBoolean() || isChar() ||
                isBoolean();
    }

    // Give the constant value encoded, but this shouldn't be called in the
    // general case.
    public boolean isArrayIndexTypes() {
        return isIntegralTypes();
    }

    // Float type may be derived from any constant type
    public boolean isFloatTypes() {
        return isFloat() || isConstant();
    }

    public boolean isLongTypes() {
        return isLongLo() || isLongConstant();
    }

    public boolean isLongHighTypes() {
        return (isLongHi() || isPreciseConstantHi() || isImpreciseConstantHi());
    }

    public boolean isDoubleTypes() {
        return isDoubleLo() || isLongConstant();
    }

    public boolean isDoubleHighTypes() {
        return (isDoubleHi() || isPreciseConstantHi() || isImpreciseConstantHi());
    }

    public boolean isLong() {
        return false;
    }

    boolean hasClass() {
//        boolean result = !klass_.IsNull();
//        return result;
        return mClassNode != null;
    }

    public boolean hasClassVirtual() {
        return false;
    }

    public boolean isJavaLangObject() {
        return getDexType().equals(new DexType("Ljava/lang/Object;"));
    }

    public boolean isJavaLangThrowable() {
        return getDexType().equals(new DexType("Ljava/lang/Throwable;"));
    }

    public boolean isArrayTypes() {
        if (mDexType != null) {
            return mDexType.toTypeDescriptor().charAt(0) == '[';
        }
        return false;
    }

    public boolean isObjectArrayTypes() {
        return false;
    }

    //    Primitive::Type GetPrimitiveType() const;
    boolean isJavaLangObjectArray() {
        return false;
    }

    public boolean isInstantiableTypes() {
        if (isUnresolvedTypes()) {
            return true;
        }

        if (isNonZeroReferenceTypes() && getClassNode() != null) {
            DexClassNode dcn = getClassNode();
            return (!dcn.isPrimitiveType() && !dcn.isInterface() && !dcn.isAbstract()) ||
                    (dcn.isAbstract() && dcn.isArrayClass());
        }
        return false;
    }

//    StringPiece& GetDescriptor(){
//        DCHECK(HasClass() ||
//                (IsUnresolvedTypes() && !IsUnresolvedMergedReference() &&
//                        !IsUnresolvedSuperClass()));
//        return descriptor_;
//    }
//    mirror::Class* GetClass()REQUIRES_SHARED(Locks::mutator_lock_) {
//        DCHECK(!IsUnresolvedReference());
//        DCHECK(!klass_.IsNull()) << Dump();
//        DCHECK(HasClass());
//        return klass_.Read();
//    }
//    uint16_t GetId(){ return cache_id_; }
// RegType& GetSuperClass(RegTypeCache* cache) const
//    REQUIRES_SHARED(Locks::mutator_lock_);
//
      public String dump() {
        return "dextype " + mDexType;
      }

//    REQUIRES_SHARED(Locks::mutator_lock_) = 0;
//
//    // Can this type access other?
//    bool CanAccess(const RegType& other) const
//    REQUIRES_SHARED(Locks::mutator_lock_);
//
//    // Can this type access a member with the given properties?
//    bool CanAccessMember(ObjPtr<mirror::Class> klass, uint32_t access_flags) const
//    REQUIRES_SHARED(Locks::mutator_lock_);

    // Can this type be assigned by src?
    // Note: Object and interface types may always be assigned to one another, see
    // comment on
    // ClassJoin.
    public boolean isAssignableFrom(MethodAnalyzer ca, RegType src) {
        return assignableFrom(ca, this, src, false);
    }

    private boolean assignableFrom(MethodAnalyzer ca, RegType lhs, RegType rhs, boolean strict) {
        if (lhs.equals(rhs)) {
            return true;
        } else {
            if (lhs.isBoolean()) {
                return rhs.isBooleanTypes();
            } else if (lhs.isByte()) {
                return rhs.isByteTypes();
            } else if (lhs.isShort()) {
                return lhs.isShortTypes();
            } else if (lhs.isChar()) {
                return rhs.isCharTypes();
            } else if (lhs.isInteger()) {
                return rhs.isIntegralTypes();
            } else if (lhs.isFloat()) {
                return rhs.isFloatTypes();
            } else if (lhs.isLongLo()) {
                return rhs.isLongTypes();
            } else if (lhs.isDoubleLo()) {
                return rhs.isDoubleTypes();
            } else if (lhs.isConflict()) {
                return false;
            } else {
                if (rhs.isZero()) {
                    // All reference types can be assigned null.
                    return true;
                } else if (!rhs.isReferenceTypes()) {
                    return false;
                } else if (lhs.isUninitializedTypes() || rhs.isUninitializedTypes()) {
                    // Uninitialized types are only allowed to be assigned to themselves.
                    return false;
                } else if (lhs.isJavaLangObject()) {
                    // All reference types can be assigned to Object.
                    return true;
                } else if (!strict && !lhs.isUnresolvedTypes() && lhs.getClassNode().isInterface()) {
                    // If we're not strict allow assignment to any interface, see comment in
                    // ClassJoin
                    return true;
                } else if (lhs.isJavaLangObjectArray()) {
                    return rhs.isObjectArrayTypes();
                } else if (lhs.isArrayTypes()) {
                    // TODO check later
                    return true;
                } else if (lhs.hasClass() && rhs.hasClass()) {
                    return ca.getClassLinker().isAssignableFrom(lhs.getClassNode(),
                            rhs.getClassNode(), ca.getClassLoader());
                } else {
                    // Unresolved types are only assignable for null and equality
                    return false;
                }
            }
        }
    }


    // Can this array type potentially be assigned by src.
    // This function is necessary as array types are valid even if their components types are not,
    // e.g., when they component type could not be resolved. The function will return true iff the
    // types are assignable. It will return false otherwise. In case of return=false, soft_error
    // will be set to true iff the assignment test failure should be treated as a soft-error, i.e.,
    // when both array types have the same 'depth' and the 'final' component types may be assignable
    // (both are reference types).
    public boolean canAssignArray(RegType src,
                                  RegTypeCache regTypeCache,
                                  DexClassLoader classLoader,
                                  MethodAnalyzer ma,
                                  AtomicBoolean softError) {
        softError.set(false);
        if (!isArrayTypes() || !src.isArrayTypes()) {
            softError.set(false);
            return false;
        }

        if (isUnresolvedMergedReference() && src.isUnresolvedMergedReference()) {
            // An unresolved array type means that it's an array of some reference type.
            // Reference arrays can never be assigned to primitive-type arrays, and vice versa.
            // So it is a soft error if both arrays are reference arrays, otherwise a hard error.
            softError.set(isObjectArrayTypes() && src.isObjectArrayTypes());
            return false;
        }

        RegType cmp1 = regTypeCache.getComponentType(this, classLoader);
        RegType cmp2 = regTypeCache.getComponentType(src, classLoader);

        if (cmp1.isAssignableFrom(ma, this)) {
            return true;
        }

        if (cmp1.isUnresolvedTypes()) {
            if (cmp2.isIntegralTypes() || cmp2.isFloatTypes() || cmp2.isArrayTypes()) {
                softError.set(false);
                return false;
            }
            softError.set(true);
            return false;
        }

        if (!cmp1.isArrayTypes() || !cmp2.isArrayTypes()) {
            softError.set(false);
            return false;
        }
        return cmp1.canAssignArray(cmp2, regTypeCache, classLoader, ma, softError);
    }

    // Can this type be assigned by src? Variant of IsAssignableFrom that doesn't
    // allow assignment to
    // an interface from an Object.
    boolean isStrictlyAssignableFrom(RegType src) {
        return false;
    }

    // Are these RegTypes the same?
//    boolean Equals(const RegType& other){ return GetId() == other.GetId(); }

    // Compute the merge of this register from one edge (path) with incoming_type
    // from another.
    public RegType merge(RegType inComingType, MethodAnalyzer ca, RegTypeCache regTypeCache) {
        UndefinedType undefined = regTypeCache.undefinedType();
        ConflictType conflictType = regTypeCache.conflictType();
        if (this.equals(undefined) || inComingType.equals(undefined)) {
            // There is a difference between undefined and conflict. Conflicts may be copied around, but
            // not used. Undefined registers must not be copied. So any merge with undefined should return
            // undefined.
            return undefined;
        } else if (this.equals(conflictType) || inComingType.equals(conflictType)) {
            return conflictType; // (conflict MERGE *) or (* MERGE Conflict) => Conflict
        } else if (isConstant() && inComingType.isConstant()) {
            ConstantType type1 = (ConstantType)this;
            ConstantType type2 = (ConstantType)inComingType;
            int val1 = type1.constantValue();
            int val2 = type2.constantValue();
            if (val1 >= 0 && val2 >= 0) {
                // +ve1 MERGE +ve2 => MAX(+ve1, +ve2)
                if (val1 >= val2) {
                    if (!type1.isPreciseConstant()) {
                        return this;
                    } else {
                        return regTypeCache.fromCat1Const(val1, false);
                    }
                } else {
                    if (!type2.isPreciseConstant()) {
                        return type2;
                    } else {
                        return regTypeCache.fromCat1Const(val2, false);
                    }
                }
            } else if (val1 < 0 && val2 < 0) {
                // -ve1 MERGE -ve2 => MIN(-ve1, -ve2)
                if (val1 <= val2) {
                    if (!type1.isPreciseConstant()) {
                        return this;
                    } else {
                        return regTypeCache.fromCat1Const(val1, false);
                    }
                } else {
                    if (!type2.isPreciseConstant()) {
                        return type2;
                    } else {
                        return regTypeCache.fromCat1Const(val2, false);
                    }
                }
            } else {
                if (type1.isConstantByte()) {
                    if (type2.isConstantByte()) {
                        return regTypeCache.byteConstant();
                    } else if (type2.isConstantShort()) {
                        return regTypeCache.shortConstant();
                    } else {
                        return regTypeCache.intConstant();
                    }
                } else if (type1.isConstantShort()) {
                    if (type2.isConstantShort()) {
                        return regTypeCache.shortConstant();
                    } else {
                        return regTypeCache.intConstant();
                    }
                } else {
                    return regTypeCache.intConstant();
                }
            }
        } else if (isConstantLo() && inComingType.isConstantLo()) {
            ConstantType type1 = (ConstantType)this;
            ConstantType type2 = (ConstantType)inComingType;
            int val1 = type1.constantValueLo();
            int val2 = type2.constantValueLo();
            return regTypeCache.fromCat2ConstLo(val1 | val2, false);
        } else if (isConstantHi() && inComingType.isConstantHi()) {
            ConstantType type1 = (ConstantType)this;
            ConstantType type2 = (ConstantType)inComingType;
            int val1 = type1.constantValueHi();
            int val2 = type2.constantValueHi();
            return regTypeCache.fromCat2ConstHi(val1 | val2, false);
        } else if (isIntegralTypes() && inComingType.isIntegralTypes()) {
            if (isBooleanTypes() && inComingType.isBooleanTypes()) {
                // boolean MERGE boolean => boolean
                return regTypeCache.booleanType();
            } else if (isByteTypes() && inComingType.isByteTypes()) {
                // byte MERGE byte => byte
                return regTypeCache.byteType();
            } else if (isShortTypes() && inComingType.isShortTypes()) {
                // short MERGE short => short
                return regTypeCache.shortType();
            } else if (isCharTypes() && inComingType.isCharTypes()) {
                // char MERGE char => char
                return regTypeCache.charType();
            }
            // int MERGE * => int
            return regTypeCache.integerType();
        } else if ((isFloatTypes() && inComingType.isFloatTypes()) ||
                (isLongTypes() && inComingType.isLongTypes()) ||
                (isLongHighTypes() && inComingType.isLongHighTypes()) ||
                (isDoubleTypes() && inComingType.isDoubleTypes()) ||
                (isDoubleHighTypes() && inComingType.isDoubleHighTypes())) {
            // check constant case was handled prior to entry
//            DCHECK(!IsConstant() || !inComingType.IsConstant());
            // float/long/double MERGE float/long/double_constant => float/long/double
            return selectNonConstant(this, inComingType);
        } else if (isReferenceTypes() && inComingType.isReferenceTypes()) {
            if (isUninitializedTypes() || inComingType.isUninitializedTypes()) {
                // Something that is uninitialized hasn't had its constructor called. Unitialized types are
                // special. They may only ever be merged with themselves (must be taken care of by the
                // caller of Merge(), see the DCHECK on entry). So mark any other merge as conflicting here.
                return conflictType;
            } else if (isZero() || inComingType.isZero()) {
                return selectNonConstant(this, inComingType);  // 0 MERGE ref => ref
            } else if (isJavaLangObject() || inComingType.isJavaLangObject()) {
                return regTypeCache.javaLangObject();  // Object MERGE ref => Object
            } else if (isUnresolvedTypes() || inComingType.isUnresolvedTypes()) {
                // We know how to merge an unresolved type with itself, 0 or Object. In this case we
                // have two sub-classes and don't know how to merge. Create a new string-based unresolved
                // type that reflects our lack of knowledge and that allows the rest of the unresolved
                // mechanics to continue.
//                throw new UnsupportedOperationException();
                // TODO
                return this;
                // return regTypeCache.fromUnresolvedMerge(*this, inComingType, verifier);
            } else {  // Two reference types, compute Join
                DexClassNode c1 = getClassNode();
                DexClassNode c2 = inComingType.getClassNode();
//                DCHECK(c1 != nullptr && !c1->IsPrimitive());
//                DCHECK(c2 != nullptr && !c2->IsPrimitive());
//                mirror::Class* join_class = ClassJoin(c1, c2);
                // Record the dependency that both `c1` and `c2` are assignable to `join_class`.
                // The `verifier` is null during unit tests.
//                if (verifier != nullptr) {
//                    VerifierDeps::MaybeRecordAssignability(
//                            verifier->GetDexFile(), join_class, c1, true /* strict */, true /* is_assignable */);
//                    VerifierDeps::MaybeRecordAssignability(
//                            verifier->GetDexFile(), join_class, c2, true /* strict */, true /* is_assignable */);
//                }
//                if (c1 == join_class && !IsPreciseReference()) {
//                    return *this;
//                } else if (c2 == join_class && !incoming_type.IsPreciseReference()) {
//                    return incoming_type;
//                } else {
//                    std::string temp;
//                    return reg_types->FromClass(join_class->GetDescriptor(&temp), join_class, false);
//                }
                // TODO
                return this;
            }
        } else {
            return conflictType;  // Unexpected types => Conflict
        }
    }

    private RegType selectNonConstant(RegType a, RegType b) {
        return a.isConstantTypes() ? b : a;
    }

    // Same as above, but also handles the case where incoming_type == this.
//    RegType SafeMerge(const RegType& incoming_type,
//                           RegTypeCache* reg_types,
//                           MethodVerifier* verifier) {
//
//        if (Equals(incoming_type)) {
//            return *this;
//        }
//        return Merge(incoming_type, reg_types, verifier);
//    }


    @Override
    public String toString() {
        return dump();
    }

}



