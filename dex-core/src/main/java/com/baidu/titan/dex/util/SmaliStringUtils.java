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

import com.baidu.titan.dex.SmaliWriter;

/**
 * 用于Smali格式输出
 *
 * @author zhangdi07@baidu.com
 * @since 2019/1/9
 */
public class SmaliStringUtils {

    public static void writeEscapedChar(SmaliWriter writer, char c) {
        if ((c >= ' ') && (c < 0x7f)) {
            if ((c == '\'') || (c == '\"') || (c == '\\')) {
                writer.write('\\');
            }
            writer.write(c);
            return;
        } else if (c <= 0x7f) {
            switch (c) {
                case '\n':
                    writer.write("\\n");
                    return;
                case '\r':
                    writer.write("\\r");
                    return;
                case '\t':
                    writer.write("\\t");
                    return;
                default: {
                    return;
                }
            }
        }

        writer.write("\\u");
        writer.write(Character.forDigit(c >> 12, 16));
        writer.write(Character.forDigit((c >> 8) & 0x0f, 16));
        writer.write(Character.forDigit((c >> 4) & 0x0f, 16));
        writer.write(Character.forDigit(c & 0x0f, 16));
    }

    public static void writeEscapedString(SmaliWriter writer, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if ((c >= ' ') && (c < 0x7f)) {
                if ((c == '\'') || (c == '\"') || (c == '\\')) {
                    writer.write('\\');
                }
                writer.write(c);
                continue;
            } else if (c <= 0x7f) {
                switch (c) {
                    case '\n':
                        writer.write("\\n");
                        continue;
                    case '\r':
                        writer.write("\\r");
                        continue;
                    case '\t':
                        writer.write("\\t");
                        continue;
                    default: {
                        continue;
                    }
                }
            }

            writer.write("\\u");
            writer.write(Character.forDigit(c >> 12, 16));
            writer.write(Character.forDigit((c >> 8) & 0x0f, 16));
            writer.write(Character.forDigit((c >> 4) & 0x0f, 16));
            writer.write(Character.forDigit(c & 0x0f, 16));
        }
    }

    public static String escapeString(String value) {
        int len = value.length();
        StringBuilder sb = new StringBuilder(len * 3 / 2);

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);

            if ((c >= ' ') && (c < 0x7f)) {
                if ((c == '\'') || (c == '\"') || (c == '\\')) {
                    sb.append('\\');
                }
                sb.append(c);
                continue;
            } else if (c <= 0x7f) {
                switch (c) {
                    case '\n':
                        sb.append("\\n");
                        continue;
                    case '\r':
                        sb.append("\\r");
                        continue;
                    case '\t':
                        sb.append("\\t");
                        continue;
                    default: {
                        continue;
                    }
                }
            }

            sb.append("\\u");
            sb.append(Character.forDigit(c >> 12, 16));
            sb.append(Character.forDigit((c >> 8) & 0x0f, 16));
            sb.append(Character.forDigit((c >> 4) & 0x0f, 16));
            sb.append(Character.forDigit(c & 0x0f, 16));
        }

        return sb.toString();
    }

}
