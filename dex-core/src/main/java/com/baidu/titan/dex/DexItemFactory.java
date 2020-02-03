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

package com.baidu.titan.dex;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * a factory to create DexItem in intern purpose
 *
 * @author zhangdi07@baidu.com
 * @since 2017/9/29
 */

public class DexItemFactory {

    private final Map<DexString, DexType> mTypePool = new ConcurrentHashMap<>();

    private final Map<DexString, DexTypeList> mTypeListPool = new ConcurrentHashMap<>();

    private final Map<DexString, DexString> mStringPool = new ConcurrentHashMap<>();

    public final IntegerClass integerClass = new IntegerClass();

    public final BooleanClass booleanClass = new BooleanClass();

    public final ByteClass byteClass = new ByteClass();

    public final CharacterClass characterClass = new CharacterClass();

    public final LongClass longClass = new LongClass();

    public final FloatClass floatClass = new FloatClass();

    public final DoubleClass doubleClass = new DoubleClass();

    public final ShortClass shortClass = new ShortClass();

    public final VoidClass voidClass = new VoidClass();

    public final ObjectClass objectClass = new ObjectClass();

    public final StringClass stringClass = new StringClass();

    public final ClassClass classClass = new ClassClass();

    public final Methods methods = new Methods();

    public final ThrowClasses throwClasses = new ThrowClasses();

    public final DOPS dops = new DOPS();

    public final BoxTypes boxTypes = new BoxTypes();

    public final DexConsts dexConsts = new DexConsts();

    private static class VMConstant {

        public static final String TYPE_DESC_OBJECT = "Ljava/lang/Object;";

        public static final String TYPE_DESC_BOOLEAN = "Ljava/lang/Boolean;";

        public static final String TYPE_DESC_BYTE = "Ljava/lang/Byte;";

        public static final String TYPE_DESC_VOID = "Ljava/lang/Void;";

        public static final String TYPE_DESC_SHORT = "Ljava/lang/Short;";

        public static final String TYPE_DESC_CHARACTER = "Ljava/lang/Character;";

        public static final String TYPE_DESC_INTEGER = "Ljava/lang/Integer;";

        public static final String TYPE_DESC_LONG = "Ljava/lang/Long;";

        public static final String TYPE_DESC_FLOAT = "Ljava/lang/Float;";

        public static final String TYPE_DESC_DOUBLE = "Ljava/lang/Double;";

        public static final String METHOD_NAME_VALUE_OF = "valueOf";

        public static final String METHOD_NAME_INT_VALUE = "intValue";

        public static final String METHOD_NAME_BOOLEAN_VALUE = "booleanValue";

        public static final String METHOD_NAME_FLOAT_VALUE = "floatValue";

        public static final String METHOD_NAME_LONG_VALUE = "longValue";

        public static final String METHOD_NAME_CHAR_VALUE = "charValue";

        public static final String METHOD_NAME_DOUBLE_VALUE = "doubleValue";

        public static final String METHOD_NAME_SHORT_VALUE = "shortValue";

        public static final String METHOD_NAME_BYTE_VALUE = "byteValue";

        public static final String METHOD_NAME_STATIC_CONSTRUCTOR = "<clinit>";

        public static final String METHOD_NAME_INSTANCE_CONSTRUCTOR = "<init>";
    }

    public DexType createType(String descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null!");
        }
        return createType(createString(descriptor));
    }

    public synchronized DexType createType(DexString descriptor) {
        DexType type = mTypePool.get(descriptor);
        if (type == null) {
            type = new DexType(descriptor);
            mTypePool.put(descriptor, type);
        }
        return type;
    }

    public synchronized DexString createString(String source) {
        if (source == null) {
            return null;
        }
        DexString newString = new DexString(source);

        DexString cached = this.mStringPool.putIfAbsent(newString, newString);

        return cached != null ? cached : newString;
    }

    public synchronized DexString intern(DexString dexString) {
        return mStringPool.computeIfAbsent(dexString, s -> s);
    }

    public synchronized DexTypeList intern(DexTypeList types) {
        if (types == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < types.count(); i++) {
            DexType type = types.getType(i);
            builder.append(type.toTypeDescriptor());
        }
        DexString key = new DexString(builder.toString());
        DexTypeList cached = this.mTypeListPool.putIfAbsent(key, types);
        return cached != null ? cached : types;
    }

    public synchronized DexType intern(DexType dexType) {
        return createType(dexType.toTypeDescriptor());
    }

    public synchronized DexTypeList createTypes(String[] typeDescriptors) {
        if (typeDescriptors == null || typeDescriptors.length == 0) {
            return DexTypeList.empty();
        }

        DexType[] types = new DexType[typeDescriptors.length];
        for (int i = 0; i < typeDescriptors.length; i++) {
            types[i] = createType(typeDescriptors[i]);
        }
        return intern(new DexTypeList(types));
    }

    public synchronized DexTypeList createTypes(DexType[] types) {
        if (types == null || types.length == 0) {
            return DexTypeList.empty();
        }
        return intern(new DexTypeList(types));
    }

    public synchronized DexTypeList createTypesVariable(DexType... types) {
        if (types == null || types.length == 0) {
            return DexTypeList.empty();
        }
        return intern(new DexTypeList(types));
    }

    public synchronized DexType createArrayType(DexType type) {
        DexString typeDesc = new DexString("[" + type.toTypeDescriptor());
        DexType arrayType = mTypePool.get(typeDesc);
        if (arrayType == null) {
            arrayType = new DexType(typeDesc);
            mTypePool.put(typeDesc, arrayType);
        }
        return arrayType;
    }

    public class DexConsts {

        private Map<DexType, DexConst.ConstType> mConstTypePool = new HashMap<>();

        private Map<DexConst.ConstMethodRef, DexConst.ConstMethodRef> mConstMethodPool
                = new HashMap<>();

        private Map<DexConst.ConstFieldRef, DexConst.ConstFieldRef> mConstFieldPool
                = new HashMap<>();

        private Map<DexString, DexConst.ConstString> mConstStringPool
                = new HashMap<>();

        private Map<Integer, DexConst.LiteralBits32> mLiteralBit32Pool
                = new HashMap<>();

        private Map<Long, DexConst.LiteralBits64> mLiteralBit64Pool
                = new HashMap<>();


        public synchronized DexConst.ConstType createConstType(DexType dexType) {
            return mConstTypePool.computeIfAbsent(
                    DexItemFactory.this.intern(dexType),
                    t -> DexConst.ConstType.make(t));
        }

        public synchronized DexConst.ConstType intern(DexConst.ConstType constType) {
            return createConstType(constType.value());
        }

        public synchronized DexConst.ConstMethodRef intern(DexConst.ConstMethodRef methodRef) {
            return createConstMethodRef(methodRef.getOwner(),
                    methodRef.getName(),
                    methodRef.getReturnType(),
                    methodRef.getParameterTypes());
        }

        public synchronized DexConst.ConstMethodRef createConstMethodRef(DexType owner,
                                                                         DexString name,
                                                                         DexType returnType,
                                                                         DexTypeList parameterTypes) {
            owner = DexItemFactory.this.intern(owner);
            name = DexItemFactory.this.intern(name);
            returnType = DexItemFactory.this.intern(returnType);
            parameterTypes = DexItemFactory.this.intern(parameterTypes);

            return mConstMethodPool.computeIfAbsent(
                    DexConst.ConstMethodRef.make(owner, name, returnType, parameterTypes), m -> m);

        }

        public synchronized DexConst.ConstFieldRef intern(DexConst.ConstFieldRef fieldRef) {
            return createConstFieldRef(fieldRef.getOwner(), fieldRef.getType(), fieldRef.getName());
        }

        public synchronized DexConst.ConstFieldRef createConstFieldRef(DexType owner,
                                                                       DexType type,
                                                                       DexString name) {
            owner = DexItemFactory.this.intern(owner);
            type = DexItemFactory.this.intern(type);
            name = DexItemFactory.this.intern(name);

            return mConstFieldPool.computeIfAbsent(
                    DexConst.ConstFieldRef.make(owner, type, name), f -> f);
        }

        public synchronized DexConst.ConstString createConstString(DexString string) {
            final DexString internedString = DexItemFactory.this.intern(string);
            return mConstStringPool.computeIfAbsent(internedString,
                    s -> DexConst.ConstString.make(internedString.toString()));
        }

        public synchronized DexConst.ConstString createConstString(String string) {
            final DexString internedString = DexItemFactory.this.intern(new DexString(string));
            return mConstStringPool.computeIfAbsent(internedString,
                    s -> DexConst.ConstString.make(internedString.toString()));
        }

        public synchronized DexConst.ConstString intern(DexConst.ConstString constString) {
            return createConstString(createString(constString.value()));
        }

        public synchronized DexConst.LiteralBits32 createLiteralBits32(int bits) {
            return mLiteralBit32Pool.computeIfAbsent(bits,
                    bit32 -> DexConst.LiteralBits32.make(bit32));
        }

        public synchronized DexConst.LiteralBits32 intern(DexConst.LiteralBits32 bit32) {
            return createLiteralBits32(bit32.getIntBits());
        }

        public synchronized DexConst.LiteralBits64 createLiteralBits64(long bits) {
            return mLiteralBit64Pool.computeIfAbsent(bits,
                    bit64 -> DexConst.LiteralBits64.make(bit64));
        }

        public synchronized DexConst.LiteralBits64 intern(DexConst.LiteralBits64 bit64) {
            return createLiteralBits64(bit64.getLongBits());
        }

    }

    public synchronized DexType createArrayType(String typeDesc) {
        return createArrayType(new DexType(new DexString(typeDesc)));
    }

    public class IntegerClass {

        public static final char SHORT_DESCRIPTOR = 'I';

        public final DexType primitiveType = createType("I");

        public final DexType boxedType = createType(VMConstant.TYPE_DESC_INTEGER);

        public final DexConst.ConstMethodRef valueOfMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_VALUE_OF),
                boxedType,
                createTypes(new DexType[]{primitiveType}));

        public final DexConst.ConstMethodRef primitiveValueMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_INT_VALUE),
                primitiveType,
                DexTypeList.empty());
    }

    public class BooleanClass {

        public static final char SHORT_DESCRIPTOR = 'Z';

        public final DexType primitiveType = createType("Z");

        public final DexType boxedType = createType(VMConstant.TYPE_DESC_BOOLEAN);

        public final DexConst.ConstMethodRef valueOfMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_VALUE_OF),
                boxedType,
                createTypes(new DexType[]{primitiveType}));

        public final DexConst.ConstMethodRef primitiveValueMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_BOOLEAN_VALUE),
                primitiveType,
                DexTypeList.empty());

    }

    public class ShortClass {

        public static final char SHORT_DESCRIPTOR = 'S';

        public final DexType primitiveType = createType("S");

        public final DexType boxedType = createType(VMConstant.TYPE_DESC_SHORT);

        public final DexConst.ConstMethodRef valueOfMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_VALUE_OF),
                boxedType,
                createTypes(new DexType[]{primitiveType}));

        public final DexConst.ConstMethodRef primitiveValueMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_SHORT_VALUE),
                primitiveType,
                DexTypeList.empty());

    }

    public class CharacterClass {

        public static final char SHORT_DESCRIPTOR = 'C';

        public final DexType primitiveType = createType("C");

        public final DexType boxedType = createType(VMConstant.TYPE_DESC_CHARACTER);

        public final DexConst.ConstMethodRef valueOfMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_VALUE_OF),
                boxedType,
                createTypes(new DexType[]{primitiveType}));

        public final DexConst.ConstMethodRef primitiveValueMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_CHAR_VALUE),
                primitiveType,
                DexTypeList.empty());

    }

    public class LongClass {

        public static final char SHORT_DESCRIPTOR = 'J';

        public final DexType primitiveType = createType("J");

        public final DexType boxedType = createType(VMConstant.TYPE_DESC_LONG);

        public final DexConst.ConstMethodRef valueOfMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_VALUE_OF),
                boxedType,
                createTypes(new DexType[]{primitiveType}));

        public final DexConst.ConstMethodRef primitiveValueMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_LONG_VALUE),
                primitiveType,
                DexTypeList.empty());

    }

    public class FloatClass {

        public static final char SHORT_DESCRIPTOR = 'F';

        public final DexType primitiveType = createType("F");

        public final DexType boxedType = createType(VMConstant.TYPE_DESC_FLOAT);

        public final DexConst.ConstMethodRef valueOfMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_VALUE_OF),
                boxedType,
                createTypes(new DexType[]{primitiveType}));

        public final DexConst.ConstMethodRef primitiveValueMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_FLOAT_VALUE),
                primitiveType,
                DexTypeList.empty());

    }

    public class DoubleClass {

        public static final char SHORT_DESCRIPTOR = 'D';

        public final DexType primitiveType = createType("D");

        public final DexType boxedType = createType(VMConstant.TYPE_DESC_DOUBLE);

        public final DexConst.ConstMethodRef valueOfMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_VALUE_OF),
                boxedType,
                createTypes(new DexType[]{primitiveType}));

        public final DexConst.ConstMethodRef primitiveValueMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_DOUBLE_VALUE),
                primitiveType,
                DexTypeList.empty());

    }

    public class ByteClass {

        public static final char SHORT_DESCRIPTOR = 'B';

        public final DexType primitiveType = createType("B");

        public final DexType boxedType = createType(VMConstant.TYPE_DESC_BYTE);

        public final DexConst.ConstMethodRef valueOfMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_VALUE_OF),
                boxedType,
                createTypes(new DexType[]{primitiveType}));

        public final DexConst.ConstMethodRef primitiveValueMethod = DexConst.ConstMethodRef.make(
                boxedType,
                createString(VMConstant.METHOD_NAME_BYTE_VALUE),
                primitiveType,
                DexTypeList.empty());

    }

    public class VoidClass {

        public static final char SHORT_DESCRIPTOR = 'V';

        public final DexType primitiveType = createType("V");

        public final DexType boxedType = createType(VMConstant.TYPE_DESC_VOID);

    }

    public class ObjectClass {

        public final DexType type = createType("Ljava/lang/Object;");

        public final DexConst.ConstMethodRef initMethod =
                DexConst.ConstMethodRef.make(
                        type,
                        createString("<init>"),
                        voidClass.primitiveType,
                        DexTypeList.empty());

    }

    public class ReferenceType {

        public static final char SHORT_DESCRIPTOR = 'L';

    }

    public class ArrayType {
        public static final char SHORT_DESCRIPTOR = '[';
    }

    public class StringClass {

        public final DexType type = createType("Ljava/lang/String;");

        public final DexConst.ConstMethodRef equalsMethod =
                DexConst.ConstMethodRef.make(
                        type,
                        createString("equals"),
                        booleanClass.primitiveType,
                        createTypesVariable(objectClass.type));

    }

    public class ClassClass {

        public final DexType type = createType("Ljava/lang/Class;");

    }

    public class BoxTypes {

        public DexType getBoxedTypeForPrimitiveType(DexType type) {
            switch (type.toShortDescriptor()) {
                case BooleanClass.SHORT_DESCRIPTOR: {
                    return booleanClass.boxedType;
                }
                case ByteClass.SHORT_DESCRIPTOR: {
                    return byteClass.boxedType;
                }
                case ShortClass.SHORT_DESCRIPTOR: {
                    return shortClass.boxedType;
                }
                case CharacterClass.SHORT_DESCRIPTOR: {
                    return characterClass.boxedType;
                }
                case IntegerClass.SHORT_DESCRIPTOR: {
                    return integerClass.boxedType;
                }
                case FloatClass.SHORT_DESCRIPTOR: {
                    return floatClass.boxedType;
                }
                case LongClass.SHORT_DESCRIPTOR: {
                    return longClass.boxedType;
                }
                case DoubleClass.SHORT_DESCRIPTOR: {
                    return doubleClass.boxedType;
                }
                default: {
                    throw new IllegalArgumentException("unknown type " + type);
                }
            }
        }

    }

    public class Methods {

        public final DexString initMethodName = createString("<init>");

        public final DexString staticInitMethodName = createString("<clinit>");

        public DexConst.ConstMethodRef valueOfMethodForType(DexType t) {
            switch (t.toShortDescriptor()) {
                case BooleanClass.SHORT_DESCRIPTOR: {
                    return booleanClass.valueOfMethod;
                }
                case ByteClass.SHORT_DESCRIPTOR: {
                    return byteClass.valueOfMethod;
                }
                case ShortClass.SHORT_DESCRIPTOR: {
                    return shortClass.valueOfMethod;
                }
                case CharacterClass.SHORT_DESCRIPTOR: {
                    return characterClass.valueOfMethod;
                }
                case IntegerClass.SHORT_DESCRIPTOR: {
                    return integerClass.valueOfMethod;
                }
                case FloatClass.SHORT_DESCRIPTOR: {
                    return floatClass.valueOfMethod;
                }
                case LongClass.SHORT_DESCRIPTOR: {
                    return longClass.valueOfMethod;
                }
                case DoubleClass.SHORT_DESCRIPTOR: {
                    return doubleClass.valueOfMethod;
                }
            }
            return null;
        }

        public DexConst.ConstMethodRef primitiveValueMethodForType(DexType t) {
            switch (t.toShortDescriptor()) {
                case BooleanClass.SHORT_DESCRIPTOR: {
                    return booleanClass.primitiveValueMethod;
                }
                case ByteClass.SHORT_DESCRIPTOR: {
                    return byteClass.primitiveValueMethod;
                }
                case ShortClass.SHORT_DESCRIPTOR: {
                    return shortClass.primitiveValueMethod;
                }
                case CharacterClass.SHORT_DESCRIPTOR: {
                    return characterClass.primitiveValueMethod;
                }
                case IntegerClass.SHORT_DESCRIPTOR: {
                    return integerClass.primitiveValueMethod;
                }
                case FloatClass.SHORT_DESCRIPTOR: {
                    return floatClass.primitiveValueMethod;
                }
                case LongClass.SHORT_DESCRIPTOR: {
                    return longClass.primitiveValueMethod;
                }
                case DoubleClass.SHORT_DESCRIPTOR: {
                    return doubleClass.primitiveValueMethod;
                }
            }
            return null;
        }

    }

    public class ThrowClasses {

        public final IllegalStateExceptionClass illegalStateExceptionClass =
                new IllegalStateExceptionClass();

        public class IllegalStateExceptionClass {

            public final DexType type = createType("Ljava/lang/IllegalStateException;");

            public final DexConst.ConstMethodRef defaultInitMethod =
                    DexConst.ConstMethodRef.make(
                            type,
                            methods.initMethodName,
                            voidClass.primitiveType,
                            DexTypeList.empty());

            public final DexConst.ConstMethodRef detailInitMethod =
                    DexConst.ConstMethodRef.make(
                            type,
                            methods.initMethodName,
                            voidClass.primitiveType,
                            createTypesVariable(stringClass.type));

        }

    }

    public class DOPS {

        public Dop getFieldGetOpForType(DexType type, boolean staticField) {
            switch (type.toShortDescriptor()) {
                case BooleanClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SGET_BOOLEAN : Dops.IGET_BOOLEAN);
                }
                case ByteClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SGET_BYTE : Dops.IGET_BYTE);
                }
                case ShortClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SGET_SHORT : Dops.IGET_SHORT);
                }
                case CharacterClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SGET_CHAR : Dops.IGET_CHAR);
                }
                case IntegerClass.SHORT_DESCRIPTOR:
                case FloatClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SGET : Dops.IGET);
                }
                case LongClass.SHORT_DESCRIPTOR:
                case DoubleClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SGET_WIDE : Dops.IGET_WIDE);
                }
                case ArrayType.SHORT_DESCRIPTOR:
                case ReferenceType.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SGET_OBJECT : Dops.IGET_OBJECT);
                }
                default: {
                    throw new IllegalArgumentException("unknown type " + type);
                }
            }
        }

        public Dop getFieldPutOpForType(DexType type, boolean staticField) {
            switch (type.toShortDescriptor()) {
                case BooleanClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SPUT_BOOLEAN : Dops.IPUT_BOOLEAN);
                }
                case ByteClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SPUT_BYTE : Dops.IPUT_BYTE);
                }
                case ShortClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SPUT_SHORT : Dops.IPUT_SHORT);
                }
                case CharacterClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SPUT_CHAR : Dops.IPUT_CHAR);
                }
                case IntegerClass.SHORT_DESCRIPTOR:
                case FloatClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SPUT : Dops.IPUT);
                }
                case LongClass.SHORT_DESCRIPTOR:
                case DoubleClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SPUT_WIDE : Dops.IPUT_WIDE);
                }
                case ArrayType.SHORT_DESCRIPTOR:
                case ReferenceType.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(staticField ? Dops.SPUT_OBJECT : Dops.IPUT_OBJECT);
                }
                default: {
                    throw new IllegalArgumentException("unknown type " + type);
                }
            }
        }

        public Dop getArrayGetOpForType(DexType type) {
            switch (type.toShortDescriptor()) {
                case BooleanClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.AGET_BOOLEAN);
                }
                case ByteClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.AGET_BYTE);
                }
                case ShortClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.AGET_SHORT);
                }
                case CharacterClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.AGET_CHAR);
                }
                case IntegerClass.SHORT_DESCRIPTOR:
                case FloatClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.AGET);
                }
                case LongClass.SHORT_DESCRIPTOR:
                case DoubleClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.AGET_WIDE);
                }
                case ArrayType.SHORT_DESCRIPTOR:
                case ReferenceType.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.AGET_OBJECT);
                }
                default: {
                    throw new IllegalArgumentException("unknown type " + type);
                }
            }
        }

        public Dop getArrayPutOpForType(DexType type) {
            switch (type.toShortDescriptor()) {
                case BooleanClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.APUT_BOOLEAN);
                }
                case ByteClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.APUT_BYTE);
                }
                case ShortClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.APUT_SHORT);
                }
                case CharacterClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.APUT_CHAR);
                }
                case IntegerClass.SHORT_DESCRIPTOR:
                case FloatClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.APUT);
                }
                case LongClass.SHORT_DESCRIPTOR:
                case DoubleClass.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.APUT_WIDE);
                }
                case ArrayType.SHORT_DESCRIPTOR:
                case ReferenceType.SHORT_DESCRIPTOR: {
                    return Dops.dopFor(Dops.APUT_OBJECT);
                }
                default: {
                    throw new IllegalArgumentException("unknown type " + type);
                }
            }
        }

        public Dop getReturnOpForType(DexType type) {
            if (type.isVoidType()) {
                return Dops.dopFor(Dops.RETURN_VOID);
            } else if (type.isReferenceType() || type.isArrayType()) {
                return Dops.dopFor(Dops.RETURN_OBJECT);
            } else if (type.isPrimitiveType()) {
                return Dops.dopFor(type.isWideType() ? Dops.RETURN_WIDE : Dops.RETURN);
            } else {
                throw new IllegalArgumentException("unknown type " + type);
            }
        }

        public Dop getMoveResultOpForType(DexType type) {
            if (type.isReferenceType() || type.isArrayType()) {
                return Dops.dopFor(Dops.MOVE_RESULT_OBJECT);
            } else if (type.isPrimitiveType() && type.isWideType()) {
                return Dops.dopFor(Dops.MOVE_RESULT_WIDE);
            } else if (type.isPrimitiveType()) {
                return Dops.dopFor(Dops.MOVE_RESULT);
            } else {
                throw new IllegalArgumentException("unknown type " + type);
            }
        }

    }

}
