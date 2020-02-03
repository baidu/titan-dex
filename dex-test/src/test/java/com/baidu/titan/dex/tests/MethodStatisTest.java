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

import com.baidu.titan.dex.reader.MultiDexFileReader;
import com.baidu.titan.dex.writer.MultiDexFileWriter;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/9/15
 */

public class MethodStatisTest {


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
        File testBase = new File("D:\\titan-v2\\method");

        File inBase = new File(testBase, "input");
        File outBase = new File(testBase, "output");

        MultiDexFileReader mdfr = new MultiDexFileReader();

        mdfr.addDexContent(1, getFileContent(new File(inBase, "classes.dex")));
        mdfr.addDexContent(2, getFileContent(new File(inBase, "classes2.dex")));
        mdfr.addDexContent(3, getFileContent(new File(inBase, "classes3.dex")));
        mdfr.addDexContent(4, getFileContent(new File(inBase, "classes4.dex")));

        MultiDexFileWriter mdfw = new MultiDexFileWriter();
//        final MultiDexFileNode mdfn = new MultiDexFileNode(0);
//        mdfr.accept(mdfn);
//
//        mdfn.accept(new MultiDexFileNodeVisitor() {
//            @Override
//            public void visitDexFile(int dexId, DexFileNode dfn) {
//                for (DexClassNode dcn: dfn.getClassesList()) {
//                    dcn.accept(new DexClassNodeVisitor() {
//                        @Override
//                        public void visitClassAnnotation(DexAnnotationNode dan) {
//
//                        }
//
//                        @Override
//                        public void visitMethod(DexMethodNode dmn) {
//                            if (dmn.getDexMethodInfo().getName().equals("<init>")) {
//                                DexCodeNode dcn = dmn.getCode();
//                                if (dcn != null) {
//                                    int invokeSuperIdx;
//                                    boolean hasIput = false;
//                                    List<DexInsnNode> newInsns = new ArrayList<DexInsnNode>();
//                                    for (DexInsnNode din: dcn.getInsns()) {
//                                        if(!(din instanceof DexPseudoInsnNode)) {
//                                            newInsns.add(din);
//                                        }
//                                    }
//
//
//                                    for (int i = 0; i < newInsns.size(); i++) {
//                                        DexInsnNode din = newInsns.get(i);
//                                        if (din instanceof DexConstInsnNode) {
//                                            DexConst dc = ((DexConstInsnNode)din).getConst();
//                                            if (dc instanceof DexConst.ConstMethodRef) {
//                                                DexConst.ConstMethodRef methodRef = (DexConst.ConstMethodRef)dc;
//                                                if(methodRef.getName().equals("<init>")) {
//                                                    invokeSuperIdx = i;
//                                                    if (invokeSuperIdx > 1 && hasIput && !dmn.getDexMethodInfo().getOwner().contains("$")) {
//                                                        System.out.println(dmn.getDexMethodInfo());
//                                                    }
//                                                    break;
//                                                }
//                                            } else if (dc instanceof DexConst.ConstFieldRef) {
//                                                switch (((DexOpcodeInsnNode) din).getOpcode()) {
//                                                    case DexConstant.Opcodes.IPUT:
//                                                    case DexConstant.Opcodes.IPUT_BOOLEAN:
//                                                    case DexConstant.Opcodes.IPUT_OBJECT:
//                                                    case DexConstant.Opcodes.IPUT_CHAR:
//                                                    case DexConstant.Opcodes.IPUT_SHORT:
//                                                    case DexConstant.Opcodes.IPUT_WIDE: {
//                                                        hasIput = true;
//                                                    }
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//
//                        @Override
//                        public void visitField(DexFieldNode dfn) {
//
//                        }
//                    });
//                }
//            }
//        });
    }

}
