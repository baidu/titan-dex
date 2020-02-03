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

package com.baidu.titan.dex.tests;

import com.baidu.titan.dex.node.MultiDexFileNode;
import com.baidu.titan.dex.reader.MultiDexFileReader;
import com.baidu.titan.dex.writer.MultiDexFileWriter;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/9/15
 */

public class MultidexWriterTest {


    static byte[] getFileContent(File f) throws IOException {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] buf = new byte[16 * 1024];
            int len;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((len = in.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Test
    public void test() throws IOException {
        File testBase = new File("D:\\titan-v2\\writer");

        File inBase = new File(testBase, "input");
        File outBase = new File(testBase, "output");

        MultiDexFileReader mdfr = new MultiDexFileReader();

        mdfr.addDexContent(1, getFileContent(new File(inBase, "classes.dex")));
        mdfr.addDexContent(2, getFileContent(new File(inBase, "classes2.dex")));
        mdfr.addDexContent(3, getFileContent(new File(inBase, "classes3.dex")));
        mdfr.addDexContent(4, getFileContent(new File(inBase, "classes4.dex")));

        MultiDexFileNode mdfn = new MultiDexFileNode();
        mdfr.accept(mdfn.asVisitor());

        MultiDexFileWriter mdfw = new MultiDexFileWriter();

        mdfn.accept(mdfw);

        Map<Integer, byte[]> dexs = null;
                //mdfw.getMultiDexContent();
        outBase.mkdirs();
        for (Map.Entry<Integer, byte[]> entry : dexs.entrySet()) {
            File outDex = new File(outBase, entry.getKey() == 1 ? "classes.dex" : "classes" + entry.getKey() + ".dex");
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outDex);
                fos.write(entry.getValue());
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }
        }
    }

}
