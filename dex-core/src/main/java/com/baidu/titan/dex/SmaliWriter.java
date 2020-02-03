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

import java.io.IOException;
import java.io.Writer;

/**
 * Smali格式输出工具类
 *
 * @author zhangdi07@baidu.com
 * @since 2017/10/10
 */

public class SmaliWriter {

    protected final Writer writer;

    protected final char[] buffer = new char[24];

    protected int indentLevel = 0;

    private boolean beginningOfLine = true;

    private static final String newLine = System.getProperty("line.separator");

    public SmaliWriter(Writer writer) {
        this.writer = writer;
    }

    protected void writeIndent() {
        for (int i=0; i<indentLevel; i++) {
            safeWrite(' ');
        }
    }

    public void write(int chr) {
        if (chr == '\n') {
            safeWrite(newLine);
            beginningOfLine = true;
        } else {
            if (beginningOfLine) {
                writeIndent();
            }
            beginningOfLine = false;
            safeWrite(chr);
        }
    }

    private void safeWrite(int chr) {
        try {
            writer.write(chr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void safeWrite(String str) {
        try {
            writer.write(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void safeWrite(char[] chars, int start, int len) {
        try {
            writer.write(chars, start, len);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void safeWrite(String str, int start, int len) {
        try {
            writer.write(str, start, len);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void safeFlush() {
        try {
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Writes out a block of text that contains no newlines
     */
    private void writeLine(char[] chars, int start, int len) {
        if (beginningOfLine && len > 0) {
            writeIndent();
            beginningOfLine = false;
        }
        safeWrite(chars, start, len);
    }


    /**
     * Writes out a block of text that contains no newlines
     */
    private void writeLine(String str, int start, int len) {
        if (beginningOfLine && len > 0) {
            writeIndent();
            beginningOfLine = false;
        }
        safeWrite(str, start, len);
    }


    public void write(char[] chars) {
        write(chars, 0, chars.length);
    }


    public void write(char[] chars, int start, int len) {
        final int end = start+len;
        int pos = start;
        while (pos < end) {
            if (chars[pos] == '\n') {
                writeLine(chars, start, pos-start);

                safeWrite(newLine);
                beginningOfLine = true;
                pos++;
                start = pos;
            } else {
                pos++;
            }
        }
        writeLine(chars, start, pos-start);
    }


    public void write(String s) {
        write(s, 0, s.length());
    }

    public void writeLine(String s) {
        write(s);
        write('\n');
    }

    public void newLine() {
        write('\n');
    }

    public void write(String str, int start, int len) {
        final int end = start+len;
        int pos = start;
        while (pos < end) {
            pos = str.indexOf('\n', start);
            if (pos == -1 || pos >= end) {
                writeLine(str, start, end-start);
                return;
            } else {
                writeLine(str, start, pos-start);
                safeWrite(newLine);
                beginningOfLine = true;
                start = pos+1;
            }
        }
    }


    public SmaliWriter append(CharSequence charSequence) {
        write(charSequence.toString());
        return this;
    }

    public SmaliWriter append(CharSequence charSequence, int start, int len) throws IOException {
        write(charSequence.subSequence(start, len).toString());
        return this;
    }


    public SmaliWriter append(char c) throws IOException {
        write(c);
        return this;
    }


    public void flush() throws IOException {
        safeFlush();
    }

    public void indent(int indentAmount) {
        this.indentLevel += indentAmount;
        if (indentLevel < 0) {
            indentLevel = 0;
        }
    }

    public void deindent(int indentAmount) {
        this.indentLevel -= indentAmount;
        if (indentLevel < 0) {
            indentLevel = 0;
        }
    }

    public void printUnsignedLongAsHex(long value) {
        int bufferIndex = 23;
        do {
            int digit = (int)(value & 15);
            if (digit < 10) {
                buffer[bufferIndex--] = (char)(digit + '0');
            } else {
                buffer[bufferIndex--] = (char)((digit - 10) + 'a');
            }

            value >>>= 4;
        } while (value != 0);

        bufferIndex++;

        writeLine(buffer, bufferIndex, 24-bufferIndex);
    }

    public void printSignedLongAsDec(long value) {
        int bufferIndex = 23;

        if (value < 0) {
            value *= -1;
            write('-');
        }

        do {
            long digit = value % 10;
            buffer[bufferIndex--] = (char)(digit + '0');

            value = value / 10;
        } while (value != 0);

        bufferIndex++;

        writeLine(buffer, bufferIndex, 24-bufferIndex);
    }

    public void printSignedIntAsDec(int value) {
        int bufferIndex = 15;

        if (value < 0) {
            value *= -1;
            write('-');
        }

        do {
            int digit = value % 10;
            buffer[bufferIndex--] = (char)(digit + '0');

            value = value / 10;
        } while (value != 0);

        bufferIndex++;

        writeLine(buffer, bufferIndex, 16-bufferIndex);
    }

    public void printUnsignedIntAsDec(int value) throws IOException {
        int bufferIndex = 15;

        if (value < 0) {
            printSignedLongAsDec(value & 0xFFFFFFFFL);
        } else {
            printSignedIntAsDec(value);
        }
    }
}

