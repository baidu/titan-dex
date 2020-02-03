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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * DexRegisterList Info <br>
 * 寄存器有序列表
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/8
 */
public class DexRegisterList implements Iterable<DexRegister> {

    private final DexRegister[] mRegs;

    public static DexRegisterList EMPTY = new DexRegisterList(0);

    private boolean mImmutable = false;

    public DexRegisterList(int count) {
        mRegs = new DexRegister[count];
    }


    public static class Builder {

        private List<DexRegister> mRegs = new ArrayList<>();

        public Builder addReg(DexRegister reg) {
            this.mRegs.add(reg);
            return this;
        }

        public DexRegisterList build() {
            if (mRegs.size() == 0) {
                return DexRegisterList.EMPTY;
            }

            DexRegisterList regs = new DexRegisterList(mRegs.size());
            for (int i = 0; i < mRegs.size(); i++) {
                regs.setReg(i, mRegs.get(i));
            }
            return regs;
        }

    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static DexRegisterList empty() {
        return EMPTY;
    }

    public DexRegisterList setReg(int idx, DexRegister reg) {
        if (mImmutable) {
            throw new IllegalStateException("cannot setReg on an immutable reg list");
        }
        mRegs[idx] = reg;
        return this;
    }

    public static DexRegisterList make(DexRegister reg) {
        return new DexRegisterList(1).setReg(0, reg).setImmutable();
    }

    public static DexRegisterList make(DexRegister reg0, DexRegister reg1) {
        return new DexRegisterList(2).setReg(0, reg0).setReg(1, reg1).setImmutable();
    }

    public static DexRegisterList make(DexRegister reg0, DexRegister reg1, DexRegister reg2) {
        return new DexRegisterList(3).setReg(0, reg0).setReg(1, reg1)
                .setReg(2, reg2).setImmutable();
    }

    public static DexRegisterList make(DexRegister reg0, DexRegister reg1, DexRegister reg2,
                                       DexRegister reg3) {
        return new DexRegisterList(4).setReg(0, reg0).setReg(1, reg1)
                .setReg(2, reg2).setReg(3, reg3).setImmutable();
    }

    public DexRegister get(int idx) {
        if (idx <0 || idx >= mRegs.length) {
            throw new ArrayIndexOutOfBoundsException("regCount = " + mRegs.length + " but idx = "
                    + idx);
        }
        return mRegs[idx];
    }

    public int count() {
        return mRegs.length;
    }

    public DexRegisterList setImmutable() {
        mImmutable = true;
        return this;
    }

    @Override
    public Iterator<DexRegister> iterator() {
        return new It();
    }

    private class It implements Iterator<DexRegister> {

        private int mIdx = 0;

        @Override
        public boolean hasNext() {
            return mIdx < count();
        }

        @Override
        public DexRegister next() {
            return get(mIdx++);
        }
    }

    @Override
    public String toString() {
        return "regs {" + Arrays.toString(mRegs) + "}";
    }
}
