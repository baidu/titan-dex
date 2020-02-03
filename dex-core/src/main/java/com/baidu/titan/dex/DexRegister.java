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

/**
 * DexRegister Info <br>
 *
 * @author zhangdi07@baidu.com
 * @since 2016/11/8
 */
public class DexRegister {

    private final int mReg;

    private final int mWidth;

    public static final int REG_WIDTH_ONE_WORD = 1;

    public static final int REG_WIDTH_DOUBLE_WORD = 2;

    private int mRegRef = REG_REF_UNSPECIFIED;

    public static final int REG_REF_UNSPECIFIED = 0;

    public static final int REG_REF_LOCAL = 1;

    public static final int REG_REF_PARAMETER = 2;

    private static final String[] REG_REF_NAME =
            new String[] {"unspecified", "local" , "parameter"};

    private static final String[] REG_WIDTH_NAME =
            new String[] {"", "one-word", "double-word"};

    private static HashMap<Long, DexRegister> sCache = new HashMap<>();

    private DexRegister(int reg, int width, int ref) {
        this.mReg = reg;
        this.mWidth = width;
        this.mRegRef = ref;
    }

    public static DexRegister makeLocalRegWithWide(int reg, boolean wideReg) {
        return wideReg ? makeDoubleLocalReg(reg) : makeLocalReg(reg);
    }

    public static DexRegister makeLocalReg(int reg) {
        return make(reg, REG_WIDTH_ONE_WORD, REG_REF_LOCAL);
    }

    public static DexRegister makeParameterReg(int reg) {
        return make(reg, REG_WIDTH_ONE_WORD, REG_REF_PARAMETER);
    }

    public static DexRegister makeDoubleLocalReg(int reg) {
        return make(reg, REG_WIDTH_DOUBLE_WORD, REG_REF_LOCAL);
    }

    public static DexRegister makeParameterRegWithWide(int reg, boolean wideReg) {
        return wideReg ? makeDoubleParameterReg(reg) : makeParameterReg(reg);
    }


    public static DexRegister makeDoubleParameterReg(int reg) {
        return make(reg, REG_WIDTH_DOUBLE_WORD, REG_REF_PARAMETER);
    }

    public static DexRegister make(int reg) {
        return make(reg, REG_WIDTH_ONE_WORD, REG_REF_UNSPECIFIED);
    }

    public static DexRegister make(int reg, int width, int ref) {
        if (reg < 0 || width < 0 || ref <0) {
            throw new IllegalArgumentException("illegal argument for reg = " + ref + " width = " +
            width + " ref = " + ref);
        }

        long key = (long)reg | (long)width << 32L | (long)ref << 48L;
        synchronized (sCache) {
            DexRegister dexReg = sCache.get(key);
            if (dexReg == null) {
                dexReg = new DexRegister(reg, width, ref);
                sCache.put(key, dexReg);
            }
            return dexReg;
        }

    }

    public int getWidth() {
        return mWidth;
    }

    public boolean isDoubleWordWidth() {
        return getWidth() == REG_WIDTH_DOUBLE_WORD;
    }

    public boolean isOneWordWidth() {
        return getWidth() == REG_WIDTH_ONE_WORD;
    }

    public boolean isLocalReg() {
        return getRef() == REG_REF_LOCAL;
    }

    public boolean isParameterReg() {
        return getRef() == REG_REF_PARAMETER;
    }

    public boolean unspecifiedReg() {
        return getRef() == REG_REF_UNSPECIFIED;
    }

    public int getReg() {
        return mReg;
    }

    public int getRef() {
        return mRegRef;
    }

    public String toSmaliString() {
        StringBuilder sb = new StringBuilder();
        switch (getRef()) {
            case REG_REF_LOCAL: {
                sb.append("v");
                break;
            }
            case REG_REF_PARAMETER: {
                sb.append("p");
                break;
            }
            case REG_REF_UNSPECIFIED: {
                sb.append("v");
                break;
            }
            default: {
                throw new IllegalStateException();
            }
        }
        sb.append(getReg());
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (getRef()) {
            case REG_REF_LOCAL: {
                sb.append("v");
                break;
            }
            case REG_REF_PARAMETER: {
                sb.append("p");
                break;
            }
            case REG_REF_UNSPECIFIED: {
                sb.append("r");
                break;
            }
            default: {
                throw new IllegalStateException();
            }
        }
        sb.append(getReg());
        if (getWidth() == REG_WIDTH_DOUBLE_WORD) {
            sb.append(" wide");
        }
        return sb.toString();
    }
}
