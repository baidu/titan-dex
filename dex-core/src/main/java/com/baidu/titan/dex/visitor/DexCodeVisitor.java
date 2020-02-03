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

package com.baidu.titan.dex.visitor;

import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexRegisterList;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;

/**
 * Dex Code Visitor。<br>
 * <p>
 * visit order：<br>
 * <ul>
 * <li> visitRegisters </li>
 * <li> visitInsn | visitLabel </li>
 * <li> visitEnd </li>
 * </ul>
 * for DexCodeWriter, visitRegisters() can last access
 *
 * @author zhangdi07@baidu.com
 * @since 2016/12/7
 */
public class DexCodeVisitor implements VisitorExtraInfo {

    protected DexCodeVisitor delegate;

    public DexCodeVisitor(DexCodeVisitor delegate) {
        this.delegate = delegate;
    }

    public DexCodeVisitor() {
        this(null);
    }

    /**
     * 访问开始
     */
    public void visitBegin() {
        if (delegate != null) {
            delegate.visitBegin();
        }
    }

    /**
     * 访问该方法中寄存器个数
     *
     * @param localRegCount   本地寄存器个数
     * @param parameterRegCount 参数寄存器个数
     */
    public void visitRegisters(int localRegCount, int parameterRegCount) {
        if (delegate != null) {
            delegate.visitRegisters(localRegCount, parameterRegCount);
        }
    }

    /**
     * 访问try catch结构体
     *
     * @param start
     * @param end
     * @param types
     * @param handlers
     */
    public void visitTryCatch(DexLabel start, DexLabel end, DexTypeList types,
                              DexLabel[] handlers, DexLabel catchAllHandler) {
        if (delegate != null) {
            delegate.visitTryCatch(start, end, types, handlers, catchAllHandler);
        }
    }

    /**
     * 访问代码标签信息
     *
     * @param label
     */
    public void visitLabel(DexLabel label) {
        if (delegate != null) {
            delegate.visitLabel(label);
        }
    }

    /**
     * 访问常量信息，包括字符串，基本类型等常量
     *
     * @param op
     * @param regs
     * @param dexConst
     */
    public void visitConstInsn(int op, DexRegisterList regs, DexConst dexConst) {
        if (delegate != null) {
            delegate.visitConstInsn(op, regs, dexConst);
        }
    }

    /**
     * 访问跳转指令信息
     *
     * @param op
     * @param regs
     * @param label
     */
    public void visitTargetInsn(int op, DexRegisterList regs, DexLabel label) {
        if (delegate != null) {
            delegate.visitTargetInsn(op, regs, label);
        }
    }

    /**
     * 访问简单指令信息
     *
     * @param op
     * @param regs
     */
    public void visitSimpleInsn(int op, DexRegisterList regs) {
        if (delegate != null) {
            delegate.visitSimpleInsn(op, regs);
        }
    }

//    /**
//     * 填充数组指令信息
//     *
//     * @param op
//     * @param regs
//     * @param width
//     * @param length
//     * @param data
//     */
//    public void visitFillArrayData(int op, DexRegisterList regs, int width, int length,
//                                   byte[] data) {
//        if (delegate != null) {
//            delegate.visitFillArrayData(op, regs, width, length, data);
//        }
//    }

    public void visitSwitch(int op, DexRegisterList regs, int[] keys, DexLabel[] targets) {
        if (delegate != null) {
            delegate.visitSwitch(op, regs, keys, targets);
        }
    }

    /**
     * 访问方法参数名称，不包括this参数
     *
     * @param parameters
     */
    public void visitParameters(DexString[] parameters) {
        if (delegate != null) {
            delegate.visitParameters(parameters);
        }
    }

    /**
     * 访问局部变量信息
     *
     * @param reg
     * @param name
     * @param type
     * @param signature
     * @param start
     * @param end
     */
    public void visitLocal(int reg, DexString name, DexType type, DexString signature,
                           DexLabel start, DexLabel end) {
        if (delegate != null) {
            delegate.visitLocal(reg, name, type, signature, start, end);
        }
    }

    /**
     * 访问方法行信息
     *
     * @param line
     * @param start
     */
    public void visitLineNumber(int line, DexLabel start) {
        if (delegate != null) {
            delegate.visitLineNumber(line, start);
        }
    }

    /**
     * 访问结束
     */
    public void visitEnd() {
        if (delegate != null) {
            delegate.visitEnd();
        }
    }

}
