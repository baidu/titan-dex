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

import com.baidu.titan.dex.DexItemFactory;
import com.baidu.titan.dex.node.DexClassNode;
import com.baidu.titan.jvm.reader.JvmClassFileReader;
import com.google.common.io.ByteStreams;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/11/4
 */

public class JvmClassFileReaderTest {

    @Test
    public void read() throws Exception{
        File testBase = new File("D:\\titan-v2\\jar");

        File androidJar = new File(testBase, "android.jar");

        DexItemFactory factory = new DexItemFactory();

        ZipInputStream stream = null;
        try {
            stream = new ZipInputStream(new FileInputStream(androidJar));
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                if (entry.getName().endsWith(".class")) {
                    byte[] content = ByteStreams.toByteArray(stream);
                    JvmClassFileReader reader = new JvmClassFileReader(content, factory);
                    DexClassNode dcn = reader.read();
                    System.out.println("processed " + dcn.type.toTypeDescriptor());
                }
            }
        } finally {

        }
    }

}
