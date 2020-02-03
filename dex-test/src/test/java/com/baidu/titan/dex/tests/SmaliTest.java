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
import com.baidu.titan.dex.node.DexFileNode;
import com.baidu.titan.dex.node.MultiDexFileNode;
import com.baidu.titan.dex.reader.MultiDexFileReader;
import com.baidu.titan.dex.visitor.DexClassPoolNodeVisitor;
import com.baidu.titan.dex.visitor.MultiDexFileNodeVisitor;
import com.google.common.io.Files;

import org.junit.Test;

import java.io.File;
import java.nio.file.spi.FileSystemProvider;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @author zhangdi07@baidu.com
 * @since 2018/2/1
 */
public class SmaliTest {

    @Test
    public void test2() {
        FileSystemProvider.installedProviders().forEach(p -> {
            System.out.println(p.getScheme());
        });
    }

    @Test
    public void test() throws Exception {
        File inputDir = new File("D:\\titan-v2\\20180117\\org-dex");
        File baseOutDir = new File("D:\\titan-v2\\20180117\\org-dex\\out\\" +
                new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        baseOutDir.mkdirs();

        for (File apkOrDex: inputDir.listFiles()) {
            if (!apkOrDex.isDirectory()) {
                if (apkOrDex.getName().endsWith(".dex")) {
                    System.out.println("[process file] " + apkOrDex.getAbsolutePath());
//                    processBytes(getFileContent(apkOrDex.getAbsolutePath()));
                } else if (apkOrDex.getName().endsWith(".apk")){
                    System.out.println("[process apk] " + apkOrDex.getAbsolutePath());
                    Map<Integer, byte[]> dexBytes = ZipUtil.getDexContentsFromApk(apkOrDex);

                    DexItemFactory factory = new DexItemFactory();

                    MultiDexFileReader mfr = new MultiDexFileReader(factory);

                    dexBytes.forEach((idx, dexContent) -> {
                        mfr.addDexContent(idx, dexContent);
                    });

                    MultiDexFileNode mdfn = new MultiDexFileNode();

                    mfr.accept(mdfn.asVisitor());

                    mdfn.accept(new MultiDexFileNodeVisitor() {

                        @Override
                        public void visitDexFile(int dexId, DexFileNode dfn) {
                            dfn.accept(new DexClassPoolNodeVisitor() {

                                @Override
                                public void visitClass(DexClassNode dcn) {
                                    String smaliString = dcn.toString();

                                    String smaliFileName = dcn.type.toTypeDescriptor()
                                            .replaceAll("/", "_");
                                    smaliFileName = smaliFileName.substring(1, smaliFileName
                                            .length() - 1);
                                    smaliFileName += ".smali";


                                    File outFile = new File(baseOutDir, smaliFileName);
                                    try {
                                        Files.write(smaliString.getBytes("utf-8"), outFile);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }


//                                    System.out.println(smaliString);
                                }

                                @Override
                                public void classPoolVisitEnd() {

                                }

                            });
                        }
                    });



                }
            }
        }
        System.out.println("done");
    }


}
