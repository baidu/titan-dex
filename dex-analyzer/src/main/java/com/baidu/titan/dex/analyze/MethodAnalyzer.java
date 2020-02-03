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

import com.baidu.titan.dex.DexAccessFlags;
import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexItemFactory;
import com.baidu.titan.dex.DexRegister;
import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.Dop;
import com.baidu.titan.dex.Dops;
import com.baidu.titan.dex.analyze.types.RegType;
import com.baidu.titan.dex.analyze.types.RegTypeCache;
import com.baidu.titan.dex.linker.ClassLinker;
import com.baidu.titan.dex.linker.DexClassLoader;
import com.baidu.titan.dex.node.DexClassNode;
import com.baidu.titan.dex.node.DexCodeNode;
import com.baidu.titan.dex.node.DexFieldNode;
import com.baidu.titan.dex.node.DexMethodNode;
import com.baidu.titan.dex.node.insn.DexConstInsnNode;
import com.baidu.titan.dex.node.insn.DexInsnNode;
import com.baidu.titan.dex.node.insn.DexLabelNode;
import com.baidu.titan.dex.node.insn.DexOpcodeInsnNode;
import com.baidu.titan.dex.node.insn.DexSwitchDataInsnNode;
import com.baidu.titan.dex.node.insn.DexTargetInsnNode;
import com.baidu.titan.dex.node.insn.DexTryCatchNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * 代码指令流图和类型分析器，实现参考了JVM Spec和Android ART虚拟机
 *
 * @author zhangdi07@baidu.com
 * @since 2017/12/14
 */
public class MethodAnalyzer {

    private static final boolean DEBUG = true;

    private RegTypeCache mRegTypeCache;

    private static final int MODE_TRACE_REGS_ALL = 0;

    private static final int MODE_TRACE_REGS_BRANCH = 1;

    private RegisterPc mWorkRegPc;

    private RegisterPc mSavedRegPc;

    private AnalyzeData mAnalyzeData;

    private boolean mNeedPreciseConstants = true;

    private DexItemFactory mDexItemFactory = new DexItemFactory();

    private DexMethodNode mMethodNode;

    private DexClassLoader mClassLoader;

    private ClassLinker mClassLinker;

    private DexCodeNode mCodeNode;

    private RegType mReturnType;

    private List<Integer> mVisitRecorder = new ArrayList<>();

    private boolean mHasAnalyzed = false;

    private RegType mDeclareClassType;

    public static class VerifyError {
        /**
         * VerifyError; hard error that skips compilation.
         */
        public static final int VERIFY_ERROR_BAD_CLASS_HARD = 1;
        /**
         * VerifyError; soft error that verifies again at runtime.
         */
        public static final int VERIFY_ERROR_BAD_CLASS_SOFT = 2;
        /**
         * NoClassDefFoundError.
         */
        public static final int VERIFY_ERROR_NO_CLASS = 4;
        /**
         * NoSuchFieldError.
         */
        public static final int VERIFY_ERROR_NO_FIELD = 8;
        /**
         * NoSuchMethodError.
         */
        public static final int VERIFY_ERROR_NO_METHOD = 16;
        /**
         * IllegalAccessError
         */
        public static final int VERIFY_ERROR_ACCESS_CLASS = 32;
        /**
         * IllegalAccessError
         */
        public static final int VERIFY_ERROR_ACCESS_FIELD = 64;
        /**
         * IllegalAccessError
         */
        public static final int VERIFY_ERROR_ACCESS_METHOD = 128;
        /**
         * IncompatibleClassChangeError
         */
        public static final int VERIFY_ERROR_CLASS_CHANGE = 256;
        /**
         * InstantiationError
         */
        public static final int VERIFY_ERROR_INSTANTIATION = 512;

        /**
         * For opcodes that don't have complete verifier support,  we need a way to continue
         * execution at runtime without attempting to re-verify (since we know it will fail no
         * matter what). Instead, run as the interpreter in a special "do access checks" mode
         * which will perform verifier-like checking on the fly.
         * Skip the verification phase at runtime; force the interpreter to do access checks.
         * (sets a soft fail at compile time).
         */
        public static final int VERIFY_ERROR_FORCE_INTERPRETER = 1024;
        /**
         * Could not guarantee balanced locking. This should be punted to the interpreter with access checks.
         */
        public static final int VERIFY_ERROR_LOCKING = 2048;

    }

    public enum MethodResolutionKind {
        DIRECT_METHOD_RESOLUTION,
        VIRTUAL_METHOD_RESOLUTION,
        INTERFACE_METHOD_RESOLUTION
    }

    public enum MethodType {
        METHOD_UNKNOWN,
        METHOD_DIRECT,      // <init>, private
        METHOD_STATIC,      // static
        METHOD_VIRTUAL,     // virtual
        METHOD_SUPER,       // super
        METHOD_INTERFACE,   // interface
        METHOD_POLYMORPHIC  // polymorphic
    }


    private static class AnalyzeData {

        public int startGuess;

        public int mWorkInsIdx = 0;
    }

    public ClassLinker getClassLinker() {
        return mClassLinker;
    }

    public DexClassLoader getClassLoader() {
        return mClassLoader;
    }

    public RegTypeCache getRegTypeCache() {
        return mRegTypeCache;
    }

    public DexItemFactory getDexItemFactory() {
        return mDexItemFactory;
    }

    public MethodAnalyzer(DexMethodNode methodNode, DexClassLoader classLoader) {
        this.mMethodNode = methodNode;
        this.mClassLoader = classLoader;
        DexCodeNode shrinkedCodeNode = CodeShrinker.shrinkDexCode(methodNode.getCode());
        this.mCodeNode = shrinkedCodeNode;
        mClassLinker = new ClassLinker(getDexItemFactory());
        mRegTypeCache = new RegTypeCache(classLoader);
        mWorkRegPc = new RegisterPc(this, shrinkedCodeNode.getLocalRegCount(),
                shrinkedCodeNode.getParameterRegCount(), mRegTypeCache);
        mSavedRegPc = new RegisterPc(this,
                this.mCodeNode.getLocalRegCount(),
                this.mCodeNode.getParameterRegCount(),
                mRegTypeCache);
    }

    /**
     * 分析 code flow
     *
     * @return
     */
    public boolean analyze() {
        mHasAnalyzed = true;
        mAnalyzeData = new AnalyzeData();
        fillInsnInfoAndassignInsnIdx(mCodeNode);
        scanTryCatchBlocks(mCodeNode);
        scanBranchTarget(mCodeNode);
        return verifyCodeFlow();
    }

    /**
     * 返回分析之后的DexCode
     *
     * @return
     */
    public DexCodeNode getAnalyzedCode() {
        if (!mHasAnalyzed) {
            throw new IllegalStateException("call analyze() first!");
        }
        return mCodeNode;
    }

    /**
     * 清楚ins node存储的extra信息
     */
    public void clear() {
        List<DexInsnNode> insns = this.mCodeNode.getInsns();
        if (insns != null) {
            insns.forEach(i -> i.clearAllExtraInfo());
        }
    }

    /**
     * 分析代码流程图
     *
     * @return
     */
    private boolean verifyCodeFlow() {
        initCodeRegs(MODE_TRACE_REGS_ALL);
        setupParameterRegType();
        return codeFlowVerifyMethod();
    }

    private boolean codeFlowVerifyMethod() {
        DexCodeNode codeNode = mCodeNode;
        List<DexInsnNode> insnNodes = codeNode.getInsns();
        // mark first instruction as changed
        InstructionInfo.infoForIns(insnNodes.get(0)).setChanged(true);

        mAnalyzeData.startGuess = 0;

        while (true) {
            int insnIdx = mAnalyzeData.startGuess;
            for (; insnIdx < insnNodes.size(); insnIdx++) {
                if (InstructionInfo.infoForIns(insnNodes.get(insnIdx)).isChanged()) {
                    break;
                }
            }
            if (insnIdx == insnNodes.size()) {
                if (mAnalyzeData.startGuess != 0) {
                    mAnalyzeData.startGuess = 0;
                    continue;
                } else {
                    break;
                }
            }

            mAnalyzeData.mWorkInsIdx = insnIdx;

            if (InstructionInfo.infoForIns(insnNodes.get(insnIdx)).isBranchTarget()) {
                mWorkRegPc.copyFromLine(InstructionInfo.infoForIns(insnNodes.get(insnIdx)).registerPc);
            }

            mVisitRecorder.add(insnIdx);

            if (!codeFlowVerifyInstruction(mAnalyzeData)) {
                return false;
            }

            InstructionInfo.infoForIns(insnNodes.get(insnIdx)).setVisited(true);
            InstructionInfo.infoForIns(insnNodes.get(insnIdx)).setChanged(false);
        }
        return true;
    }

    private boolean codeFlowVerifyInstruction(AnalyzeData ad) {
        DexMethodNode methodNode = mMethodNode;
        DexCodeNode codeNode = mCodeNode;
        List<DexInsnNode> insnNodes = codeNode.getInsns();
        DexInsnNode insnNode = insnNodes.get(ad.mWorkInsIdx);

        boolean justSetResult = false;
        Dop dop = null;
        InstructionInfo insnsInfo = InstructionInfo.infoForIns(insnNode);

        if (insnNode instanceof DexLabelNode) {
            // skip
        } else if (insnNode instanceof DexOpcodeInsnNode) {
            DexOpcodeInsnNode opcodeInsnNode = (DexOpcodeInsnNode)insnNode;
            DexConstInsnNode constInsnNode = opcodeInsnNode instanceof DexConstInsnNode ?
                    (DexConstInsnNode)opcodeInsnNode : null;

            int opcode = opcodeInsnNode.getOpcode();
            dop = Dops.dopFor(opcode);
            DexRegisterList registerList = opcodeInsnNode.getRegisters();

            if (dop.canThrow() && insnsInfo.isInTry()) {
                mSavedRegPc.copyFromLine(mWorkRegPc);
            }

            switch (opcode) {
                case Dops.NOP: {
                    break;
                }
                case Dops.MOVE:
                case Dops.MOVE_FROM16:
                case Dops.MOVE_16: {
                    mWorkRegPc.copyRegister1(registerList.get(0), registerList.get(1),
                            RegisterPc.TYPE_CATEGORY_1NR);
                    break;
                }
                case Dops.MOVE_WIDE:
                case Dops.MOVE_WIDE_FROM16:
                case Dops.MOVE_WIDE_16: {
                    mWorkRegPc.copyRegister2(registerList.get(0), registerList.get(1));
                    break;
                }
                case Dops.MOVE_OBJECT:
                case Dops.MOVE_OBJECT_FROM16:
                case Dops.MOVE_OBJECT_16: {
                    mWorkRegPc.copyRegister1(registerList.get(0), registerList.get(1),
                            RegisterPc.TYPE_CATEGORY_REF);
                    break;
                }
                case Dops.MOVE_RESULT: {
                    mWorkRegPc.copyResultRegister1(registerList.get(0), false);
                    break;
                }
                case Dops.MOVE_RESULT_WIDE: {
                    mWorkRegPc.copyResultRegister2(registerList.get(0));
                    break;
                }
                case Dops.MOVE_RESULT_OBJECT: {
                    mWorkRegPc.copyResultRegister1(registerList.get(0), true);
                    break;
                }
                case Dops.MOVE_EXCEPTION: {
                    // 按照dalvik vm规范，move_exception 必须是handler中第一个指令

                    DexLabelNode handlerLabel =
                            (DexLabelNode)insnNodes.get(mAnalyzeData.mWorkInsIdx - 1);
                    RegType catchRegType = getCatchExceptionType(handlerLabel);
                    mWorkRegPc.setRegTypeFromDexRegister(registerList.get(0), catchRegType);
                    break;
                }
                case Dops.RETURN_VOID: {
                    if (!methodNode.isInstanceInitMethod() || mWorkRegPc.checkConstructorReturn()) {
                        if (!getMethodReturnType().isConflict()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "return-void not expected");
                        }
                    }
                    break;
                }
                case Dops.RETURN: {
                    if (!methodNode.isInstanceInitMethod() || mWorkRegPc.checkConstructorReturn()) {
                        RegType returnType = getMethodReturnType();
                        if (!returnType.isCategory1Types()) {
                            fail(0, "unexpected non-category 1 return type " + returnType.dump());
                        } else {
                            // Compilers may generate synthetic functions that write byte values
                            // into boolean fields.
                            // Also, it may use integer values for boolean, byte, short,
                            // and character return types.

                            RegType srcType = mWorkRegPc.getRegTypeFromDexRegister(
                                    registerList.get(0));
                            boolean useSrc = ((returnType.isBoolean() && srcType.isByte()) ||
                                    ((returnType.isBoolean() || returnType.isByte() ||
                                            returnType.isShort() || returnType.isChar()) &&
                                            srcType.isInteger()));
                            boolean sucess = mWorkRegPc.verifyRegisterType(registerList.get(0),
                                    useSrc ? srcType : returnType);

                        }

                    }
                    break;
                }

                case Dops.RETURN_WIDE: {
                    if (!methodNode.isInstanceInitMethod() || mWorkRegPc.checkConstructorReturn()) {
                        // check method signature
                        RegType returnType = getMethodReturnType();
                        if (!returnType.isCategory2Types()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "return-wide not expected");
                        } else {
                            boolean sucess = mWorkRegPc.verifyRegisterType(registerList.get(0),
                                    returnType);
                        }

                    }
                    break;
                }
                case Dops.RETURN_OBJECT: {
                    if (!methodNode.isInstanceInitMethod() || mWorkRegPc.checkConstructorReturn()) {
                        RegType returnType = getMethodReturnType();
                        if (!returnType.isReferenceTypes()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "return-object not expected");
                        } else {
                            RegType regType =
                                    mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                            // Disallow returning undefined, conflict & uninitialized values
                            // and verify that the reference in vAA is an instance of the
                            // "return_type."

                            if (regType.isUndefined()) {
                                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                        "returning undefined register");
                            } else if (regType.isConflict()) {
                                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                        "returning register with conflict");
                            } else if (regType.isUninitializedTypes()) {
                                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                        "returning uninitialized object " + regType.dump());
                            } else if (!regType.isReferenceTypes()) {
                                // We really do expect a reference here.
                                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                        "return-object returns a non-reference type "
                                                + regType.dump());
                            } else if (!returnType.isAssignableFrom(this, regType)) {
                                if (regType.isUnresolvedTypes() || returnType.isUnresolvedTypes()) {
                                    fail(VerifyError.VERIFY_ERROR_NO_CLASS,
                                            "can't resolve returned type " + returnType.dump() +
                                    "' or '" + regType + "'");
                                } else {
                                    AtomicBoolean softError = new AtomicBoolean(false);
                                    // Check whether arrays are involved. They will show a valid
                                    // class status, even if their components are erroneous.
                                    if (regType.isArrayTypes() && returnType.isArrayTypes()) {
                                        returnType.canAssignArray(regType, mRegTypeCache,
                                                getClassLoader(), this, softError);
                                        if (softError.get()) {
                                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_SOFT,
                                                    "array with erroneous component type: " +
                                            regType + " vs " + returnType);
                                        }
                                    }
                                    if (!softError.get()) {
                                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                                "returning '" + regType +
                                                        "', but expected from declaration '" +
                                                        returnType + "'");
                                    }
                                }
                            }

                        }
                    }
                    break;
                }
                case Dops.CONST_4:
                case Dops.CONST_16:
                case Dops.CONST:
                case Dops.CONST_HIGH16: {
                    DexConst.LiteralBits32 value = (DexConst.LiteralBits32)constInsnNode.getConst();
                    mWorkRegPc.setRegTypeFromDexRegister(registerList.get(0),
                            determineCat1Constant(value.getIntBits(), mNeedPreciseConstants));
                    break;
                }
                case Dops.CONST_WIDE_16:
                case Dops.CONST_WIDE_32:
                case Dops.CONST_WIDE:
                case Dops.CONST_WIDE_HIGH16: {
                    DexConst.LiteralBits64 value = (DexConst.LiteralBits64)constInsnNode.getConst();
                    RegType loType = mRegTypeCache.fromCat2ConstLo(value.getLongBits(), true);
                    RegType hiType = mRegTypeCache.fromCat2ConstHi(value.getLongBits(), true);
                    mWorkRegPc.setRegTypeWideFromDexRegister(registerList.get(0), loType, hiType);
                    break;
                }
                case Dops.CONST_STRING:
                case Dops.CONST_STRING_JUMBO: {
                    RegType stringType = mRegTypeCache.javaLangString();
                    mWorkRegPc.setRegTypeFromDexRegister(registerList.get(0), stringType);
                    break;
                }
                case Dops.CONST_CLASS: {
                    RegType classType = mRegTypeCache.javaLangClass();
                    mWorkRegPc.setRegTypeFromDexRegister(registerList.get(0), classType);
                    break;
                }
                case Dops.MONITOR_ENTER: {
                    break;
                }
                case Dops.MONITOR_EXIT: {
                    break;
                }
                case Dops.CHECK_CAST: {
                    // orgType : 寄存器当前的类型
                    RegType orgType = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                    // castType : 要转换的类型
                    RegType castType = resolveClassAndCheckAccess(
                            ((DexConst.ConstType)constInsnNode.getConst()).value());

                    if (!castType.isNonZeroReferenceTypes()) {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                "check-cast on unexpected class " + castType);
                    } else if (!orgType.isReferenceTypes()) {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                "check-cast on non-reference in " + registerList.get(0));
                    } else if (orgType.isUninitializedTypes()) {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                "check-cast on uninitialized reference in "
                                        + registerList.get(0));
                    } else {
                        mWorkRegPc.setRegTypeFromDexRegister(registerList.get(0), castType);
                    }
                    break;
                }
                case Dops.INSTANCE_OF: {
                    // testType : 要转换的类型
                    RegType testType = resolveClassAndCheckAccess(
                            ((DexConst.ConstType)constInsnNode.getConst()).value());

                    RegType orgType = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(1));

                    if (!testType.isNonZeroReferenceTypes()) {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                "instance-of on unexpected class " + testType);
                    } else if (!orgType.isReferenceTypes()) {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                "instance-of on non-reference in " + registerList.get(0));
                    } else if (orgType.isUninitializedTypes()) {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                "instance-of on uninitialized reference in "
                                        + registerList.get(0));
                    } else {
                        mWorkRegPc.setRegTypeFromDexRegister(registerList.get(0),
                                mRegTypeCache.booleanType());
                    }
                    break;
                }
                case Dops.ARRAY_LENGTH: {
                    RegType arrayType = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(1));
                    if (arrayType.isReferenceTypes()) {
                        if (!arrayType.isArrayTypes() && !arrayType.isZeroOrNull()) {
                            // ie not an array or null
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "array-length on non-array " + arrayType);
                        } else {
                            mWorkRegPc.setRegTypeFromDexRegister(registerList.get(0),
                                    mRegTypeCache.integerType());
                        }
                    } else {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                "array-length on non-array " + arrayType);
                    }
                    break;
                }
                case Dops.NEW_INSTANCE: {
                    RegType newInstanceType = resolveClassAndCheckAccess(
                            ((DexConst.ConstType) constInsnNode.getConst()).value());

                    if (newInstanceType.isConflict()) {
                        // TODO
                        break;
                    } else {
                        if (!newInstanceType.isInstantiableTypes()) {
                            fail(VerifyError.VERIFY_ERROR_INSTANTIATION,
                                    "new-instance on primitive, interface or abstract class "
                                            + newInstanceType);
                        }
                        RegType unitType = mRegTypeCache.uninitialized(newInstanceType, ad.mWorkInsIdx);
                        mWorkRegPc.markUninitRefsAdInvalid(unitType);
                        mWorkRegPc.setRegTypeFromDexRegister(registerList.get(0), unitType);
                    }
                    break;
                }
                case Dops.NEW_ARRAY: {
                    RegType newArrayType = resolveClassAndCheckAccess(
                            ((DexConst.ConstType)constInsnNode.getConst()).value());
                    if (newArrayType.isConflict()) {

                    } else {
                        if (!newArrayType.isArrayTypes()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "new-array on on-array class " + newArrayType);
                        } else {
                            // array size
                            mWorkRegPc.verifyRegisterType(registerList.get(1),
                                    mRegTypeCache.integerType());
                            RegType preciseType = mRegTypeCache.fromUninitialized(newArrayType);
                            mWorkRegPc.setRegTypeFromDexRegister(registerList.get(0), preciseType);

                        }
                    }
                    break;
                }
                case Dops.FILLED_NEW_ARRAY:
                case Dops.FILLED_NEW_ARRAY_RANGE: {
                    RegType newArrayType = resolveClassAndCheckAccess(
                            ((DexConst.ConstType)constInsnNode.getConst()).value());
                    if (newArrayType.isConflict()) {

                    } else {
                        if (!newArrayType.isArrayTypes()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "new-array on on-array class " + newArrayType);
                        } else {
                            RegType expectedType = mRegTypeCache.getComponentType(newArrayType,
                                    mClassLoader);
                            for (int i = 0; i < registerList.count(); i++) {
                                DexRegister fillReg = registerList.get(i);
                                if (!mWorkRegPc.verifyRegisterType(fillReg, expectedType)) {
                                    mWorkRegPc.setResultType1(mRegTypeCache.conflictType());
                                    break;
                                }
                            }
                            RegType preciseType = mRegTypeCache.fromUninitialized(newArrayType);
                            mWorkRegPc.setResultType1(preciseType);
                        }
                        justSetResult = true;
                    }
                    break;
                }
                case Dops.CMPL_FLOAT:
                case Dops.CMPG_FLOAT: {
                    if (!mWorkRegPc.verifyRegisterType(
                            registerList.get(1), mRegTypeCache.floatType())) {
                        break;
                    }
                    if (!mWorkRegPc.verifyRegisterType(
                            registerList.get(2), mRegTypeCache.floatType())) {
                        break;
                    }
                    mWorkRegPc.setRegTypeFromDexRegister(
                            registerList.get(0), mRegTypeCache.integerType());
                    break;
                }
                case Dops.CMPL_DOUBLE:
                case Dops.CMPG_DOUBLE: {
                    if (!mWorkRegPc.verifyRegisterTypeWide(registerList.get(1),
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType())) {
                        break;
                    }
                    if (!mWorkRegPc.verifyRegisterTypeWide(registerList.get(2),
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType())) {
                        break;
                    }
                    mWorkRegPc.setRegTypeFromDexRegister(
                            registerList.get(0), mRegTypeCache.integerType());
                    break;
                }

                case Dops.CMP_LONG: {
                    if (!mWorkRegPc.verifyRegisterTypeWide(registerList.get(1),
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType())) {
                        break;
                    }
                    if (!mWorkRegPc.verifyRegisterTypeWide(registerList.get(2),
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType())) {
                        break;
                    }
                    mWorkRegPc.setRegTypeFromDexRegister(
                            registerList.get(0), mRegTypeCache.integerType());
                    break;
                }
                case Dops.THROW: {
                    RegType throwType = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                    if (!mRegTypeCache.javaLangThrowable().isAssignableFrom(this, throwType)) {
                        if (throwType.isUninitializedTypes()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "thrown exception not initialized");
                        } else if (!throwType.isReferenceTypes()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "thrown value of non-reference type " + throwType.dump());
                        } else {
                            fail(throwType.isUnresolvedTypes() ? VerifyError.VERIFY_ERROR_NO_CLASS :
                                    VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "thrown class " + throwType.dump() +
                                            " not instanceof Throwable");
                        }
                    }
                    break;
                }
                case Dops.GOTO:
                case Dops.GOTO_16:
                case Dops.GOTO_32: {
                    break;
                }
                case Dops.PACKED_SWITCH:
                case Dops.SPARSE_SWITCH: {
                    mWorkRegPc.verifyRegisterType(
                            registerList.get(0), mRegTypeCache.integerType());
                    break;
                }
                case Dops.FILL_ARRAY_DATA: {
                    RegType arrayType = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                    if (!arrayType.isZero()) {
                        if (!arrayType.isArrayTypes()) {
                            fail(0, "invalid fill-array-data with array type " + arrayType);
                        } else {
                            // TODO
                        }
                    }
                    justSetResult = true;
                    break;
                }
                case Dops.IF_EQ:
                case Dops.IF_NE: {
                    RegType regType1 = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                    RegType regType2 = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(1));
                    boolean mismatch = false;
                    if (regType1.isZero()) { // if zero, then integral or reference expected
                        mismatch = !regType2.isReferenceTypes() && !regType2.isIntegralTypes();
                    } else if (regType1.isReferenceTypes()) { // both references?
                        mismatch = !regType2.isReferenceTypes();
                    } else { // both integral?
                        mismatch = !regType1.isIntegralTypes() || !regType2.isIntegralTypes();
                    }
                    if (mismatch) {
                        fail(0, "args to if-eq/if-ne (" + regType1 + "," + regType2 +
                                ") must both be references or integral");
                    }
                    break;
                }
                case Dops.IF_LT:
                case Dops.IF_GE:
                case Dops.IF_GT:
                case Dops.IF_LE: {
                    RegType regType1 = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                    RegType regType2 = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(1));
                    if (!regType1.isIntegralTypes() || !regType2.isIntegralTypes()) {
                        fail(0, "args to 'if' (" + regType1 + "," + regType2 +
                                ") must be integral");
                    }
                    break;
                }
                case Dops.IF_EQZ:
                case Dops.IF_NEZ: {
                    RegType testType = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                    if (!testType.isReferenceTypes() && !testType.isIntegralTypes()) {
                        fail(0, "type " + testType + " unexpected as arg to if-eqz/if-nez");
                    }


                    break;
                }
                case Dops.IF_LTZ:
                case Dops.IF_GEZ:
                case Dops.IF_GTZ:
                case Dops.IF_LEZ: {
                    RegType testType = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                    if (!testType.isIntegralTypes()) {
                        fail(0, "type " + testType +
                                " unexpected as arg to if-ltz/if-gez/if-gtz/if-lez");
                    }
                    break;
                }
                case Dops.AGET_BOOLEAN: {
                    verifyAGet(registerList, mRegTypeCache.booleanType(), true);
                    break;
                }
                case Dops.AGET_BYTE: {
                    verifyAGet(registerList, mRegTypeCache.byteType(), true);
                    break;
                }
                case Dops.AGET_CHAR: {
                    verifyAGet(registerList, mRegTypeCache.charType(), true);
                    break;
                }
                case Dops.AGET_SHORT: {
                    verifyAGet(registerList, mRegTypeCache.shortType(), true);
                    break;
                }
                case Dops.AGET: {
                    verifyAGet(registerList, mRegTypeCache.integerType(), true);
                    break;
                }
                case Dops.AGET_WIDE: {
                    verifyAGet(registerList, mRegTypeCache.longLoType(), true);
                    break;
                }
                case Dops.AGET_OBJECT: {
                    verifyAGet(registerList, mRegTypeCache.javaLangObject(), false);
                    break;
                }
                case Dops.APUT_BOOLEAN: {
                    verifyAPut(registerList, mRegTypeCache.booleanType(), true);
                    break;
                }
                case Dops.APUT_BYTE: {
                    verifyAPut(registerList, mRegTypeCache.byteType(), true);
                    break;
                }
                case Dops.APUT_CHAR: {
                    verifyAPut(registerList, mRegTypeCache.charType(), true);
                    break;
                }
                case Dops.APUT_SHORT: {
                    verifyAPut(registerList, mRegTypeCache.shortType(), true);
                    break;
                }
                case Dops.APUT: {
                    verifyAPut(registerList, mRegTypeCache.integerType(), true);
                    break;
                }
                case Dops.APUT_WIDE: {
                    verifyAPut(registerList, mRegTypeCache.longLoType(), true);
                    break;
                }
                case Dops.APUT_OBJECT: {
                    verifyAPut(registerList, mRegTypeCache.javaLangObject(), false);
                    break;
                }

                case Dops.IGET_BOOLEAN: {
                    verifyIGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.booleanType());
                    break;
                }
                case Dops.IGET_BYTE: {
                    verifyIGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.byteType());
                    break;
                }
                case Dops.IGET_CHAR: {
                    verifyIGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.charType());
                    break;
                }
                case Dops.IGET_SHORT: {
                    verifyIGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.shortType());
                    break;
                }
                case Dops.IGET: {
                    verifyIGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.integerType());
                    break;
                }
                case Dops.IGET_WIDE: {
                    verifyIGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.longLoType());
                    break;
                }
                case Dops.IGET_OBJECT: {
                    verifyIGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            false, mRegTypeCache.javaLangObject());
                    break;
                }
                case Dops.IPUT_BOOLEAN: {
                    verifyIPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.booleanType());
                    break;
                }
                case Dops.IPUT_BYTE: {
                    verifyIPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.byteType());
                    break;
                }
                case Dops.IPUT_CHAR: {
                    verifyIPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.charType());
                    break;
                }
                case Dops.IPUT_SHORT: {
                    verifyIPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.shortType());
                    break;
                }
                case Dops.IPUT: {
                    verifyIPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.integerType());
                    break;
                }
                case Dops.IPUT_WIDE: {
                    verifyIPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.longLoType());
                    break;
                }
                case Dops.IPUT_OBJECT: {
                    verifyIPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            false, mRegTypeCache.javaLangObject());
                    break;
                }

                case Dops.SGET_BOOLEAN: {
                    verifySGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.booleanType());
                    break;
                }
                case Dops.SGET_BYTE: {
                    verifySGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.byteType());
                    break;
                }
                case Dops.SGET_CHAR: {
                    verifySGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.charType());
                    break;
                }
                case Dops.SGET_SHORT: {
                    verifySGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.shortType());
                    break;
                }
                case Dops.SGET: {
                    verifySGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.integerType());
                    break;
                }
                case Dops.SGET_WIDE: {
                    verifySGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.longLoType());
                    break;
                }
                case Dops.SGET_OBJECT: {
                    verifySGet(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            false, mRegTypeCache.javaLangObject());
                    break;
                }

                case Dops.SPUT_BOOLEAN: {
                    verifySPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.booleanType());
                    break;
                }
                case Dops.SPUT_BYTE: {
                    verifySPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.byteType());
                    break;
                }
                case Dops.SPUT_CHAR: {
                    verifySPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.charType());
                    break;
                }
                case Dops.SPUT_SHORT: {
                    verifySPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.shortType());
                    break;
                }
                case Dops.SPUT: {
                    verifySPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.integerType());
                    break;
                }
                case Dops.SPUT_WIDE: {
                    verifySPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            true, mRegTypeCache.longLoType());
                    break;
                }
                case Dops.SPUT_OBJECT: {
                    verifySPut(registerList, (DexConst.ConstFieldRef)constInsnNode.getConst(),
                            false, mRegTypeCache.javaLangObject());
                    break;
                }

                case Dops.INVOKE_VIRTUAL:
                case Dops.INVOKE_VIRTUAL_RANGE:
                case Dops.INVOKE_SUPER:
                case Dops.INVOKE_SUPER_RANGE: {
                    DexConst.ConstMethodRef methodRef =
                            (DexConst.ConstMethodRef)constInsnNode.getConst();
                    boolean isSuper = opcode ==
                            Dops.INVOKE_SUPER || opcode == Dops.INVOKE_SUPER_RANGE;
                    DexMethodNode calledMethod = verifyInvocationArgs(methodRef,
                            isSuper ? MethodType.METHOD_SUPER : MethodType.METHOD_VIRTUAL,
                            registerList);
                    RegType returnType = null;
                    if (calledMethod != null) {
                        DexClassNode returnTypeClass =
                                mClassLoader.findClass(calledMethod.returnType);
                        if (returnTypeClass != null) {
                            returnType = fromClass(calledMethod.returnType, returnTypeClass,
                                    false);
                        }

                    }
                    if (returnType == null) {
                        returnType = mRegTypeCache.fromDescriptor(mClassLoader,
                                methodRef.getReturnType(), false);
                    }
                    if (!returnType.isLowHalf()) {
                        mWorkRegPc.setResultType1(returnType);
                    } else {
                        mWorkRegPc.setResultTypeWide(returnType, returnType.highHalf(mRegTypeCache));
                    }
                    justSetResult = true;
                    break;
                }
                case Dops.INVOKE_DIRECT:
                case Dops.INVOKE_DIRECT_RANGE: {
                    DexConst.ConstMethodRef methodRef =
                            (DexConst.ConstMethodRef)constInsnNode.getConst();
                    DexMethodNode calledMethod = verifyInvocationArgs(methodRef,
                            MethodType.METHOD_DIRECT, registerList);
                    boolean isConstructor = methodRef.getName().equals("<init>");
                    RegType returnType = null;
                    if (calledMethod != null) {
                        DexClassNode returnTypeClass =
                                mClassLoader.findClass(calledMethod.returnType);
                        if (returnTypeClass!= null) {
                            returnType = fromClass(returnTypeClass.type, returnTypeClass, false);
                        }
                    }

                    if (returnType == null) {
                        returnType = mRegTypeCache.fromDescriptor(mClassLoader,
                                methodRef.getReturnType(), false);
                    }

                    if (isConstructor) {
                        RegType thisType = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                        if (thisType.isConflict()) {
                            break;
                        }
                        if (thisType.isZero()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "unable to initialize null ref");
                            break;
                        }

                        if (!thisType.isUninitializedTypes()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "Expected initialization on uninitialized reference" + thisType);
                            break;
                        }
                        mWorkRegPc.markRefsAsInitialized(thisType);
                    }

                    if (!returnType.isLowHalf()) {
                        mWorkRegPc.setResultType1(returnType);
                    } else {
                        mWorkRegPc.setResultTypeWide(returnType, returnType.highHalf(mRegTypeCache));
                    }
                    justSetResult = true;
                    break;
                }

                case Dops.INVOKE_STATIC:
                case Dops.INVOKE_STATIC_RANGE: {
                    DexConst.ConstMethodRef methodRef =
                            (DexConst.ConstMethodRef)constInsnNode.getConst();
                    DexMethodNode calledMethod = verifyInvocationArgs(methodRef,
                            MethodType.METHOD_STATIC, registerList);
                    RegType returnType = null;
                    if (calledMethod != null) {
                        DexClassNode returnTypeClass =
                                mClassLoader.findClass(calledMethod.returnType);
                        if (returnTypeClass != null) {
                            returnType = mRegTypeCache.fromClass(returnTypeClass.type,
                                    returnTypeClass, false);
                        }
                    }
                    if (returnType == null) {
                        returnType = mRegTypeCache.fromDescriptor(mClassLoader,
                                methodRef.getReturnType(), false);
                    }
                    if (!returnType.isLowHalf()) {
                        mWorkRegPc.setResultType1(returnType);
                    } else {
                        mWorkRegPc.setResultTypeWide(returnType, returnType.highHalf(mRegTypeCache));
                    }
                    justSetResult = true;
                    break;
                }

                case Dops.INVOKE_INTERFACE:
                case Dops.INVOKE_INTERFACE_RANGE: {
                    DexConst.ConstMethodRef methodRef =
                            (DexConst.ConstMethodRef)constInsnNode.getConst();
                    DexMethodNode absMethod = verifyInvocationArgs(methodRef,
                            MethodType.METHOD_INTERFACE, registerList);
                    if (absMethod != null) {
                        DexClassNode calledInterface = mClassLoader.findClass(absMethod.owner);
                        if (!calledInterface.isInterface() && !calledInterface.type.equals
                                (mDexItemFactory.objectClass.type)) {
                            fail(VerifyError.VERIFY_ERROR_CLASS_CHANGE,
                                    "expected interface class in invoke-interface " + absMethod);
                            break;
                        }
                    }
                    RegType thisType = mWorkRegPc.getRegTypeFromDexRegister(registerList.get(0));
                    if (thisType.isZero()) {

                    } else {
                        if (thisType.isUninitializedTypes()) {
                            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                                    "interface call on uninitialized object " + thisType);
                            break;
                        }
                    }
                    RegType returnType = mRegTypeCache.fromDescriptor(mClassLoader,
                            methodRef.getReturnType(), false);
                    if (!returnType.isLowHalf()) {
                        mWorkRegPc.setResultType1(returnType);
                    } else {
                        mWorkRegPc.setResultTypeWide(returnType, returnType.highHalf(mRegTypeCache));
                    }
                    justSetResult = true;
                    break;
                }

                case Dops.NEG_INT:
                case Dops.NOT_INT: {
                    mWorkRegPc.checkUnaryOp(registerList, mRegTypeCache.integerType(),
                            mRegTypeCache.integerType());
                    break;
                }
                case Dops.NEG_LONG:
                case Dops.NOT_LONG: {
                    mWorkRegPc.checkUnaryOpWide(registerList, mRegTypeCache.longLoType(),
                            mRegTypeCache.longHiType(), mRegTypeCache.longLoType(),
                            mRegTypeCache.longHiType());
                    break;
                }
                case Dops.NEG_FLOAT: {
                    mWorkRegPc.checkUnaryOp(registerList, mRegTypeCache.floatType(),
                            mRegTypeCache.floatType());
                    break;
                }
                case Dops.NEG_DOUBLE: {
                    mWorkRegPc.checkUnaryOpWide(registerList, mRegTypeCache.doubleLoType(),
                            mRegTypeCache.doubleHiType(), mRegTypeCache.doubleLoType(),
                            mRegTypeCache.doubleHiType());
                    break;
                }
                case Dops.INT_TO_LONG: {
                    mWorkRegPc.checkUnaryOpToWide(registerList, mRegTypeCache.longLoType(),
                            mRegTypeCache.longHiType(), mRegTypeCache.integerType());
                    break;
                }
                case Dops.INT_TO_FLOAT: {
                    mWorkRegPc.checkUnaryOp(registerList, mRegTypeCache.floatType(),
                            mRegTypeCache.integerType());
                    break;
                }
                case Dops.INT_TO_DOUBLE: {
                    mWorkRegPc.checkUnaryOpToWide(registerList, mRegTypeCache.doubleLoType(),
                            mRegTypeCache.doubleHiType(), mRegTypeCache.integerType());
                    break;
                }
                case Dops.LONG_TO_INT: {
                    mWorkRegPc.checkUnaryOpFromWide(registerList, mRegTypeCache.integerType(),
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType());
                    break;
                }
                case Dops.LONG_TO_FLOAT: {
                    mWorkRegPc.checkUnaryOpFromWide(registerList, mRegTypeCache.floatType(),
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType());
                    break;
                }
                case Dops.LONG_TO_DOUBLE: {
                    mWorkRegPc.checkUnaryOpWide(registerList,
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType(),
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType());
                    break;
                }
                case Dops.FLOAT_TO_INT: {
                    mWorkRegPc.checkUnaryOp(registerList, mRegTypeCache.integerType(),
                            mRegTypeCache.floatType());
                    break;
                }
                case Dops.FLOAT_TO_LONG: {
                    mWorkRegPc.checkUnaryOpToWide(registerList, mRegTypeCache.longLoType(),
                            mRegTypeCache.longHiType(), mRegTypeCache.floatType());
                    break;
                }
                case Dops.FLOAT_TO_DOUBLE: {
                    mWorkRegPc.checkUnaryOpToWide(registerList, mRegTypeCache.doubleLoType(),
                            mRegTypeCache.doubleHiType(), mRegTypeCache.floatType());
                    break;
                }
                case Dops.DOUBLE_TO_INT: {
                    mWorkRegPc.checkUnaryOpFromWide(registerList, mRegTypeCache.integerType(),
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType());
                    break;
                }
                case Dops.DOUBLE_TO_LONG: {
                    mWorkRegPc.checkUnaryOpWide(registerList,
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType(),
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType());
                    break;
                }
                case Dops.DOUBLE_TO_FLOAT: {
                    mWorkRegPc.checkUnaryOpFromWide(registerList, mRegTypeCache.floatType(),
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType());
                    break;
                }
                case Dops.INT_TO_BYTE: {
                    mWorkRegPc.checkUnaryOp(registerList, mRegTypeCache.byteType(),
                            mRegTypeCache.integerType());
                    break;
                }
                case Dops.INT_TO_CHAR: {
                    mWorkRegPc.checkUnaryOp(registerList, mRegTypeCache.charType(),
                            mRegTypeCache.integerType());
                    break;
                }
                case Dops.INT_TO_SHORT: {
                    mWorkRegPc.checkUnaryOp(registerList, mRegTypeCache.shortType(),
                            mRegTypeCache.integerType());
                    break;
                }
                case Dops.ADD_INT:
                case Dops.SUB_INT:
                case Dops.MUL_INT:
                case Dops.REM_INT:
                case Dops.DIV_INT:
                case Dops.SHL_INT:
                case Dops.SHR_INT:
                case Dops.USHR_INT: {
                    mWorkRegPc.checkBinaryOp(registerList,
                            mRegTypeCache.integerType(), mRegTypeCache.integerType(),
                            mRegTypeCache.integerType(), false);
                    break;
                }
                case Dops.AND_INT:
                case Dops.OR_INT:
                case Dops.XOR_INT: {
                    mWorkRegPc.checkBinaryOp(registerList,
                            mRegTypeCache.integerType(), mRegTypeCache.integerType(),
                            mRegTypeCache.integerType(), true);
                    break;
                }
                case Dops.ADD_LONG:
                case Dops.SUB_LONG:
                case Dops.MUL_LONG:
                case Dops.DIV_LONG:
                case Dops.REM_LONG:
                case Dops.AND_LONG:
                case Dops.OR_LONG:
                case Dops.XOR_LONG: {
                    mWorkRegPc.checkBinaryOpWide(registerList,
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType(),
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType(),
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType());
                    break;
                }
                case Dops.SHL_LONG:
                case Dops.SHR_LONG:
                case Dops.USHR_LONG: {
                    /* shift distance is Int, making these different from other binary operations */
                    mWorkRegPc.checkBinaryOpWideShift(registerList, mRegTypeCache.longLoType(),
                            mRegTypeCache.longHiType(), mRegTypeCache.integerType());
                    break;
                }
                case Dops.ADD_FLOAT:
                case Dops.SUB_FLOAT:
                case Dops.MUL_FLOAT:
                case Dops.DIV_FLOAT:
                case Dops.REM_FLOAT: {
                    mWorkRegPc.checkBinaryOp(registerList,
                            mRegTypeCache.floatType(), mRegTypeCache.floatType(),
                            mRegTypeCache.floatType(), false);
                    break;
                }
                case Dops.ADD_DOUBLE:
                case Dops.SUB_DOUBLE:
                case Dops.MUL_DOUBLE:
                case Dops.DIV_DOUBLE:
                case Dops.REM_DOUBLE: {
                    mWorkRegPc.checkBinaryOpWide(registerList,
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType(),
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType(),
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType());
                    break;
                }
                case Dops.ADD_INT_2ADDR:
                case Dops.SUB_INT_2ADDR:
                case Dops.MUL_INT_2ADDR:
                case Dops.REM_INT_2ADDR:
                case Dops.SHL_INT_2ADDR:
                case Dops.SHR_INT_2ADDR:
                case Dops.USHR_INT_2ADDR: {
                    mWorkRegPc.checkBinaryOp2addr(registerList,
                            mRegTypeCache.integerType(), mRegTypeCache.integerType(),
                            mRegTypeCache.integerType(), false);
                    break;
                }
                case Dops.AND_INT_2ADDR:
                case Dops.OR_INT_2ADDR:
                case Dops.XOR_INT_2ADDR: {
                    mWorkRegPc.checkBinaryOp2addr(registerList,
                            mRegTypeCache.integerType(), mRegTypeCache.integerType(),
                            mRegTypeCache.integerType(), true);
                    break;
                }
                case Dops.DIV_INT_2ADDR: {
                    mWorkRegPc.checkBinaryOp2addr(registerList,
                            mRegTypeCache.integerType(), mRegTypeCache.integerType(),
                            mRegTypeCache.integerType(), false);
                    break;
                }
                case Dops.ADD_LONG_2ADDR:
                case Dops.SUB_LONG_2ADDR:
                case Dops.MUL_LONG_2ADDR:
                case Dops.DIV_LONG_2ADDR:
                case Dops.REM_LONG_2ADDR:
                case Dops.AND_LONG_2ADDR:
                case Dops.OR_LONG_2ADDR:
                case Dops.XOR_LONG_2ADDR: {
                    mWorkRegPc.checkBinaryOp2addrWide(registerList,
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType(),
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType(),
                            mRegTypeCache.longLoType(), mRegTypeCache.longHiType());
                    break;
                }
                case Dops.SHL_LONG_2ADDR:
                case Dops.SHR_LONG_2ADDR:
                case Dops.USHR_LONG_2ADDR: {
                    mWorkRegPc.checkBinaryOp2addrWideShift(registerList, mRegTypeCache.longLoType(),
                            mRegTypeCache.longHiType(), mRegTypeCache.integerType());
                    break;
                }
                case Dops.ADD_FLOAT_2ADDR:
                case Dops.SUB_FLOAT_2ADDR:
                case Dops.MUL_FLOAT_2ADDR:
                case Dops.DIV_FLOAT_2ADDR:
                case Dops.REM_FLOAT_2ADDR: {
                    mWorkRegPc.checkBinaryOp2addr(registerList,
                            mRegTypeCache.floatType(), mRegTypeCache.floatType(),
                            mRegTypeCache.floatType(), false);
                    break;
                }
                case Dops.ADD_DOUBLE_2ADDR:
                case Dops.SUB_DOUBLE_2ADDR:
                case Dops.MUL_DOUBLE_2ADDR:
                case Dops.DIV_DOUBLE_2ADDR:
                case Dops.REM_DOUBLE_2ADDR: {
                    mWorkRegPc.checkBinaryOp2addrWide(registerList,
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType(),
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType(),
                            mRegTypeCache.doubleLoType(), mRegTypeCache.doubleHiType());
                    break;
                }
                case Dops.ADD_INT_LIT16:
                    // TODO
                case Dops.RSUB_INT:
                case Dops.MUL_INT_LIT16:
                case Dops.DIV_INT_LIT16:
                case Dops.REM_INT_LIT16: {
                    mWorkRegPc.checkLiteralOp(registerList, mRegTypeCache.integerType(),
                            mRegTypeCache.integerType(), false, true,
                            (DexConst.LiteralBits)constInsnNode.getConst());
                    break;
                }
                case Dops.AND_INT_LIT16:
                case Dops.OR_INT_LIT16:
                case Dops.XOR_INT_LIT16: {
                    mWorkRegPc.checkLiteralOp(registerList, mRegTypeCache.integerType(),
                            mRegTypeCache.integerType(), true, true,
                            (DexConst.LiteralBits)constInsnNode.getConst());
                    break;
                }
                case Dops.ADD_INT_LIT8:
                case Dops.RSUB_INT_LIT8:
                case Dops.MUL_INT_LIT8:
                case Dops.DIV_INT_LIT8:
                case Dops.REM_INT_LIT8:
                case Dops.SHL_INT_LIT8:
                case Dops.SHR_INT_LIT8:
                case Dops.USHR_INT_LIT8: {
                    mWorkRegPc.checkLiteralOp(registerList, mRegTypeCache.integerType(),
                            mRegTypeCache.integerType(), false, false,
                            (DexConst.LiteralBits)constInsnNode.getConst());
                    break;
                }
                case Dops.AND_INT_LIT8:
                case Dops.OR_INT_LIT8:
                case Dops.XOR_INT_LIT8: {
                    mWorkRegPc.checkLiteralOp(registerList, mRegTypeCache.integerType(),
                            mRegTypeCache.integerType(), true, false,
                            (DexConst.LiteralBits)constInsnNode.getConst());
                    break;
                }
                default: {
                    throw new IllegalStateException("unknown opcode " + dop.toString());
                }
            }

        }

        if (dop != null && !justSetResult) {
            mWorkRegPc.setResultTypeToUnknown();
        }


        if (dop != null && dop.canBranch()) {
            DexLabelNode target = ((DexTargetInsnNode)insnNode).getTarget();
            if (!updateRegisters(target, mWorkRegPc, false)) {
                return false;
            }
        }

        if (dop != null && dop.canSwitch()) {
            DexLabelNode[] switchLabels = ((DexSwitchDataInsnNode)insnNode).getCasesLabel();
            for (DexLabelNode switchLabel : switchLabels) {
                if (!updateRegisters(switchLabel, mWorkRegPc, false)) {
                    return false;
                }
            }
        }

        if (dop != null && dop.canThrow() && insnsInfo.isInTry()) {
            DexTryCatchNode tryCatchNode = findTryCatchNode(codeNode.getTryCatches(), insnNode);
            if (tryCatchNode.getHandlers() != null) {
                for (DexLabelNode handlerLabel : tryCatchNode.getHandlers()) {
                    if (!updateRegisters(handlerLabel, mSavedRegPc, false)) {
                        return false;
                    }
                }
            }
            if (tryCatchNode.getCatchAllHandler() != null) {
                if (!updateRegisters(tryCatchNode.getCatchAllHandler(), mSavedRegPc, false)) {
                    return false;
                }
            }
        }

        if ((dop == null || dop.canContinue()) &&
                mAnalyzeData.mWorkInsIdx + 1 < codeNode.getInsns().size()) {
            DexInsnNode nextIns = codeNode.getInsns().get(mAnalyzeData.mWorkInsIdx + 1);
            RegisterPc nextRegisterPc = InstructionInfo.infoForIns(nextIns).registerPc;
            if (nextRegisterPc != null) {
                if (!updateRegisters(nextIns, mWorkRegPc, true)) {
                    return false;
                }
            } else {
                InstructionInfo.infoForIns(nextIns).setChanged(true);
            }
        }

        if (dop == null || dop.canContinue()) {
            mAnalyzeData.startGuess = mAnalyzeData.mWorkInsIdx + 1;
        } else if (dop != null && dop.canBranch()) {
            mAnalyzeData.startGuess = InstructionInfo.infoForIns(
                    ((DexTargetInsnNode)insnNode).getTarget()).idx;
        }

        return true;
    }

    /**
     *
     * 找到指定指令所对应的try catch node
     *
     * @param tries
     * @param insnNode
     * @return
     */
    private DexTryCatchNode findTryCatchNode(List<DexTryCatchNode> tries, DexInsnNode insnNode) {
        for (DexTryCatchNode tryCatchNode : tries) {
            int startIdx = InstructionInfo.infoForIns(tryCatchNode.getStart()).idx;
            int endIdx = InstructionInfo.infoForIns(tryCatchNode.getEnd()).idx;
            int curIdx = InstructionInfo.infoForIns(insnNode).idx;
            if (curIdx >= startIdx && curIdx <= endIdx) {
                return tryCatchNode;
            }
        }
        return null;
    }


    private boolean updateRegisters(DexInsnNode nextInsn, RegisterPc mergePc,
                                    boolean updateMergeLine) {
        boolean changed = true;
        InstructionInfo nextInsnsInfo = InstructionInfo.infoForIns(nextInsn);
        RegisterPc targetPc = nextInsnsInfo.registerPc;
        if (!nextInsnsInfo.isVisitedOrChanged()) {
            targetPc.copyFromLine(mergePc);
        } else {
            changed = targetPc.mergeRegisters(mergePc, this);

            if (DEBUG && changed) {
                info("verifier", "Merging at ");
            }
            if (updateMergeLine && changed) {
                mergePc.copyFromLine(targetPc);
            }
        }
        if (changed) {
            nextInsnsInfo.setChanged(true);
        }
        return true;
    }

    private RegType determineCat1Constant(int value, boolean precise) {
        if (precise) {
            return mRegTypeCache.fromCat1Const(value, true);
        } else {
            if (value < Short.MIN_VALUE) {
                return mRegTypeCache.intConstant();
            } else if (value < Byte.MIN_VALUE) {
                return mRegTypeCache.shortConstant();
            } else if (value < 0) {
                return mRegTypeCache.byteConstant();
            } else if (value == 0) {
                return mRegTypeCache.zero();
            } else if (value == 1) {
                return mRegTypeCache.one();
            } else if (value <= Byte.MAX_VALUE) {
                return mRegTypeCache.posByteConstant();
            } else if (value <= Short.MAX_VALUE) {
                return mRegTypeCache.posShortConstant();
            } else if (value < Character.MAX_VALUE) {
                return mRegTypeCache.charConstant();
            } else {
                return mRegTypeCache.intConstant();
            }


        }
    }

    /**
     *
     * 返回当前指令对应的catch(s)的Exception type，如果，有多个catch item，则会返回公共最近父类型
     *
     * @return
     */
    private RegType getCatchExceptionType(DexLabelNode handlerLabel) {
        DexCodeNode codeNode = mCodeNode;
        List<DexInsnNode> insnNodes = codeNode.getInsns();

        RegType commonSuper = null;

        for (DexTryCatchNode tryCatch : codeNode.getTryCatches()) {
            if (tryCatch.getCatchAllHandler() == handlerLabel) {
                commonSuper = mRegTypeCache.javaLangThrowable();
            } else if (tryCatch.getHandlers() != null) {
                for (int i = 0; i < tryCatch.getHandlers().length; i++) {
                    DexLabelNode handlerNode = tryCatch.getHandlers()[i];
                    if (handlerNode == handlerLabel) {
                        RegType exceptionType =
                                resolveClassAndCheckAccess(tryCatch.getTypes().getType(i));
                        if (!mRegTypeCache.javaLangThrowable().isAssignableFrom(this,
                                exceptionType)) {
                            if (exceptionType.isUnresolvedTypes()) {
                                return exceptionType;
                            } else {
                                return mRegTypeCache.conflictType();
                            }
                        } else if (commonSuper == null) {
                            commonSuper = exceptionType;
                        } else if (commonSuper.equals(exceptionType)) {
                            // do noting
                        } else {
                            // TODO
                            commonSuper = commonSuper.merge(exceptionType, this, mRegTypeCache);
                        }
                    }
                }
            }
        }
        if (commonSuper == null) {
            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_SOFT, "unable to find exception handler");
            return mRegTypeCache.conflictType();
        }

        return commonSuper;
    }

    /**
     *
     * 验证aget指令，并设置目的寄存器数据类型
     *
     * @param regList
     * @param insnType
     * @param isPrimitive
     */
    private void verifyAGet(DexRegisterList regList, RegType insnType, boolean isPrimitive) {
        RegType indexType = mWorkRegPc.getRegTypeFromDexRegister(regList.get(2));
        RegType arrayType = mWorkRegPc.getRegTypeFromDexRegister(regList.get(1));
        if (!indexType.isArrayIndexTypes()) {
            fail(0, "Invalid reg type for array index (" + indexType + ")");
        } else {
            if (arrayType.isZero()) {
                if (!isPrimitive || insnType.isCategory1Types()) {
                    mWorkRegPc.setRegTypeFromDexRegister(regList.get(0), mRegTypeCache.zero());
                } else { // category 2

                    mWorkRegPc.setRegTypeWideFromDexRegister(regList.get(0),
                            mRegTypeCache.fromCat2ConstLo(0, false),
                            mRegTypeCache.fromCat2ConstHi(0, false));
                }
            } else if (!arrayType.isArrayTypes()) {
                fail(0, "not array type " + arrayType + " with aget");
            } else {
                RegType componentType = mRegTypeCache.getComponentType(arrayType, mClassLoader);
                if (!componentType.isReferenceTypes() && !isPrimitive) {
                    fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "primitive array type " +
                    arrayType + " source for aget-object");
                } else if (componentType.isNonZeroReferenceTypes() && isPrimitive) {
                    fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "reference array type "
                            + arrayType + " source for category 1 aget");
                } else if (isPrimitive && !insnType.equals(componentType) &&
                        !((insnType.isInteger() && componentType.isFloat()) ||
                        (insnType.isLong() && componentType.isDouble()))) {
                    fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "array type " + arrayType +
                    " incompatible with aget of type " + insnType);
                } else {
                    if (!componentType.isLowHalf()) {
                        mWorkRegPc.setRegTypeFromDexRegister(regList.get(0), componentType);
                    } else {
                        mWorkRegPc.setRegTypeWideFromDexRegister(regList.get(0), componentType,
                                componentType.highHalf(mRegTypeCache));
                    }
                }

            }
        }

    }

    /**
     *
     * 验证aput指令
     *
     * @param regList
     * @param insnType
     * @param isPrimitive
     */
    private void verifyAPut(DexRegisterList regList,
                            RegType insnType,
                            boolean isPrimitive) {

        RegType indexType = mWorkRegPc.getRegTypeFromDexRegister(regList.get(2));
        RegType arrayType = mWorkRegPc.getRegTypeFromDexRegister(regList.get(1));
        if (!indexType.isArrayIndexTypes()) {
            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                    "Invalid reg type for arry index (" + indexType + ")");
        } else {
            if (arrayType.isZero()) {
                // TODO
            } else if (!arrayType.isArrayTypes()){
                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                        "not array type " + arrayType + " with aput");
            } else {
                RegType componentType = mRegTypeCache.getComponentType(arrayType, mClassLoader);
                if (isPrimitive) {
                    verifyPrimitivePut(componentType, insnType, regList.get(0));
                } else {
                    if (!componentType.isReferenceTypes()) {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "primitive array type " +
                        arrayType + " source for aput-object");
                    } else {
                        mWorkRegPc.verifyRegisterType(regList.get(0), insnType);
                        System.identityHashCode(this);
                    }
                }
            }

        }
    }

    /**
     *
     * 获取实例字段信息
     *
     * @param objType
     * @param fieldRef
     * @return
     */
    private DexFieldNode getInstanceField(RegType objType, DexConst.ConstFieldRef fieldRef) {
        RegType classType = resolveClassAndCheckAccess(fieldRef.getOwner());
        if (classType.isConflict()) {

            return null;
        }
        if (classType.isUnresolvedTypes()) {
            return null;
        }

        DexFieldNode dfn = mClassLinker.resolveFieldJLS(mClassLoader, fieldRef);
        if (dfn == null) {
            return null;
        } else if (objType.isZero()) {
            return dfn;
        } else if (!objType.isReferenceTypes()) {
            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                    "instance field access on object that has non-reference type " + objType);
            return null;
        } else {
            DexClassNode fieldClass = mClassLoader.findClass(dfn.owner);
            RegType fieldType = fromClass(dfn.owner, fieldClass, false);
            if (objType.isUninitializedTypes()) {
                if (!objType.isUninitializedThisReference() ||
                        !(mMethodNode.isInstanceInitMethod() || mMethodNode.isStaticInitMethod()) ||
                        !fieldType.getDexType().equals(mMethodNode.owner)) {
                    fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "cannot access instance field "
                            + dfn + " of a not fully initialized object within the context of " +
                            mMethodNode);
                    return null;
                }
                return dfn;
            } else if (!classType.isAssignableFrom(this, objType)) {
                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "cannot access instance field " +
                    fieldType + " from object of type " + objType);
                return null;
            } else {
                return dfn;
            }
        }
    }

    /**
     *
     * 获取静态字段信息
     *
     * @param fieldRef
     * @return
     */
    private DexFieldNode getStaticField(DexConst.ConstFieldRef fieldRef) {
        RegType classType = resolveClassAndCheckAccess(fieldRef.getOwner());
        if (classType.isConflict()) {
            return null;
        }
        if (classType.isUnresolvedTypes()) {
            return null;
        }
        DexFieldNode fieldNode = mClassLinker.resolveFieldJLS(mClassLoader, fieldRef);
        if (fieldNode == null) {

            return null;
        } else {

        }

        return fieldNode;
    }

    /**
     *
     * 验证iget指令
     *
     * @param regList
     * @param fieldRef
     * @param isPrimitive
     * @param insnType
     */
    private void verifyIGet(DexRegisterList regList, DexConst.ConstFieldRef fieldRef,
                            boolean isPrimitive, RegType insnType) {
        RegType objectType = mWorkRegPc.getRegTypeFromDexRegister(regList.get(1));
        DexFieldNode fieldNode = getInstanceField(objectType, fieldRef);

        RegType fieldType = null;
        if (fieldNode != null) {
            DexClassNode fieldClass = mClassLoader.findClass(fieldNode.type);
            if (fieldClass != null) {
                fieldType = fromClass(fieldClass.type, fieldClass, false);
            }
        } else {
            System.err.println("verify iget, cannot find fieldNode for " + fieldRef);
        }

        if (fieldType == null) {
            fieldType = mRegTypeCache.fromDescriptor(mClassLoader, fieldRef.getType(), false);
        }

        if (isPrimitive) {
            if (fieldType.equals(insnType) ||
                    (fieldType.isFloat() && insnType.isInteger()) ||
                    (fieldType.isDouble() && insnType.isLong())) {
                // expected that read is of the correct primitive type or that int reads are reading
                // floats or long reads are reading doubles
            } else {
                // This is a global failure rather than a class change failure as the instructions and
                // the descriptors for the type should have been consistent within the same file at
                // compile time

                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                        "expected field " + fieldNode + " to be of type " +
                                insnType + " but found type " + fieldType + " in get");
                return;
            }
        } else {
            if (!insnType.isAssignableFrom(this, fieldType)) {
                int error = fieldType.isReferenceTypes() ?
                        VerifyError.VERIFY_ERROR_BAD_CLASS_SOFT:
                        VerifyError.VERIFY_ERROR_BAD_CLASS_HARD;
                fail(error, "expected field " + fieldNode +
                        " to be compatible with type '" + insnType +
                        "' but found type '" + fieldType + "' in get-object");

                if (error != VerifyError.VERIFY_ERROR_BAD_CLASS_HARD) {
                    mWorkRegPc.setRegTypeFromDexRegister(regList.get(0),
                            mRegTypeCache.conflictType());
                }

                return;
            }
        }

        if (!fieldType.isLowHalf()) {
            mWorkRegPc.setRegTypeFromDexRegister(regList.get(0), fieldType);
        } else {
            mWorkRegPc.setRegTypeWideFromDexRegister(
                    regList.get(0), fieldType, fieldType.highHalf(mRegTypeCache));
        }


    }

    private void verifyIPut(DexRegisterList regList, DexConst.ConstFieldRef fieldRef,
                            boolean isPrimitive, RegType insnType) {
        RegType objectType = mWorkRegPc.getRegTypeFromDexRegister(regList.get(1));

        boolean shouldAdjust = objectType.isUninitializedThisReference();
        RegType adjustedType = shouldAdjust ?
                mRegTypeCache.fromUninitialized(objectType) : objectType;

        DexFieldNode fieldNode = getInstanceField(adjustedType, fieldRef);

        RegType fieldType = null;
        if (fieldNode != null) {
            if (fieldNode.accessFlags.containsOneOf(DexAccessFlags.ACC_FINAL)
                    && !fieldNode.owner.equals(mMethodNode.owner)) {
                fail(VerifyError.VERIFY_ERROR_ACCESS_FIELD, "" +
                        "cannot modify final field " + fieldNode +
                        " from other class " + mMethodNode.owner);
            }

            DexClassNode fieldClass = mClassLoader.findClass(fieldNode.type);
            if (fieldClass != null) {
                fieldType = fromClass(fieldClass.type, fieldClass, false);
            }
        } else {
            System.out.println("iput, cannot find fieldNode for " + fieldRef);
        }

        if (fieldType == null) {
            fieldType = mRegTypeCache.fromDescriptor(mClassLoader, fieldRef.getType(), false);
        }

        if (isPrimitive) {
            verifyPrimitivePut(fieldType, insnType, regList.get(0));
        } else {
            if (!insnType.isAssignableFrom(this, fieldType)) {
                int error = fieldType.isReferenceTypes() ?
                        VerifyError.VERIFY_ERROR_BAD_CLASS_SOFT:
                        VerifyError.VERIFY_ERROR_BAD_CLASS_HARD;
                fail(error, "expected field " + fieldNode +
                        " to be compatible with type '" + fieldType +
                        "' in put-object");
                return;
            }
            mWorkRegPc.verifyRegisterType(regList.get(0), fieldType);
        }


    }

    private void verifySGet(DexRegisterList regList, DexConst.ConstFieldRef fieldRef,
                            boolean isPrimitive, RegType insnType) {
        DexFieldNode fieldNode = getStaticField(fieldRef);


        RegType fieldType = null;
        if (fieldNode != null) {
            DexClassNode fieldClass = mClassLoader.findClass(fieldNode.type);
            if (fieldClass != null) {
                fieldType = fromClass(fieldClass.type, fieldClass, false);
            }
        }

        if (fieldType == null) {
            fieldType = mRegTypeCache.fromDescriptor(mClassLoader, fieldRef.getType(), false);
        }

        if (isPrimitive) {
            if (fieldType.equals(insnType) ||
                    (fieldType.isFloat() && insnType.isInteger()) ||
                    (fieldType.isDouble() && insnType.isLong())) {
                // verify success
            } else {
                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                        "expected field " + fieldNode + " to be of type " +
                                insnType + " but found type " + fieldType + " in get");
            }
        } else {
            if (!insnType.isAssignableFrom(this, fieldType)) {
                int error = fieldType.isReferenceTypes() ?
                        VerifyError.VERIFY_ERROR_BAD_CLASS_SOFT:
                        VerifyError.VERIFY_ERROR_BAD_CLASS_HARD;

                fail(error, "expected field " + fieldNode +
                        " to be compatible with type '" + insnType +
                        "' but found type '" + fieldType +
                        "' in get-object");

                if (error != VerifyError.VERIFY_ERROR_BAD_CLASS_HARD) {
                    mWorkRegPc.setRegTypeFromDexRegister(regList.get(0),
                            mRegTypeCache.conflictType());
                }
                return;
            }
        }

        if (!fieldType.isLowHalf()) {
            mWorkRegPc.setRegTypeFromDexRegister(regList.get(0), fieldType);
        } else {
            mWorkRegPc.setRegTypeWideFromDexRegister(
                    regList.get(0), fieldType, fieldType.highHalf(mRegTypeCache));
        }

    }

    private void verifySPut(DexRegisterList regList, DexConst.ConstFieldRef fieldRef,
                            boolean isPrimitive, RegType insnType) {
        DexFieldNode fieldNode = getStaticField(fieldRef);

        RegType fieldType = null;

        if (fieldNode != null) {
            DexClassNode fieldClass = mClassLoader.findClass(fieldNode.type);
            if (fieldClass != null) {
                fieldType = fromClass(fieldClass.type, fieldClass, false);
            }
        }

        if (fieldType == null) {
            fieldType = mRegTypeCache.fromDescriptor(mClassLoader, fieldRef.getType(), false);
        }

        if (isPrimitive) {
            verifyPrimitivePut(fieldType, insnType, regList.get(0));
        } else {
            if (!insnType.isAssignableFrom(this, fieldType)) {
                // TODO
                fail(0, "");
                return;
            }
        }
        mWorkRegPc.verifyRegisterType(regList.get(0), fieldType);

    }

    private void verifyPrimitivePut(RegType targetType, RegType insnType, DexRegister reg) {
        RegType valueType = mWorkRegPc.getRegTypeFromDexRegister(reg);

        boolean instructionCompatible = false;
        boolean valueCompatible = false;

        if (targetType.isIntegralTypes()) {
            instructionCompatible = targetType.equals(insnType);
            valueCompatible = valueType.isIntegralTypes();
        } else if (targetType.isFloat()) {
            // no put-float, so expect put-int
            instructionCompatible = insnType.isInteger();
            valueCompatible = valueType.isFloatTypes();
        } else if (targetType.isLong()) {
            instructionCompatible = insnType.isLong();
            // TODO
            valueCompatible = true;
        } else if (targetType.isDouble()) {
            instructionCompatible = insnType.isLong();
            // TODO
            valueCompatible = true;
        } else {
            instructionCompatible = false;
            valueCompatible = false;
        }
        if (!instructionCompatible) {
            fail(VerifyError.VERIFY_ERROR_ACCESS_FIELD,
                    "pub insn has type " + insnType +
                            " but expected type " + targetType);
            return;
        }
        if (!valueCompatible) {
            fail(VerifyError.VERIFY_ERROR_ACCESS_FIELD,
                    "unexpected value in " + reg +
                            " but expected type " + targetType);
            return;
        }
    }

    private DexMethodNode verifyInvocationArgs(DexConst.ConstMethodRef methodRef,
                                               MethodType methodType,
                                               DexRegisterList regs) {
        DexMethodNode resMethod = resolveMethodAndCheckAccess(methodRef, methodType);
        if (resMethod == null) {
            return null;
        }

        if (methodType == MethodType.METHOD_SUPER) {
            // TODO
            RegType referenceType = mRegTypeCache.fromDescriptor(mClassLoader,
                    methodRef.getOwner(), false);
            if (referenceType.isUnresolvedTypes()) {
                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_SOFT,
                        "Unable to find referenced class from invoke-super");
                return null;
            }

        }

        return verifyInvocationArgsFromIterator(methodType, resMethod, methodRef, regs);
    }

    private DexMethodNode verifyInvocationArgsFromIterator(MethodType methodType,
                                                           DexMethodNode resMethod,
                                                           DexConst.ConstMethodRef methodRef,
                                                           DexRegisterList regs) {
        int nextRegIdx = 0;
        if (methodType != MethodType.METHOD_STATIC) {
            RegType actualArgType = mWorkRegPc.getRegTypeFromDexRegister(regs.get(nextRegIdx++));
            if (actualArgType.isUndefined() || actualArgType.isConflict() ||
                    !actualArgType.isReferenceTypes()) {
                fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "");
                return null;
            }
            boolean isInit = false;
            if (actualArgType.isUninitializedTypes()) {
                if (resMethod != null) {
                    if (!resMethod.isInstanceInitMethod()) {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "'this' arg must " +
                                "initialized");
                        return null;
                    }
                } else {
                    if (!methodRef.getName().equals("<init>")) {
                        fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD, "'this' arg must " +
                                "initialized");
                        return null;
                    }
                }
                isInit = true;
            }

            RegType adjustedType = isInit ? mRegTypeCache.fromUninitialized(actualArgType) :
                    actualArgType;

            if (methodType == MethodType.METHOD_INTERFACE && !adjustedType.isZero()) {
                RegType resMethodClass = mRegTypeCache.fromDescriptor(mClassLoader, methodRef
                        .getOwner(), false);

                if (!resMethodClass.isAssignableFrom(this, adjustedType)) {
                    fail(resMethodClass.isUnresolvedTypes() ?
                            VerifyError.VERIFY_ERROR_NO_CLASS :
                            VerifyError.VERIFY_ERROR_BAD_CLASS_SOFT,
                            "'this' argument ' " + actualArgType +
                                    " ' not instance of '" + resMethodClass);
                    return null;
                }
            }
        }

        DexTypeList paraTypes = methodRef.getParameterTypes();
        for (int i = 0; i < paraTypes.count(); i++) {
            DexType curParaType = paraTypes.getType(i);
            RegType curParaRegType = mRegTypeCache.
                    fromDescriptor(mClassLoader, curParaType, false);
            if (curParaRegType.isIntegralTypes()) {
                RegType srcType = mWorkRegPc.getRegTypeFromDexRegister(regs.get(nextRegIdx));
                if (!srcType.isIntegralTypes()) {
                    fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                            " register " + regs.get(nextRegIdx) + " has type " + srcType +
                    " but expected " + curParaRegType);
                    return null;
                }
            } else {
                if (!mWorkRegPc.verifyRegisterType(regs.get(nextRegIdx), curParaRegType)) {
                    return null;
                } else if (curParaRegType.isLongOrDoubleTypes()) {

                }
            }
            nextRegIdx++;
        }

        return resMethod;
    }

    /**
     *
     * 扫描所有可能会抛出异常并且在try_start和try_end范围内的指令，并设置对应标志位。
     *
     * @param codeNode
     */
    private void scanTryCatchBlocks(DexCodeNode codeNode) {
        List<DexInsnNode> insns = codeNode.getInsns();
        List<DexTryCatchNode> tries = codeNode.getTryCatches();
        tries.forEach(t -> {
            DexLabelNode startLabel = t.getStart();
            DexLabelNode endLabel = t.getEnd();
            InstructionInfo startLabelInfo = InstructionInfo.infoForIns(startLabel);
            InstructionInfo endLabelInfo = InstructionInfo.infoForIns(endLabel);
            int startIdx = startLabelInfo.idx;
            int endIdx = endLabelInfo.idx;
            for (int i = startIdx; i < endIdx; i++) {
                DexInsnNode insnNode = insns.get(i);
                if (insnNode instanceof DexOpcodeInsnNode) {
                    InstructionInfo.infoForIns(insnNode).setInTry(true);
                }
            }

            if (t.getHandlers() != null) {
                for (DexLabelNode handler : t.getHandlers()) {
                    InstructionInfo.infoForIns(handler).setBranchTarget(true);
                }
            }

            if (t.getCatchAllHandler() != null) {
                InstructionInfo.infoForIns(t.getCatchAllHandler()).setBranchTarget(true);
            }

        });
    }

    /**
     *
     * 找到所有跳转分支起始点，并设置标志位。
     *
     * @param codeNode
     */
    private void scanBranchTarget(DexCodeNode codeNode) {
        List<DexInsnNode> insnNodes = codeNode.getInsns();
        InstructionInfo.infoForIns(insnNodes.get(0)).setBranchTarget(true);
        for (int i = 0; i < insnNodes.size(); i++) {
            DexInsnNode insnNode = insnNodes.get(i);
            if (insnNode instanceof DexTargetInsnNode) {
                DexLabelNode targetLabelNode = ((DexTargetInsnNode)insnNode).getTarget();
                InstructionInfo.infoForIns(targetLabelNode).setBranchTarget(true);
            } else if (insnNode instanceof DexSwitchDataInsnNode) {
                DexSwitchDataInsnNode switchInsNode = (DexSwitchDataInsnNode)insnNode;
                for (DexLabelNode caseLabel : switchInsNode.getCasesLabel()) {
                    InstructionInfo.infoForIns(caseLabel).setBranchTarget(true);
                }
            }
        }
    }

    /**
     *
     * 设置InsnInfo相关信息
     *
     * @param codeNode
     */
    private void fillInsnInfoAndassignInsnIdx(DexCodeNode codeNode) {
           List<DexInsnNode> insns = codeNode.getInsns();
        for (int i = 0; i < insns.size(); i++) {
            InstructionInfo insnsInfo = new InstructionInfo();
            insnsInfo.idx = i;
            insnsInfo.attachIns(insns.get(i));
        }
    }

    private RegType resolveClassAndCheckAccess(DexType type) {
        DexClassNode dcn = mClassLoader.findClass(type);
        RegType result = null;
        if (dcn != null) {
            boolean precise = false;
            result = mRegTypeCache.findClassNode(dcn, precise);
                    //cannotBeAssignedFromOtherTypes(dcn);
            if (result == null) {
                result = mRegTypeCache.insertClass(dcn, precise);
            }
        } else {
            result = mRegTypeCache.fromDescriptor(mClassLoader, type, false);
        }
        if (result.isConflict()) {
            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_SOFT, "accessing broken descriptor " + type
                    + " in ");
            return result;
        }
        if (result.isNonZeroReferenceTypes() && !result.isUnresolvedTypes()) {
            // TODO
        }
        return result;
    }

    private DexMethodNode resolveMethodAndCheckAccess(DexConst.ConstMethodRef methodRef,
                                                      MethodType methodType) {
        RegType classType = resolveClassAndCheckAccess(methodRef.getOwner());
        if (classType.isConflict()) {
            return null;
        }
        if (classType.isUnresolvedTypes()) {
            return null;
        }

        DexClassNode classNode = classType.getClassNode();
        RegType referrerType = getDeclaringClass();
        MethodResolutionKind resKind = getMethodResolutionKind(methodType, classNode.isInterface());
        DexMethodNode resMethod = null;
        if (resKind == MethodResolutionKind.DIRECT_METHOD_RESOLUTION) {
            resMethod = mClassLinker.findDirectMethod(classNode, methodRef.getParameterTypes(),
                    methodRef.getReturnType(), methodRef.getName());
        } else if (resKind == MethodResolutionKind.VIRTUAL_METHOD_RESOLUTION) {
            resMethod = mClassLinker.findVirtualMethod(classNode, methodRef.getParameterTypes(),
                    methodRef.getReturnType(), methodRef.getName());
        } else {
            resMethod = mClassLinker.findInterfaceMethod(classNode, methodRef.getParameterTypes(),
                    methodRef.getReturnType(), methodRef.getName());
        }

        if (resMethod == null) {
            // TODO
            System.err.println("resolve method, cannot find resMethod = " + methodRef
                    + " called from " + mMethodNode.toString());
            return null;
        }

        if (resMethod.isInstanceInitMethod() && methodType != MethodType.METHOD_DIRECT) {
            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                    "rejecting non-direct call to constructor " + resMethod);
            return null;
        }
        if (resMethod.isStaticInitMethod()) {
            fail(VerifyError.VERIFY_ERROR_BAD_CLASS_HARD,
                    "rejecting call to class initializer " + resMethod);
            return null;
        }

        if (classNode.isInterface()) {
            if (methodType != MethodType.METHOD_INTERFACE &&
                    methodType != MethodType.METHOD_STATIC &&
                    /**((dex_file_->GetVersion() < DexFile::kDefaultMethodsVersion) ||
                            method_type != METHOD_DIRECT) && */
                    methodType != MethodType.METHOD_SUPER) {
                fail(VerifyError.VERIFY_ERROR_CLASS_CHANGE, "non-interface method " + resMethod +
                        " is in an interface class " + classNode);
                return null;
            }
        } else {
            if (methodType == MethodType.METHOD_INTERFACE) {
                fail(VerifyError.VERIFY_ERROR_CLASS_CHANGE, "interface method " + resMethod +
                    " is in a non-interface class " + classNode);
                return null;
            }
        }

        return resMethod;
    }

    public static MethodResolutionKind getMethodResolutionKind(MethodType methodType,
                                                               boolean isInterface) {
        if (methodType == MethodType.METHOD_DIRECT || methodType == MethodType.METHOD_STATIC) {
            return MethodResolutionKind.DIRECT_METHOD_RESOLUTION;
        } else if (methodType == MethodType.METHOD_INTERFACE) {
            return MethodResolutionKind.INTERFACE_METHOD_RESOLUTION;
        } else if (methodType == MethodType.METHOD_SUPER && isInterface) {
            return MethodResolutionKind.INTERFACE_METHOD_RESOLUTION;
        } else {
            return MethodResolutionKind.VIRTUAL_METHOD_RESOLUTION;
        }
    }
    private RegType getDeclaringClass() {
        if (mDeclareClassType == null) {
            mDeclareClassType =
                    mRegTypeCache.fromDescriptor(mClassLoader, mMethodNode.owner, false);
        }
        return mDeclareClassType;
    }

    private boolean cannotBeAssignedFromOtherTypes(DexClassNode dcn) {
        if (dcn.type.toTypeDescriptor().charAt(0) == '[') {
            return dcn.accessFlags.containsOneOf(DexAccessFlags.ACC_FINAL);
        }
        return false;
    }

    private void initCodeRegs(int mode) {
        DexCodeNode codeNode = mCodeNode;
        List<DexInsnNode> insns = codeNode.getInsns();
        insns.forEach(insnNode -> {
            InstructionInfo insnsInfo = InstructionInfo.infoForIns(insnNode);
            boolean interesting = false;
            switch (mode) {
                case MODE_TRACE_REGS_ALL: {
                    interesting = true;
                    break;
                }
                case MODE_TRACE_REGS_BRANCH: {
                    interesting = insnsInfo.isBranchTarget();
                    break;
                }
                default: {
                    break;
                }
            }
            if (interesting) {
                insnsInfo.registerPc = new RegisterPc(this,
                        codeNode.getLocalRegCount(),
                        codeNode.getParameterRegCount(), mRegTypeCache);
            }
        });
    }

    /**
     *
     * 设置参数寄存器对应的类型
     *
     * @return
     */
    private boolean setupParameterRegType() {
        DexMethodNode methodNode = mMethodNode;
        List<DexInsnNode> insns = mCodeNode.getInsns();
        InstructionInfo entryInsnInfo = InstructionInfo.infoForIns(insns.get(0));

        RegisterPc rp = entryInsnInfo.registerPc;

        RegType defineType = getDeclaringClass();
        int curParaIdx = 0;
        if (!methodNode.isStatic()) {
            if (methodNode.isInstanceInitMethod()) {
                if (defineType.isJavaLangObject()) {
                    rp.setThisInitialized();
                    rp.setParameterRegType(curParaIdx, defineType);
                } else {
                    rp.setParameterRegType(curParaIdx,
                            mRegTypeCache.uninitializedThisArgument(defineType));
                }
            } else {
                rp.setParameterRegType(curParaIdx, defineType);
            }
            curParaIdx++;
        }

        for (int i = 0; i < methodNode.parameters.count(); i++) {
            DexType paraType = methodNode.parameters.getType(i);
            switch (paraType.toShortDescriptor()) {
                case DexItemFactory.ReferenceType.SHORT_DESCRIPTOR:
                case DexItemFactory.ArrayType.SHORT_DESCRIPTOR: {
                    RegType regType = resolveClassAndCheckAccess(paraType);
                    if (!regType.isNonZeroReferenceTypes()) {
                        return false;
                    }
                    rp.setParameterRegType(curParaIdx, regType);
                    break;
                }
                case DexItemFactory.BooleanClass.SHORT_DESCRIPTOR: {
                    rp.setParameterRegType(curParaIdx, mRegTypeCache.booleanType());
                    break;
                }
                case DexItemFactory.CharacterClass.SHORT_DESCRIPTOR: {
                    rp.setParameterRegType(curParaIdx, mRegTypeCache.charType());
                    break;
                }
                case DexItemFactory.ByteClass.SHORT_DESCRIPTOR: {
                    rp.setParameterRegType(curParaIdx, mRegTypeCache.byteType());
                    break;
                }
                case DexItemFactory.IntegerClass.SHORT_DESCRIPTOR: {
                    rp.setParameterRegType(curParaIdx, mRegTypeCache.integerType());
                    break;
                }
                case DexItemFactory.ShortClass.SHORT_DESCRIPTOR: {
                    rp.setParameterRegType(curParaIdx, mRegTypeCache.shortType());
                    break;
                }
                case DexItemFactory.FloatClass.SHORT_DESCRIPTOR: {
                    rp.setParameterRegType(curParaIdx, mRegTypeCache.floatType());
                    break;
                }
                case DexItemFactory.LongClass.SHORT_DESCRIPTOR: {
                    rp.setParameterRegTypeWide(curParaIdx, mRegTypeCache.longLoType(),
                            mRegTypeCache.longHiType());
                    curParaIdx++;
                    break;
                }
                case DexItemFactory.DoubleClass.SHORT_DESCRIPTOR: {
                    rp.setParameterRegTypeWide(curParaIdx, mRegTypeCache.doubleLoType(),
                            mRegTypeCache.doubleHiType());
                    curParaIdx++;
                    break;
                }
                default: {
                    break;
                }
            }

            curParaIdx++;
        }
        return true;
    }

    /**
     *
     * 返回当前方法的返回值类型
     *
     * @return
     */
    public RegType getMethodReturnType() {
        if (mReturnType == null) {
            mReturnType = mRegTypeCache.fromDescriptor(mClassLoader, mMethodNode.returnType,
                    false);
        }
        return mReturnType;
    }


    public void fail(int type, String msg) {
        System.err.println("method = " + mMethodNode.toString()  + " type : " + type + " " + msg
                + " current idx = " + mAnalyzeData.mWorkInsIdx);
    }

    public void info(String tag, String msg) {

    }

    public void ensure(boolean sure) {
        if (!sure) {
//            throw new IllegalStateException();
        }
    }

    public RegType fromClass(DexType type, DexClassNode dcn, boolean precise) {
        return mRegTypeCache.fromClass(type, dcn, precise);
    }

}
