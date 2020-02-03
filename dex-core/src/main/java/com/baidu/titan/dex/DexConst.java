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

import com.baidu.titan.dex.util.SmaliStringUtils;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 用来表示Dex结构中的各种常量 <br>
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/6
 */
public class DexConst {

    private boolean mImmutable = false;

    public String toSmaliString() {
        StringWriter writer = new StringWriter();
        SmaliWriter sw = new SmaliWriter(writer);
        smaliTo(sw);
        return writer.toString();
    }

    /**
     * 常量内部成员是否可变
     *
     * @return
     */
    public boolean isImmutable() {
        return mImmutable;
    }

    void setImmutable(boolean immutable) {
        this.mImmutable = immutable;
    }

    /**
     * 设置为不可变，不可逆
     */
    public void setImmutable() {
        setImmutable(true);
        onImmutable();
    }

    protected void onImmutable() {
    }

    public void smaliTo(SmaliWriter writer) {

    }


    /**
     * 基本类型字面量
     */
    public static abstract class LiteralBits extends DexConst {

        /**
         * 使用Int类型表示
         *
         * @return
         */
        public abstract int getIntBits();

        /**
         * 使用long类型表示
         *
         * @return
         */
        public abstract long getLongBits();

        /**
         * 判断使用Int类型能否满足位数需求
         *
         * @return
         */
        public abstract boolean fitsInInt();

        /**
         * 判断使用16Bit能够满足位数需求
         *
         * @return
         */
        public boolean fitsIn16Bits() {
            if (!fitsInInt()) {
                return false;
            }
            int bits = getIntBits();
            return (short) bits == bits;
        }

        /**
         * 判断使用8Bit能够满足位数需求
         *
         * @return
         */
        public boolean fitsIn8Bits() {
            if (!fitsInInt()) {
                return false;
            }
            int bits = getIntBits();
            return (byte) bits == bits;
        }
    }

    /**
     * 使用32bit表示的字面量
     */
    public static class LiteralBits32 extends LiteralBits {

        private final int mBits;

        LiteralBits32(int bits) {
            this.mBits = bits;
        }

        public static LiteralBits32 make(int value) {
            return new LiteralBits32(value);
        }

        @Override
        public int getIntBits() {
            return mBits;
        }

        @Override
        public long getLongBits() {
            return mBits;
        }

        @Override
        public boolean fitsInInt() {
            return true;
        }

        @Override
        public String toString() {
            return "literal32/" + mBits;
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write(String.format("0x%x", this.mBits));
        }
    }

    /**
     * 使用64bit表示的字面量
     */
    public static class LiteralBits64 extends LiteralBits {

        private final long mBits;

        LiteralBits64(long bits) {
            this.mBits = bits;
        }

        public static LiteralBits64 make(long value) {
            return new LiteralBits64(value);
        }

        @Override
        public int getIntBits() {
            return (int) mBits;
        }

        @Override
        public long getLongBits() {
            return mBits;
        }

        @Override
        public boolean fitsInInt() {
            return (int) mBits == mBits;
        }

        @Override
        public String toString() {
            return "literal64/" + mBits;
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write(String.format("0x%xL", this.mBits));
        }

    }

    /**
     * 字符串常量
     */
    public static class ConstString extends DexConst {

        private String mValue;

        private ConstString(String value) {
            this.mValue = value;
        }

        public static ConstString make(String value) {
            return new ConstString(value);
        }

        public String value() {
            return mValue;
        }

        @Override
        public String toString() {
            return toSmaliString();
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write('"');
            writer.write(this.mValue);
            writer.write('"');
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConstString)) return false;
            ConstString that = (ConstString) o;
            return Objects.equals(mValue, that.mValue);
        }

        @Override
        public int hashCode() {

            return Objects.hash(mValue);
        }

    }

    /**
     * 类型常量
     */
    public static class ConstType extends DexConst {

        private DexType mValue;

        private ConstType(DexType value) {
            this.mValue = value;
        }

        public static ConstType make(DexType value) {
            return new ConstType(value);
        }

        public DexType value() {
            return mValue;
        }

        @Override
        public String toString() {
            return toSmaliString();
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write(mValue.toTypeDescriptor());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConstType)) return false;
            ConstType constType = (ConstType) o;
            return Objects.equals(mValue, constType.mValue);
        }

        @Override
        public int hashCode() {

            return Objects.hash(mValue);
        }
    }

    /**
     * 成员常量
     */
    public static abstract class ConstMemberRef extends DexConst {

        protected DexType mOwner;

        protected DexString mName;

        ConstMemberRef(DexType owner, DexString name) {
            this.mOwner = owner;
            this.mName = name;
        }

        /**
         * 宿主类型描述符
         *
         * @return
         */
        public DexType getOwner() {
            return mOwner;
        }

        /**
         * 成员名称
         *
         * @return
         */
        public DexString getName() {
            return mName;
        }

        @Override
        public String toString() {
            return "ConstMemberRef{" +
                    "mOwner='" + mOwner + '\'' +
                    ", mName='" + mName + '\'' +
                    "} " + super.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConstMemberRef)) return false;
            ConstMemberRef that = (ConstMemberRef) o;
            return Objects.equals(mOwner, that.mOwner) &&
                    Objects.equals(mName, that.mName);
        }

        @Override
        public int hashCode() {

            return Objects.hash(mOwner, mName);
        }
    }

    /**
     * 字段常量
     */
    public static class ConstFieldRef extends ConstMemberRef {

        private DexType mType;

        ConstFieldRef(DexType owner, DexString name, DexType type) {
            super(owner, name);
            this.mType = type;
        }

        public static ConstFieldRef make(DexType owner, DexType type, DexString name) {
            return new ConstFieldRef(owner, name, type);
        }

        /**
         * 获取类型描述符
         *
         * @return
         */
        public DexType getType() {
            return mType;
        }

        @Override
        public String toString() {
            return toSmaliString();
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write(this.mOwner.toTypeDescriptor());
            writer.write("->");
            writer.write(this.mName.toString());
            writer.write(":");
            writer.write(this.mType.toTypeDescriptor());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConstFieldRef)) return false;
            if (!super.equals(o)) return false;
            ConstFieldRef that = (ConstFieldRef) o;
            return Objects.equals(mType, that.mType);
        }

        @Override
        public int hashCode() {

            return Objects.hash(super.hashCode(), mType);
        }
    }

    /**
     * 方法常量
     */
    public static class ConstMethodRef extends ConstMemberRef {

        private DexType mReturnType;

        private DexTypeList mParameterTypes;

        ConstMethodRef(DexType owner, DexString name, DexType returnType, DexTypeList parameterTypes) {
            super(owner, name);
            this.mReturnType = returnType;
            this.mParameterTypes = parameterTypes;
        }

        public static ConstMethodRef make(DexType owner, DexString name, DexType returnType,
                                          DexTypeList parameterTypes) {
            return new ConstMethodRef(owner, name, returnType, parameterTypes);
        }

        /**
         * 返回类型描述符
         *
         * @return
         */
        public DexType getReturnType() {
            return mReturnType;
        }

        /**
         * 参数类型列表
         *
         * @return
         */
        public DexTypeList getParameterTypes() {
            return mParameterTypes;
        }

        /**
         * Helper方法，获取方法描述符
         *
         * @return
         */
        public String getDesc() {
            StringBuilder dp = new StringBuilder("(");
            if (mParameterTypes != null) {
                for (DexType param : mParameterTypes.types()) {
                    dp.append(param);
                }
            }
            dp.append(")").append(getReturnType());
            return dp.toString();
        }

        public ConstMethodRef withOwner(DexType newOwner) {
            return make(newOwner,
                    mName,
                    mReturnType,
                    mParameterTypes);
        }

        @Override
        public String toString() {
            return toSmaliString();
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write(this.mOwner.toTypeDescriptor());
            writer.write("->");
            writer.write(this.mName.toString());
            writer.write("(");
            if (mParameterTypes != null) {
                mParameterTypes.forEach(t -> writer.write(t.toTypeDescriptor()));
            }
            writer.write(")");
            writer.write(this.mReturnType.toTypeDescriptor());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConstMethodRef)) return false;
            if (!super.equals(o)) return false;
            ConstMethodRef that = (ConstMethodRef) o;
            return Objects.equals(mReturnType, that.mReturnType) &&
                    Objects.equals(mParameterTypes, that.mParameterTypes);
        }

        @Override
        public int hashCode() {

            return Objects.hash(super.hashCode(), mReturnType, mParameterTypes);
        }
    }

    public static class Proto extends DexConst {

        private DexType mReturnType;

        private DexTypeList mParamTypes;

        private Proto(DexType returnType, DexTypeList paraTypes) {
            this.mReturnType = returnType;
            this.mParamTypes = paraTypes;
        }

        public static Proto make(DexType returnType, DexTypeList paraTypes) {
            return new Proto(returnType, paraTypes);
        }

        /**
         * 返回类型描述符
         *
         * @return
         */
        public DexType getReturnType() {
            return mReturnType;
        }

        /**
         * 参数类型列表
         *
         * @return
         */
        public DexTypeList getParameterTypes() {
            return mParamTypes;
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write("(");
            if (mParamTypes != null) {
                mParamTypes.forEach(t -> writer.write(t.toTypeDescriptor()));
            }
            writer.write(")");
            writer.write(mReturnType.toTypeDescriptor());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Proto)) return false;
            Proto proto = (Proto) o;
            return Objects.equals(mReturnType, proto.mReturnType) &&
                    Objects.equals(mParamTypes, proto.mParamTypes);
        }

        @Override
        public int hashCode() {

            return Objects.hash(mReturnType, mParamTypes);
        }
    }

    public static class MethodAndProto extends DexConst {

        private DexConst.ConstMethodRef mMethodRef;

        private Proto mProto;

        private MethodAndProto(DexConst.ConstMethodRef methodRef, Proto proto) {
            this.mMethodRef = methodRef;
            this.mProto = proto;
        }

        public DexConst.ConstMethodRef getMethodRef() {
            return this.mMethodRef;
        }

        public Proto getProto() {
            return this.mProto;
        }

        public static MethodAndProto make(DexConst.ConstMethodRef methodRef,
                                          Proto proto) {
            return new MethodAndProto(methodRef, proto);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodAndProto)) return false;
            MethodAndProto that = (MethodAndProto) o;
            return Objects.equals(mMethodRef, that.mMethodRef) &&
                    Objects.equals(mProto, that.mProto);
        }

        @Override
        public int hashCode() {

            return Objects.hash(mMethodRef, mProto);
        }
    }

    public static class MethodHandle extends DexConst {

        public static final int METHOD_HANDLE_TYPE_STATIC_PUT = 0x00;

        public static final int METHOD_HANDLE_TYPE_STATIC_GET = 0x01;

        public static final int METHOD_HANDLE_TYPE_INSTANCE_PUT = 0x02;

        public static final int METHOD_HANDLE_TYPE_INSTANCE_GET = 0x03;

        public static final int METHOD_HANDLE_TYPE_INVOKE_STATIC = 0x04;

        public static final int METHOD_HANDLE_TYPE_INVOKE_INSTANCE = 0x05;

        public static final int METHOD_HANDLE_TYPE_INVOKE_CONSTRUCTOR = 0x06;

        public static final int METHOD_HANDLE_TYPE_INVOKE_DIRECT = 0x07;

        public static final int METHOD_HANDLE_TYPE_INVOKE_INTERFACE = 0x08;

        private int mHandleType;

        private DexConst.ConstMemberRef mMemberRef;

        private MethodHandle(int handleType, DexConst.ConstMemberRef memberRef) {
            this.mHandleType = handleType;
            this.mMemberRef = memberRef;
        }

        public static MethodHandle make(int handleType, DexConst.ConstMemberRef memberRef) {
            return new MethodHandle(handleType, memberRef);
        }

        public DexConst.ConstMethodRef asMethodRef() {
            return (DexConst.ConstMethodRef)this.mMemberRef;
        }

        public DexConst.ConstFieldRef asFieldRef() {
            return (DexConst.ConstFieldRef)this.mMemberRef;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodHandle)) return false;
            MethodHandle that = (MethodHandle) o;
            return mHandleType == that.mHandleType &&
                    Objects.equals(mMemberRef, that.mMemberRef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mHandleType, mMemberRef);
        }
    }


    public static class EncodedValue extends DexConst {

        /**
         * signed one-byte integer value
         */
        public static final int VALUE_BYTE = 0x00;
        /**
         * signed two-byte integer value, sign-extended
         */
        public static final int VALUE_SHORT = 0x02;
        /**
         * unsigned two-byte integer value, zero-extended
         */
        public static final int VALUE_CHAR = 0x03;
        /**
         * signed four-byte integer value, sign-extended
         */
        public static final int VALUE_INT = 0x04;
        /**
         * signed eight-byte integer value, sign-extended
         */
        public static final int VALUE_LONG = 0x06;
        /**
         * four-byte bit pattern, zero-extended to the right, and interpreted as an IEEE754 32-bit floating point value
         */
        public static final int VALUE_FLOAT = 0x10;
        /**
         * eight-byte bit pattern, zero-extended to the right, and interpreted as an IEEE754 64-bit floating point value
         */
        public static final int VALUE_DOUBLE = 0x11;
        /**
         * unsigned (zero-extended) four-byte integer value, interpreted as an index into the proto_ids section and representing a method type value
         */
        public static final int VALUE_METHOD_TYPE = 0x15;
        /**
         * unsigned (zero-extended) four-byte integer value, interpreted as an index into the method_handles section and representing a method handle value
         */
        public static final int VALUE_METHOD_HANDLE = 0x16;
        /**
         * unsigned (zero-extended) four-byte integer value, interpreted as an index into the string_ids section and representing a string value
         */
        public static final int VALUE_STRING = 0x17;
        /**
         * unsigned (zero-extended) four-byte integer value, interpreted as an index into the type_ids section and representing a reflective type/class value
         */
        public static final int VALUE_TYPE = 0x18;
        /**
         * unsigned (zero-extended) four-byte integer value, interpreted as an index into the field_ids section and representing a reflective field value
         */
        public static final int VALUE_FIELD = 0x19;
        /**
         * unsigned (zero-extended) four-byte integer value, interpreted as an index into the method_ids section and representing a reflective method value
         */
        public static final int VALUE_METHOD = 0x1a;
        /**
         * unsigned (zero-extended) four-byte integer value, interpreted as an index into the field_ids section and representing the value of an enumerated type constant
         */
        public static final int VALUE_ENUM = 0x1b;
        /**
         * an array of values, in the format specified by "encoded_array format" below. The size of the value is implicit in the encoding.
         */
        public static final int VALUE_ARRAY = 0x1c;
        /**
         * a sub-annotation, in the format specified by "encoded_annotation format" below. The size of the value is implicit in the encoding.
         */
        public static final int VALUE_ANNOTATION = 0x1d;
        /**
         * null reference value
         */
        public static final int VALUE_NULL = 0x1e;
        /**
         * 0 for false and 1 for true. The bit is represented in the value_arg.
         */
        public static final int VALUE_BOOLEAN = 0x1f;

        private final Object mValue;

        private final int mType;

        private EncodedValue(int type, Object value) {
            this.mType = type;
            this.mValue = value;
        }

        public int getType() {
            return mType;
        }

        public static EncodedValue makePrimitive(Object primitiveValue) {
            int type;
            if (primitiveValue instanceof Byte) {
                type = VALUE_BYTE;
            } else if (primitiveValue instanceof Short) {
                type = VALUE_SHORT;
            } else if (primitiveValue instanceof Character) {
                type = VALUE_CHAR;
            } else if (primitiveValue instanceof Integer) {
                type = VALUE_INT;
            } else if (primitiveValue instanceof Long) {
                type = VALUE_LONG;
            } else if (primitiveValue instanceof Float) {
                type = VALUE_FLOAT;
            } else if (primitiveValue instanceof Double) {
                type = VALUE_DOUBLE;
            } else if (primitiveValue instanceof Boolean) {
                type = VALUE_BOOLEAN;
            } else {
                throw new IllegalArgumentException("unknown type " + primitiveValue.getClass());
            }
            return new EncodedValue(type, primitiveValue);
        }

        public static EncodedValue makeString(DexString value) {
            return new EncodedValue(VALUE_STRING, value);
        }

        public static EncodedValue makeEnum(EncodedEnum encodedEnum) {
            return new EncodedValue(VALUE_ENUM, encodedEnum);
        }

        public static EncodedValue makeAnnotation(EncodedAnnotation annotation) {
            return new EncodedValue(VALUE_ANNOTATION, annotation);
        }

        public static EncodedValue makeMethod(DexConst.ConstMethodRef method) {
            return new EncodedValue(VALUE_METHOD, method);
        }

        public static EncodedValue makeField(DexConst.ConstFieldRef field) {
            return new EncodedValue(VALUE_FIELD, field);
        }

        public static EncodedValue makeArray(DexConst.EncodedArray array) {
            return new EncodedValue(VALUE_ARRAY, array);
        }

        public static EncodedValue makeType(DexType type) {
            return new EncodedValue(VALUE_TYPE, type);
        }

        public static EncodedValue makeNull() {
            return new EncodedValue(VALUE_NULL, null);
        }

        public EncodedAnnotation asAnnotation() {
            return (EncodedAnnotation) mValue;
        }

        public EncodedArray asArray() {
            return (EncodedArray) mValue;
        }

        public EncodedEnum asEnum() {
            return (EncodedEnum) mValue;
        }

        public Object asPrimitive() {
            return mValue;
        }

        public byte asByte() {
            return (byte) mValue;
        }

        public short asShort() {
            return (short) mValue;
        }

        public char asChar() {
            return (char) mValue;
        }

        public int asInt() {
            return (int) mValue;
        }

        public long asLong() {
            return (long) mValue;
        }

        public float asFloat() {
            return (float) mValue;
        }

        public double asDouble() {
            return (double) mValue;
        }

        public boolean asBoolean() {
            return (boolean) mValue;
        }

        public Proto asMethodType() {
            return (Proto) mValue;
        }

        public MethodHandle asMethodHandle() {
            return (MethodHandle) mValue;
        }

        public DexString asString() {
            return (DexString) mValue;
        }

        public DexType asType() {
            return (DexType) mValue;
        }

        public ConstFieldRef asField() {
            return (ConstFieldRef) mValue;
        }

        public ConstMethodRef asMethod() {
            return (ConstMethodRef) mValue;
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            switch (mType) {
                case DexConst.EncodedValue.VALUE_BYTE: {
                    byte byteValue = asByte();
                    if (byteValue < 0) {
                        writer.write("-0x");
                        writer.printUnsignedLongAsHex(-byteValue);
                        writer.write('t');
                    } else {
                        writer.write("0x");
                        writer.printUnsignedLongAsHex(byteValue);
                        writer.write('t');
                    }
                    break;
                }
                case DexConst.EncodedValue.VALUE_SHORT: {
                    short shortValue = asShort();
                    if (shortValue < 0) {
                        writer.write("-0x");
                        writer.printUnsignedLongAsHex(-shortValue);
                        writer.write('s');
                    } else {
                        writer.write("0x");
                        writer.printUnsignedLongAsHex(shortValue);
                        writer.write('s');
                    }
                    break;
                }
                case DexConst.EncodedValue.VALUE_CHAR: {
                    char charValue = asChar();
                    writer.write('\'');
                    SmaliStringUtils.writeEscapedChar(writer, charValue);
                    writer.write('\'');
                    break;
                }
                case DexConst.EncodedValue.VALUE_INT: {
                    int intValue = asInt();
                    if (intValue < 0) {
                        writer.write("-0x");
                        writer.printUnsignedLongAsHex(-(long)intValue);
                    } else {
                        writer.write("0x");
                        writer.printUnsignedLongAsHex(intValue);
                    }
                    break;
                }
                case DexConst.EncodedValue.VALUE_LONG: {
                    long longValue = asLong();
                    if (longValue < 0) {
                        writer.write("-0x");
                        writer.printUnsignedLongAsHex(-longValue);
                        writer.write('L');
                    } else {
                        writer.write("0x");
                        writer.printUnsignedLongAsHex(longValue);
                        writer.write('L');
                    }
                    break;

                }
                case DexConst.EncodedValue.VALUE_FLOAT: {
                    float floatValue = asFloat();
                    writer.write(Float.toString(floatValue));
                    writer.write('f');
                    break;
                }
                case DexConst.EncodedValue.VALUE_DOUBLE: {
                    double doubleValue = asDouble();
                    writer.write(Double.toString(doubleValue));
                    break;
                }
                case DexConst.EncodedValue.VALUE_BOOLEAN: {
                    boolean boolValue = asBoolean();
                    writer.write(boolValue ? "true" : "false");
                    break;
                }
                case DexConst.EncodedValue.VALUE_METHOD_TYPE:
                case DexConst.EncodedValue.VALUE_METHOD_HANDLE: {
                    // TODO support later
                    break;
                }
                case DexConst.EncodedValue.VALUE_STRING: {
                    writer.write('"');
                    SmaliStringUtils.writeEscapedString(writer, asString().toString());
                    writer.write('"');
                    break;
                }
                case DexConst.EncodedValue.VALUE_TYPE: {
                    DexType typeValue = asType();
                    writer.write(typeValue.toTypeDescriptor());
                    break;
                }
                case DexConst.EncodedValue.VALUE_FIELD: {
                    DexConst.ConstFieldRef fieldRefValue = asField();
                    writer.write(fieldRefValue.toSmaliString());
                    break;
                }
                case DexConst.EncodedValue.VALUE_METHOD: {
                    DexConst.ConstMethodRef methodRefValue = asMethod();
                    writer.write(methodRefValue.toSmaliString());
                    break;
                }
                case DexConst.EncodedValue.VALUE_ENUM: {
                    asEnum().smaliTo(writer);
                    break;
                }
                case DexConst.EncodedValue.VALUE_ARRAY: {
                    EncodedArray encodedArray = asArray();
                    writer.write('{');
                    if (encodedArray.getArrayValue().size() == 0) {
                        writer.write('}');
                        break;
                    }

                    // # write element
                    writer.newLine();
                    writer.indent(4);
                    for (int i = 0; i < encodedArray.getArrayValue().size(); i++) {
                        if (i > 0) {
                            writer.write(",");
                            writer.newLine();
                        }
                        encodedArray.getArrayValue().get(i).smaliTo(writer);
                    }
                    writer.deindent(4);
                    writer.newLine();
                    writer.write("}");
                    break;
                }
                case DexConst.EncodedValue.VALUE_ANNOTATION: {
                    writer.write(".subannotation");
                    EncodedAnnotation encodedAnnotation = asAnnotation();
                    writer.write(encodedAnnotation.getType().toTypeDescriptor());
                    writer.newLine();
                    writer.indent(4);
                    asAnnotation().smaliTo(writer);
                    writer.deindent(4);
                    writer.write(".end subannotation");
                    break;
                }
                case DexConst.EncodedValue.VALUE_NULL: {
                    writer.write("null");
                    break;
                }
            }
        }

    }

    public static class EncodedAnnotation extends DexConst {

        private final DexType mType;

        private List<AnnotationElement> mAnnotationItems;

        private EncodedAnnotation(DexType type, List<AnnotationElement> elements) {
            this.mType = type;
            this.mAnnotationItems = Collections.unmodifiableList(new ArrayList<>(elements));
        }

        private EncodedAnnotation(DexType type) {
            mType = type;
            mAnnotationItems = new ArrayList<>();
            setImmutable(false);
        }

        public DexType getType() {
            return mType;
        }

        public void addAnnotationElement(AnnotationElement element) {
            if (isImmutable()) {
                throw new IllegalStateException("can not add element when immutable");
            }
            mAnnotationItems.add(element);
        }

        public List<AnnotationElement> getAnnotationItems() {
            return mAnnotationItems;
        }

        public static EncodedAnnotation make(DexType type, List<AnnotationElement> elements) {
            return new EncodedAnnotation(type, elements);
        }

        public static EncodedAnnotation makeMutable(DexType type) {
            return new EncodedAnnotation(type);
        }

        @Override
        protected void onImmutable() {
            mAnnotationItems = Collections.unmodifiableList(new ArrayList<>(mAnnotationItems));
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            mAnnotationItems.forEach(a -> {
                writer.newLine();
                a.smaliTo(writer);
            });
        }

    }

    public static class EncodedArray extends DexConst {

        private List<EncodedValue> mArrayValue;

        private EncodedArray(List<EncodedValue> arrayValue) {
            this.mArrayValue = Collections.unmodifiableList(new ArrayList<>(arrayValue));
        }

        private EncodedArray() {
            mArrayValue = new ArrayList<>();
            setImmutable(false);
        }

        public static EncodedArray make(List<EncodedValue> values) {
            return new EncodedArray(values);
        }

        public static EncodedArray makeMutable() {
            return new EncodedArray();
        }

        public List<EncodedValue> getArrayValue() {
            return mArrayValue;
        }

        public void addEncodedValue(EncodedValue value) {
            if (isImmutable()) {
                throw new IllegalStateException("can not add element when immutable");
            }
            mArrayValue.add(value);
        }

        @Override
        protected void onImmutable() {
            mArrayValue = Collections.unmodifiableList(new ArrayList<>(mArrayValue));
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            mArrayValue.forEach(v -> {
                writer.newLine();
                v.smaliTo(writer);
            });
        }

    }

    public static class AnnotationElement extends DexConst {

        private final DexString mName;

        private final EncodedValue mValue;

        private AnnotationElement(DexString name, EncodedValue value) {
            this.mName = name;
            this.mValue = value;
        }

        public static AnnotationElement make(DexString name, EncodedValue value) {
            return new AnnotationElement(name, value);
        }

        public DexString getName() {
            return mName;
        }

        public EncodedValue getValue() {
            return mValue;
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write(mName.toString());
            writer.write(" = ");
            mValue.smaliTo(writer);
        }

    }

    public static class EncodedEnum extends DexConst {

        private DexType mType;

        private DexString mName;

        public DexType getType() {
            return mType;
        }

        public DexString getName() {
            return mName;
        }

        private EncodedEnum(DexType type, DexString name) {
            this.mType = type;
            this.mName = name;
        }

        public static EncodedEnum make(DexType type, DexString name) {
            return new EncodedEnum(type, name);
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write(".enum");
            writer.write(getType().toTypeDescriptor());
            writer.write("->");
            writer.write(getName().toString());
            writer.write(":");
            writer.write(getType().toTypeDescriptor());
        }

    }


    public static class CallSite extends DexConst {

        private MethodHandle mMethodHandle;

        private DexString mMethodName;

        private Proto mMethodType;

        private List<DexConst> mOrderedArguments;

        private CallSite(MethodHandle methodHandle,
                         DexString methodName,
                         Proto methodType,
                         List<DexConst> orderedArguments) {
            this.mMethodHandle = methodHandle;
            this.mMethodName = methodName;
            this.mMethodType = methodType;
            this.mOrderedArguments = Collections.unmodifiableList(new ArrayList<>(orderedArguments));
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            // TODO
        }

        public CallSite make(MethodHandle methodHandle, DexString methodName, Proto methodType,
                             List<DexConst> orderedArguments) {
            return new CallSite(methodHandle, methodName, methodType, orderedArguments);
        }

    }

    public static class ArrayData extends DexConst {

        private int mWidth;

        private int mLength;

        private byte[] mData;

        private ArrayData(int width, int length, byte[] data) {
            this.mWidth = width;
            this.mLength = length;
            this.mData = data;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getLength() {
            return mLength;
        }

        public byte[] getData() {
            return mData;
        }

        public static ArrayData make(int width, int length, byte[] data) {
            return new ArrayData(width, length, data);
        }

        @Override
        public void smaliTo(SmaliWriter writer) {
            writer.write(".array-data");
            writer.write(' ');
            writer.printSignedIntAsDec(mWidth);
            writer.newLine();

            writer.indent(4);

            String suffix = "";
            switch (mWidth) {
                case 1: {
                    suffix = "t";
                    break;
                }
                case 2: {
                    suffix = "s";
                    break;
                }
                default: {
                    break;
                }
            }

            for (int i = 0; i < mLength; i++) {
                long eleValue = arrayDataToLong(mData, mWidth, i);
                if (eleValue < 0) {
                    writer.write("-0x");
                    writer.printUnsignedLongAsHex(-eleValue);
                    if (eleValue < Integer.MIN_VALUE) {
                        writer.write('L');
                    }
                } else {
                    writer.write("0x");
                    writer.printUnsignedLongAsHex(eleValue);
                    if (eleValue > Integer.MAX_VALUE) {
                        writer.write('L');
                    }
                }
                writer.write(suffix);

                writer.newLine();
            }

            writer.deindent(4);

            writer.write(".end array-data");
            writer.newLine();
        }

        private long arrayDataToLong(byte[] arrayData, int width, int elementIdx) {
            if (width > 8) {
                throw new IllegalArgumentException("unexpected width " + width);
            }

            long result = 0L;
            for (int i = 0; i < width; i++) {
                byte v = arrayData[elementIdx * width + i];
                result |= ((v & 0xFF) << (i * 8));
            }
            return result;
        }

    }



}
