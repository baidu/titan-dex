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

package com.baidu.titan.dex.writer;

import com.baidu.titan.dex.DexFileVersion;
import com.baidu.titan.dex.visitor.DexClassVisitor;
import com.baidu.titan.dex.visitor.DexClassVisitorInfo;
import com.baidu.titan.dex.visitor.DexFileVisitor;
import com.baidu.titan.dexlib.dx.dex.DexOptions;
import com.baidu.titan.dexlib.dx.dex.file.DexFile;

import java.io.*;

/**
 * 用于生成DexFile<br>
 * Dex 指令集参考：http://source.android.com/devices/tech/dalvik/dalvik-bytecode.html <br>
 * http://source.android.com/devices/tech/dalvik/instruction-formats.html
 *
 * @author zhangdi07@baidu.com
 * @since 2017/1/15
 */
public class DexFileWriter extends DexFileVisitor {

    private DexFile mDexFile;

    public DexFileWriter() {
        super();
    }

    @Override
    public void visitBegin() {
        DexOptions dexOptions = new DexOptions();
        mDexFile = new DexFile(dexOptions);
    }

    @Override
    public void visitDexVersion(DexFileVersion version) {
        // TODO 暂时先写入固定Dex Version，后继支持多种版本写入
        super.visitDexVersion(version);
    }

    @Override
    public DexClassVisitor visitClass(DexClassVisitorInfo classInfo) {
        return new DexClassWriter(mDexFile, classInfo);
    }

    @Override
    public void visitEnd() {

    }

    /**
     * 生成Dex文件字节数组
     * @return
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            writeTo(baos, null , false);
            return baos.toByteArray();
        } catch (IOException e) {
        }
        return null;
    }

    /**
     * 生成Dex文件，并输出到Stream
     * @param out
     * @param humanOut
     * @param verbose
     * @throws IOException
     */
    public void writeTo(OutputStream out, Writer humanOut, boolean verbose) throws IOException{
        mDexFile.writeTo(out, humanOut, verbose);
    }
}
