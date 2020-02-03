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

package com.baidu.titan.dex.util;

import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.node.DexClassNode;
import com.baidu.titan.dex.node.DexFieldIdNode;
import com.baidu.titan.dex.node.DexMethodIdNode;
import com.baidu.titan.dex.visitor.DexAnnotationVisitor;
import com.baidu.titan.dex.visitor.DexAnnotationVisitorInfo;
import com.baidu.titan.dex.visitor.DexClassVisitor;
import com.baidu.titan.dex.visitor.DexCodeVisitor;
import com.baidu.titan.dex.visitor.DexFieldVisitor;
import com.baidu.titan.dex.visitor.DexFieldVisitorInfo;
import com.baidu.titan.dex.visitor.DexLabel;
import com.baidu.titan.dex.visitor.DexMethodVisitor;
import com.baidu.titan.dex.visitor.DexMethodVisitorInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * 用来收集单个DexClassNode中诸如MethodID、FieldID、StringID等共享信息。
 *
 * @author zhangdi07@baidu.com
 * @since 2018/6/1
 */
public class DexIdsCollector extends DexClassVisitor {

    private HashSet<DexMethodIdNode> mMethodIds = new HashSet<>(128);

    private HashSet<DexFieldIdNode> mFieldIds = new HashSet<>(128);

    private HashSet<DexString> mStringIds = new HashSet<>(128);

    private Cache mCache;

    public DexIdsCollector() {
        this(null);
    }

    public static final int FLAG_COLLECT_METHOD_IDS = 1 << 0;

    public static final int FLAG_COLLECT_FIELD_IDS = 1 << 1;

    public static final int FLAG_COLLECT_STRING_IDS = 1 << 2;

    public static final int FLAG_COLLECT_TYPE_IDS = 1 << 3;

    private Flags mFlags;

    public DexIdsCollector(Cache cache) {
        this(FLAG_COLLECT_METHOD_IDS | FLAG_COLLECT_FIELD_IDS, cache);
    }

    public DexIdsCollector(int flags, Cache cache) {
        if (cache == null) {
            cache = new Cache();
        }
        this.mCache = cache;
        this.mFlags = new Flags(flags);
    }

    public static class Cache {

        private Map<DexMethodIdNode, DexMethodIdNode> mMethodIds = new HashMap<>();

        private Map<DexFieldIdNode, DexFieldIdNode> mFieldIds = new HashMap<>();

        private Map<DexString, DexString> mStringIds = new HashMap<>();

        public Cache() {

        }

        public DexMethodIdNode intern(DexMethodIdNode methodIdNode) {
            DexMethodIdNode cached = this.mMethodIds.putIfAbsent(methodIdNode, methodIdNode);
            return cached != null ? cached : methodIdNode;
        }

        public DexFieldIdNode intern(DexFieldIdNode fieldIdNode) {
            DexFieldIdNode cached = this.mFieldIds.putIfAbsent(fieldIdNode, fieldIdNode);
            return cached != null ? cached : fieldIdNode;
        }

        public DexString intern(DexString stringId) {
            DexString cached = this.mStringIds.putIfAbsent(stringId, stringId);
            return cached != null ? cached : stringId;
        }

    }

    public Set<DexMethodIdNode> getMethodIds() {
        return this.mMethodIds;
    }

    public Set<DexFieldIdNode> getFieldIds() {
        return this.mFieldIds;
    }

    public static DexIdsCollector collectDexIds(DexClassNode classNode, Cache cache) {
        DexIdsCollector collector = new DexIdsCollector(cache);
        classNode.accept(collector);
        return collector;
    }

    @Override
    public void visitBegin() {
        super.visitBegin();
    }

    @Override
    public void visitSourceFile(DexString sourceFile) {
        super.visitSourceFile(sourceFile);

        if (mFlags.containsOneOf(FLAG_COLLECT_STRING_IDS)) {
            mStringIds.add(this.mCache.intern(sourceFile));
        }

    }

    @Override
    public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
        return new AnnotationCollector();
    }

    @Override
    public DexFieldVisitor visitField(DexFieldVisitorInfo fieldInfo) {

        DexFieldIdNode fieldIdNode = new DexFieldIdNode(
                fieldInfo.owner,
                fieldInfo.name,
                fieldInfo.type);

        if (mFlags.containsOneOf(FLAG_COLLECT_FIELD_IDS)) {
            this.mFieldIds.add(this.mCache.intern(fieldIdNode));
        }

        return new FieldCollector();
    }

    @Override
    public DexMethodVisitor visitMethod(DexMethodVisitorInfo methodInfo) {
        DexMethodIdNode methodIdNode = new DexMethodIdNode(
                methodInfo.owner,
                methodInfo.name,
                methodInfo.parameters,
                methodInfo.returnType);

        if (mFlags.containsOneOf(FLAG_COLLECT_METHOD_IDS)) {
            this.mMethodIds.add(this.mCache.intern(methodIdNode));
        }

        return new MethodCollector();
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }

    private class MethodCollector extends DexMethodVisitor {

        public MethodCollector() {
            super();
        }

        @Override
        public void visitBegin() {
            super.visitBegin();
        }

        @Override
        public DexAnnotationVisitor visitAnnotationDefault() {
            return new AnnotationCollector();
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
            return new AnnotationCollector();
        }

        @Override
        public DexAnnotationVisitor visitParameterAnnotation(int parameter,
                                                          DexAnnotationVisitorInfo annotationInfo) {
            return new AnnotationCollector();
        }

        @Override
        public DexCodeVisitor visitCode() {
            return new CodeCollector();
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    private class FieldCollector extends DexFieldVisitor {

        @Override
        public void visitBegin() {
            super.visitBegin();
        }

        @Override
        public void visitStaticValue(Object staticValue) {
            super.visitStaticValue(staticValue);
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotation) {
            return new AnnotationCollector();
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }

    }

    private class CodeCollector extends DexCodeVisitor {

        public CodeCollector() {
            super();
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
        public void visitTryCatch(DexLabel start, DexLabel end, DexTypeList types,
                                  DexLabel[] handlers, DexLabel catchAllHandler) {
            super.visitTryCatch(start, end, types, handlers, catchAllHandler);

        }

        @Override
        public void visitLabel(DexLabel label) {
            super.visitLabel(label);
        }

        @Override
        public void visitConstInsn(int op, DexRegisterList regs, DexConst dexConst) {
            super.visitConstInsn(op, regs, dexConst);
            if (dexConst instanceof DexConst.ConstFieldRef) {
                if (mFlags.containsOneOf(FLAG_COLLECT_FIELD_IDS)) {
                    DexConst.ConstFieldRef fieldRef = (DexConst.ConstFieldRef)dexConst;
                    mFieldIds.add(mCache.intern(new DexFieldIdNode(
                            fieldRef.getOwner(),
                            fieldRef.getName(),
                            fieldRef.getType())));
                }
            } else if (dexConst instanceof DexConst.ConstMethodRef) {
                if (mFlags.containsOneOf(FLAG_COLLECT_METHOD_IDS)) {
                    DexConst.ConstMethodRef methodRef = (DexConst.ConstMethodRef)dexConst;
                    mMethodIds.add(mCache.intern(new DexMethodIdNode(
                            methodRef.getOwner(),
                            methodRef.getName(),
                            methodRef.getParameterTypes(),
                            methodRef.getReturnType())));
                }
            } else if (dexConst instanceof DexConst.ConstString) {
                DexConst.ConstString constString = (DexConst.ConstString)dexConst;
                mStringIds.add(mCache.intern(new DexString(constString.value())));
            }

        }

        @Override
        public void visitTargetInsn(int op, DexRegisterList regs, DexLabel label) {
            super.visitTargetInsn(op, regs, label);
        }

        @Override
        public void visitSimpleInsn(int op, DexRegisterList regs) {
            super.visitSimpleInsn(op, regs);
        }

        @Override
        public void visitSwitch(int op, DexRegisterList regs, int[] keys, DexLabel[] targets) {
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

    private class AnnotationCollector extends DexAnnotationVisitor {

        @Override
        public void visitBegin() {
            super.visitBegin();
        }

        @Override
        public void visitPrimitive(DexString name, Object value) {
            super.visitPrimitive(name, value);
        }

        @Override
        public void visitString(DexString name, DexString value) {
            super.visitString(name, value);
        }

        @Override
        public void visitEnum(DexString name, DexType enumType, DexString enumName) {
            super.visitEnum(name, enumType, enumName);
            DexFieldIdNode fieldIdNode = new DexFieldIdNode(
                    enumType,
                    enumName,
                    enumType);
            if (mFlags.containsOneOf(FLAG_COLLECT_FIELD_IDS)) {
                mFieldIds.add(mCache.intern(fieldIdNode));
            }
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(DexString name, DexType type) {
            return new AnnotationCollector();
        }

        @Override
        public DexAnnotationVisitor visitArray(DexString name) {
            return new AnnotationCollector();
        }

        @Override
        public void visitMethod(DexString name, DexConst.ConstMethodRef methodRef) {
            super.visitMethod(name, methodRef);
            DexMethodIdNode methodIdNode = new DexMethodIdNode(
                    methodRef.getOwner(),
                    methodRef.getName(),
                    methodRef.getParameterTypes(),
                    methodRef.getReturnType());
            if (mFlags.containsOneOf(FLAG_COLLECT_METHOD_IDS)) {
                mMethodIds.add(mCache.intern(methodIdNode));
            }
        }

        @Override
        public void visitType(DexString name, DexType type) {
            super.visitType(name, type);
        }

        @Override
        public void visitNull(DexString name) {
            super.visitNull(name);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

}
