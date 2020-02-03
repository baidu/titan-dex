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
 * 常量定义 <br>
 *
 * @author zhangdi07@baidu.com
 * @since 2017/12/1
 */
public class DexConstant {

    public static class DebugOpcodes {
        /**
         * Debug instruction : end sequence
         */
        public final static int DBG_END_SEQUENCE = 0x00;
        /**
         * Debug instruction : advance pc
         */
        public final static int DBG_ADVANCE_PC = 0x01;
        /**
         * Debug instruction : advance linie
         */
        public final static int DBG_ADVANCE_LINE = 0x02;
        /**
         * debug instruction : start local
         */
        public final static int DBG_START_LOCAL = 0x03;
        /**
         * Debug instruction : start local (extended)
         */
        public final static int DBG_START_LOCAL_EXTENDED = 0x04;
        /**
         * Debug instruction : end local
         */
        public final static int DBG_END_LOCAL = 0x05;
        /**
         * debug instruction : restart local
         */
        public final static int DBG_RESTART_LOCAL = 0x06;
        /**
         * debug instruction : set prologue end
         */
        public final static int DBG_SET_PROLOGUE_END = 0x07;
        /**
         * debug instruction : set epilogue begin
         */
        public final static int DBG_SET_EPILOGUE_BEGIN = 0x08;
        /**
         * debug instruction : set file
         */
        public final static int DBG_SET_FILE = 0x09;

        /**
         * Debug offset first special
         */
        public final static int DBG_FIRST_SPECIAL = 0x0a;
        /**
         * Debug offset line base
         */
        public final static int DBG_LINE_BASE = -4;
        /**
         * Debug offset line range
         */
        public final static int DBG_LINE_RANGE = 15;
    }
}
