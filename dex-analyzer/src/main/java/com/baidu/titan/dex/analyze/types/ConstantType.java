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


/**
 * @author zhangdi07@baidu.com
 * @since 2017/12/27
 */
public class ConstantType extends RegType {

    private int constantValue;


    public ConstantType(int id, int value) {
        super(id, null, null);
        this.constantValue = value;
    }



    public int constantValue() {
        return constantValue;
    }

    public int constantValueLo() {
        return constantValue;
    }

    public int constantValueHi() {
        return constantValue;
    }

    @Override
    public boolean isZero() {
        return isPreciseConstant() && constantValue() == 0;
    }

    @Override
    public boolean isOne() {
        return isPreciseConstant() && constantValue() == 1;
    }

    public boolean isConstantChar() {
        return isConstant() && constantValue() >= 0
                && constantValue() <= Character.MAX_VALUE && constantValue() >= Character.MIN_VALUE;
    }

    @Override
    public boolean isConstantByte() {
        return isConstant() &&
                constantValue() >= Byte.MIN_VALUE &&
                constantValue() <= Byte.MAX_VALUE;
    }

    @Override
    public boolean isConstantShort() {
        return isConstant() &&
                constantValue() >= Short.MIN_VALUE &&
                constantValue() <= Short.MAX_VALUE;
    }

    @Override
    public boolean isConstantTypes() {
        return true;
    }

}
