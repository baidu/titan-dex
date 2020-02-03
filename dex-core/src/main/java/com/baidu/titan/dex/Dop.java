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

/**
 * 标识一个Dalvik字节码指令及其属性
 *
 * @author zhangdi07@baidu.com
 * @since 2017/12/14
 */
public class Dop {

    public final int opcode;

    public String opcodeName;

    public boolean hasResult;

    private final int cfgFlags;

    private final int mFormat;

    static final int FLAG_CFG_BRANCH = 1 << 0;

    static final int FLAG_CFG_CONTINUE = 1 << 1;

    static final int FLAG_CFG_SWITCH = 1 << 2;

    static final int FLAG_CFG_THROW = 1 << 3;

    static final int FLAG_CFG_RETURN = 1 << 4;

//    static final int FLAG_INVOKE = 1 << 5;
//
    static final int FLAG_CONST = 1 << 6;

    public Dop(int opcode, String opcodeName, boolean hasResult, int cfgFlags, int format) {
        this.opcode = opcode;
        this.opcodeName = opcodeName;
        this.cfgFlags = cfgFlags;
        this.hasResult = hasResult;
        this.mFormat = format;
    }

    public String getOpcodeName() {
        return this.opcodeName;
    }

    public boolean canContinue() {
        return (cfgFlags & FLAG_CFG_CONTINUE) != 0;
    }

    public boolean canBranch() {
        return (cfgFlags & FLAG_CFG_BRANCH) != 0;
    }

    public boolean canSwitch() {
        return (cfgFlags & FLAG_CFG_SWITCH) != 0;
    }

    public boolean canThrow() {
        return (cfgFlags & FLAG_CFG_THROW) != 0;
    }

    public boolean canReturn() {
        return (cfgFlags & FLAG_CFG_RETURN) != 0;
    }

    public boolean hasResult() {
        return hasResult;
    }

    public boolean isConstOpcode() {
        return (this.cfgFlags & Dop.FLAG_CONST) != 0;
    }

    public boolean isTargetOpcode() {
        return canBranch();
    }

    public boolean isSwitchOpcode() {
        return canSwitch();
    }

    public boolean isSimpleOpcode() {
        return !isConstOpcode() && !isTargetOpcode() && !isSwitchOpcode();
    }

    public String getOpcodeCategory() {
        if (isConstOpcode()) {
            return "const";
        } else if (isTargetOpcode()) {
            return "target";
        } else if (isSwitchOpcode()) {
            return "switch";
        } else if (isSimpleOpcode()) {
            return "simple";
        } else {
            throw new IllegalStateException("unknown opcode category " + this);
        }

    }

    public int getFormat() {
        return mFormat;
    }

    public boolean isInvokeKind() {
        switch (this.opcode) {
            case Dops.INVOKE_DIRECT:
            case Dops.INVOKE_DIRECT_RANGE:
            case Dops.INVOKE_INTERFACE:
            case Dops.INVOKE_INTERFACE_RANGE:
            case Dops.INVOKE_STATIC:
            case Dops.INVOKE_STATIC_RANGE:
            case Dops.INVOKE_SUPER:
            case Dops.INVOKE_SUPER_RANGE:
            case Dops.INVOKE_VIRTUAL:
            case Dops.INVOKE_VIRTUAL_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isFieldAccessKind() {
        switch (this.opcode) {
            // get-xx
            case Dops.IGET:
            case Dops.IGET_BOOLEAN:
            case Dops.IGET_BYTE:
            case Dops.IGET_OBJECT:
            case Dops.IGET_SHORT:
            case Dops.IGET_WIDE:
            case Dops.IGET_CHAR:
            case Dops.SGET:
            case Dops.SGET_BOOLEAN:
            case Dops.SGET_BYTE:
            case Dops.SGET_OBJECT:
            case Dops.SGET_SHORT:
            case Dops.SGET_WIDE:
            case Dops.SGET_CHAR:
                // put-xx
            case Dops.IPUT:
            case Dops.IPUT_BOOLEAN:
            case Dops.IPUT_BYTE:
            case Dops.IPUT_OBJECT:
            case Dops.IPUT_SHORT:
            case Dops.IPUT_WIDE:
            case Dops.IPUT_CHAR:
            case Dops.SPUT:
            case Dops.SPUT_BOOLEAN:
            case Dops.SPUT_BYTE:
            case Dops.SPUT_OBJECT:
            case Dops.SPUT_SHORT:
            case Dops.SPUT_WIDE:
            case Dops.SPUT_CHAR: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isFieldInstanceGet() {
        switch (this.opcode) {
            // get-xx
            case Dops.IGET:
            case Dops.IGET_BOOLEAN:
            case Dops.IGET_BYTE:
            case Dops.IGET_OBJECT:
            case Dops.IGET_SHORT:
            case Dops.IGET_WIDE:
            case Dops.IGET_CHAR: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isFieldInstancePut() {
        switch (this.opcode) {
            // put-xx
            case Dops.IPUT:
            case Dops.IPUT_BOOLEAN:
            case Dops.IPUT_BYTE:
            case Dops.IPUT_OBJECT:
            case Dops.IPUT_SHORT:
            case Dops.IPUT_WIDE:
            case Dops.IPUT_CHAR: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isFieldStaticGet() {
        switch (this.opcode) {
            case Dops.SGET:
            case Dops.SGET_BOOLEAN:
            case Dops.SGET_BYTE:
            case Dops.SGET_OBJECT:
            case Dops.SGET_SHORT:
            case Dops.SGET_WIDE:
            case Dops.SGET_CHAR: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isFieldStaticPut() {
        switch (this.opcode) {
            // put-xx
            case Dops.SPUT:
            case Dops.SPUT_BOOLEAN:
            case Dops.SPUT_BYTE:
            case Dops.SPUT_OBJECT:
            case Dops.SPUT_SHORT:
            case Dops.SPUT_WIDE:
            case Dops.SPUT_CHAR: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isInvokeStatic() {
        switch (this.opcode) {
            case Dops.INVOKE_STATIC:
            case Dops.INVOKE_STATIC_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isInvokeVirtual() {
        switch (this.opcode) {
            case Dops.INVOKE_VIRTUAL:
            case Dops.INVOKE_VIRTUAL_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isInvokeDirect() {
        switch (this.opcode) {
            case Dops.INVOKE_DIRECT:
            case Dops.INVOKE_DIRECT_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isInvokeSuper() {
        switch (this.opcode) {
            case Dops.INVOKE_SUPER:
            case Dops.INVOKE_SUPER_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isInvokeInterface() {
        switch (this.opcode) {
            case Dops.INVOKE_INTERFACE:
            case Dops.INVOKE_INTERFACE_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public boolean isInvokeRange() {
        switch (this.opcode) {
            case Dops.INVOKE_DIRECT_RANGE:
            case Dops.INVOKE_INTERFACE_RANGE:
            case Dops.INVOKE_STATIC_RANGE:
            case Dops.INVOKE_SUPER_RANGE:
            case Dops.INVOKE_VIRTUAL_RANGE: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    @Override
    public String toString() {
        return this.opcodeName;
    }
    
}
