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

import com.baidu.titan.dex.util.Flags;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangdi07
 * @since 2017/10/27
 */

public class DexAccessFlags extends Flags {

    /**
     * Public
     */
    public final static int ACC_PUBLIC = 0x1;
    /**
     * Private
     */
    public final static int ACC_PRIVATE = 0x2;
    /**
     * Protected
     */
    public final static int ACC_PROTECTED = 0x4;
    /**
     * Static
     */
    public final static int ACC_STATIC = 0x8;
    /**
     * Final
     */
    public final static int ACC_FINAL = 0x10;
    /**
     * Synchronized
     */
    public final static int ACC_SYNCHRONIZED = 0x20;
    /**
     * Volatile
     */
    public final static int ACC_VOLATILE = 0x40;
    /**
     * Bridge
     */
    public final static int ACC_BRIDGE = 0x40;
    /**
     * Transient
     */
    public final static int ACC_TRANSIENT = 0x80;
    /**
     * Varargs
     */
    public final static int ACC_VARARGS = 0x80;
    /**
     * Native
     */
    public final static int ACC_NATIVE = 0x100;
    /**
     * Interface
     */
    public final static int ACC_INTERFACE = 0x200;
    /**
     * abstract
     */
    public final static int ACC_ABSTRACT = 0x400;
    /**
     * Strict
     */
    public final static int ACC_STRICT = 0x800;
    /**
     * Synthetic
     */
    public final static int ACC_SYNTHETIC = 0x1000;
    /**
     * Annotation
     */
    public final static int ACC_ANNOTATION = 0x2000;
    /**
     * Enum
     */
    public final static int ACC_ENUM = 0x4000;
    /**
     * Constructor
     */
    public final static int ACC_CONSTRUCTOR = 0x10000;
    /**
     * Declared synchronized
     */
    public final static int ACC_DECLARED_SYNCHRONIZED = 0x20000;


    private static final List<DexAccessFlag> sAccessFlags = new ArrayList<>();

    static {
        addToAccessFlag(ACC_PUBLIC, "public", true, true, true);
        addToAccessFlag(ACC_PRIVATE, "private", true, true, true);
        addToAccessFlag(ACC_PROTECTED, "protected", true, true, true);
        addToAccessFlag(ACC_STATIC, "static", true, true, true);
        addToAccessFlag(ACC_FINAL, "final", true, true, true);
        addToAccessFlag(ACC_SYNCHRONIZED, "synchronized", false, true, false);
        addToAccessFlag(ACC_VOLATILE, "volatile", false, false, true);
        addToAccessFlag(ACC_BRIDGE, "bridge", false, true, false);
        addToAccessFlag(ACC_TRANSIENT, "transient", false, false, true);
        addToAccessFlag(ACC_VARARGS, "varargs", false, true, false);
        addToAccessFlag(ACC_NATIVE, "native", false, true, false);
        addToAccessFlag(ACC_INTERFACE, "interface", true, false, false);
        addToAccessFlag(ACC_ABSTRACT, "abstract", true, true, false);
        addToAccessFlag(ACC_STRICT, "strict", false, true, false);
        addToAccessFlag(ACC_SYNTHETIC, "synthetic", true, true, true);
        addToAccessFlag(ACC_ANNOTATION, "annotation", true, false, false);
        addToAccessFlag(ACC_ENUM, "enum", true, false, true);
        addToAccessFlag(ACC_CONSTRUCTOR, "constructor", false, true, false);
        addToAccessFlag(ACC_DECLARED_SYNCHRONIZED, "declared-synchronized",
                false, true, false);
    }

    private static void addToAccessFlag(int value, String name, boolean forClass,
                                        boolean forMethod, boolean forField) {
        sAccessFlags.add(new DexAccessFlag(value, name, forClass, forMethod, forField));
    }

    public DexAccessFlags(int flags) {
        super(flags);
    }

    public DexAccessFlags(int... flags) {
        super(flags);
    }

    public DexAccessFlags(DexAccessFlags flags) {
        this(flags.getFlags());
    }

    public static DexAccessFlag[] getAccessFlagForMethod(int flags) {
        ArrayList<DexAccessFlag> resultList = new ArrayList<>();
        for (DexAccessFlag accessFlag : sAccessFlags) {
            if (accessFlag.forMethod && (accessFlag.value & flags) != 0) {
                resultList.add(accessFlag);
            }
        }
        DexAccessFlag[] result = new DexAccessFlag[resultList.size()];
        resultList.toArray(result);
        return result;
    }

    public static DexAccessFlag[] getAccessFlagForField(int flags) {
        ArrayList<DexAccessFlag> resultList = new ArrayList<>();
        for (DexAccessFlag accessFlag : sAccessFlags) {
            if (accessFlag.forField && (accessFlag.value & flags) != 0) {
                resultList.add(accessFlag);
            }
        }
        DexAccessFlag[] result = new DexAccessFlag[resultList.size()];
        resultList.toArray(result);
        return result;
    }

    public static DexAccessFlag[] getAccessFlagForClass(int flags) {
        ArrayList<DexAccessFlag> resultList = new ArrayList<>();
        for (DexAccessFlag accessFlag : sAccessFlags) {
            if (accessFlag.forClass && (accessFlag.value & flags) != 0) {
                resultList.add(accessFlag);
            }
        }
        DexAccessFlag[] result = new DexAccessFlag[resultList.size()];
        resultList.toArray(result);
        return result;
    }

}
