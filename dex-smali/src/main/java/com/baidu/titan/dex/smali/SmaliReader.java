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

package com.baidu.titan.dex.smali;

import com.baidu.titan.dex.node.DexFileNode;
import com.baidu.titan.dex.reader.DexFileReader;
import com.baidu.titan.dex.visitor.DexFileVisitor;

import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 用于读取解析smali文本文件，转换成Dex Visitor或Dex Node模式
 *
 * @author zhangdi07@baidu.com
 * @since 2018/1/9
 */
public class SmaliReader {

    private List<SmaliPath> mPaths;

    private File mTmpDir;

    public static class SmaliPath {

        private static final int TYPE_SMALI_FILE = 0;

        private static final int TYPE_SMALI_DIR = 1;

        private final int mType;

        private File mSmaliFile;

        private File mSmaliDir;

        private SmaliPath(int type) {
            this.mType = type;
        }

        public File getSmaliFile() {
            return mSmaliFile;
        }

        public File getSmaliDir() {
            return mSmaliDir;
        }

        public int getType() {
            return mType;
        }

        public static SmaliPath createFromFile(File smaliFile) {
            SmaliPath path = new SmaliPath(TYPE_SMALI_FILE);
            path.mSmaliFile = smaliFile;
            return path;
        }

        public static SmaliPath createFromDir(File smaliDir) {
            SmaliPath path = new SmaliPath(TYPE_SMALI_DIR);
            path.mSmaliDir = smaliDir;
            return path;
        }

    }

    public SmaliReader(SmaliPath path, File tmpDir) {
        this.mPaths = new ArrayList<>();
        this.mPaths.add(path);
        this.mTmpDir = tmpDir;
    }

    public SmaliReader(List<SmaliPath> paths, File tmpDir) {
        this.mPaths = paths;
        this.mTmpDir = tmpDir;
    }

    public byte[] toDexFileBytes() throws  IOException {
        List<String> inputs = new ArrayList<>();

        for (SmaliPath path : mPaths) {
            switch (path.getType()) {
                case SmaliPath.TYPE_SMALI_DIR: {
                    inputs.add(path.getSmaliDir().getAbsolutePath());
                    break;
                }
                case SmaliPath.TYPE_SMALI_FILE: {
                    inputs.add(path.getSmaliFile().getAbsolutePath());
                    break;
                }
                default: {
                    break;
                }
            }
        }

        File tmpDexFile = File.createTempFile("smali", ".dex", this.mTmpDir);
        SmaliOptions so = new SmaliOptions();
        so.outputDexFile = tmpDexFile.getAbsolutePath();
        Smali.assemble(so, inputs);
        return fileToByteArray(tmpDexFile);
    }


    private byte[] fileToByteArray(File file) throws IOException {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {

                }
            }
        }
    }

    public DexFileNode toDexFileNode() throws IOException {
        DexFileNode dfn = new DexFileNode();
        accept(dfn.asVisitor());
        return dfn;
    }

    public void accept(DexFileVisitor dfv) throws IOException {
        DexFileReader dfr = new DexFileReader(toDexFileBytes());
        dfr.accept(dfv);
    }

}