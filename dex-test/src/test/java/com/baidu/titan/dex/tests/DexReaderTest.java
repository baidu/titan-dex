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

import com.baidu.titan.dex.reader.DexFileReader;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/3/28
 */

public class DexReaderTest {

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
        }  finally {
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
        File testBase = new File(new File(".").getParentFile(), "titan-test");

        File dexFile = new File(testBase, "classes.dex");
        System.out.println("dexFile Path = " + dexFile.getAbsolutePath());
        DexFileReader reader = new DexFileReader(getFileContent(dexFile));



    }
}
