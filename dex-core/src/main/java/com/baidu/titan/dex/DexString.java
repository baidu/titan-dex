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

import java.io.UTFDataFormatException;
import java.util.Arrays;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/9/29
 */

public class DexString implements Comparable<DexString> {

    public static final DexString[] EMPTY_ARRAY = new DexString[]{};

    public final int size;  // size of this string, in UTF-16
    public final byte[] content;

    DexString(int size, byte[] content) {
        this.size = size;
        this.content = content;
    }

    public DexString(String string) {
        this.size = string.length();
        this.content = encode(string);
    }

    @Override
    public int hashCode() {
        return size * 7 + Arrays.hashCode(content);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof DexString) {
            DexString o = (DexString) other;
            return size == o.size && Arrays.equals(content, o.content);
        } else if (other instanceof String) {
            return toString().equals((other));
        }
        return false;
    }

    @Override
    public String toString() {
        try {
            return decode();
        } catch (UTFDataFormatException e) {
            throw new RuntimeException("Bad format", e);
        }
    }

    public int numberOfLeadingSquareBrackets() {
        int result = 0;
        while (content.length > result && content[result] == ((byte) '[')) {
            result++;
        }
        return result;
    }

    // Inspired from /dex/src/main/java/com/android/dex/Mutf8.java
    private String decode() throws UTFDataFormatException {
        int s = 0;
        int p = 0;
        char[] out = new char[size];
        while (true) {
            char a = (char) (content[p++] & 0xff);
            if (a == 0) {
                return new String(out, 0, s);
            }
            out[s] = a;
            if (a < '\u0080') {
                s++;
            } else if ((a & 0xe0) == 0xc0) {
                int b = content[p++] & 0xff;
                if ((b & 0xC0) != 0x80) {
                    throw new UTFDataFormatException("bad second byte");
                }
                out[s++] = (char) (((a & 0x1F) << 6) | (b & 0x3F));
            } else if ((a & 0xf0) == 0xe0) {
                int b = content[p++] & 0xff;
                int c = content[p++] & 0xff;
                if (((b & 0xC0) != 0x80) || ((c & 0xC0) != 0x80)) {
                    throw new UTFDataFormatException("bad second or third byte");
                }
                out[s++] = (char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F));
            } else {
                throw new UTFDataFormatException("bad byte");
            }
        }
    }

    // Inspired from /dex/src/main/java/com/android/dex/Mutf8.java
    private static int countBytes(String string) {
        int result = 0;
        for (int i = 0; i < string.length(); ++i) {
            char ch = string.charAt(i);
            if (ch != 0 && ch <= 127) { // U+0000 uses two bytes.
                ++result;
            } else if (ch <= 2047) {
                result += 2;
            } else {
                result += 3;
            }
            assert result > 0;
        }
        // We need an extra byte for the terminating '0'.
        return result + 1;
    }

    // Inspired from /dex/src/main/java/com/android/dex/Mutf8.java
    private static byte[] encode(String string) {
        byte[] result = new byte[countBytes(string)];
        int offset = 0;
        for (int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
            if (ch != 0 && ch <= 127) { // U+0000 uses two bytes.
                result[offset++] = (byte) ch;
            } else if (ch <= 2047) {
                result[offset++] = (byte) (0xc0 | (0x1f & (ch >> 6)));
                result[offset++] = (byte) (0x80 | (0x3f & ch));
            } else {
                result[offset++] = (byte) (0xe0 | (0x0f & (ch >> 12)));
                result[offset++] = (byte) (0x80 | (0x3f & (ch >> 6)));
                result[offset++] = (byte) (0x80 | (0x3f & ch));
            }
        }
        result[offset] = 0;
        return result;
    }

    @Override
    public int compareTo(DexString other) {
        // Compare the bytes, as comparing UTF-8 encoded strings as strings of unsigned bytes gives
        // the same result as comparing the corresponding Unicode strings lexicographically by
        // codepoint. The only complication is the MUTF-8 encoding have the two byte encoding c0 80 of
        // the null character (U+0000) to allow embedded null characters.
        // Supplementary characters (unicode code points above U+FFFF) are always represented as
        // surrogate pairs and are compared using UTF-16 code units as per Java string semantics.
        int index = 0;
        while (true) {
            char b1 = (char) (content[index] & 0xff);
            char b2 = (char) (other.content[index] & 0xff);
            int diff = b1 - b2;
            if (diff != 0) {
                // Check if either string ends here.
                if (b1 == 0 || b2 == 0) {
                    return diff;
                }
                // If either of the strings have the null character starting here, the null character
                // sort lowest.
                if ((b1 == 0xc0 && (content[index + 1] & 0xff) == 0x80) ||
                        (b2 == 0xc0 && (other.content[index + 1] & 0xff) == 0x80)) {
                    return b1 == 0xc0 && (content[index + 1] & 0xff) == 0x80 ? -1 : 1;
                }
                return diff;
            } else if (b1 == 0) {
                // Reached the end in both strings.
                return 0;
            }
            index++;
        }
    }
}
