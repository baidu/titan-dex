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

import com.baidu.titan.dex.smali.SmaliReader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class VerifySmaliTest {

    private File testDir;
    private File outRootDir;
    private String testName;

    public VerifySmaliTest(File testDir, File outRootDir, String testName) {
        this.testDir = testDir;
        this.outRootDir = outRootDir;
        this.testName = testName;
    }

    @Parameterized.Parameters(name = "{2}")
    public static Collection<Object[]> data() {
        File file = new File(".").getAbsoluteFile().getParentFile().getParentFile().getParentFile();
        File outRootDir = new File(file, "titan-core/build/test/out/lightpatch");
        File[] testFiles = outRootDir.listFiles((dir, name) -> name.startsWith("t"));

        List<Object[]> data = Arrays.stream(testFiles)
                .sorted()
                .map(testDir -> new Object[]{testDir, outRootDir, testDir.getName()})
                .collect(Collectors.toList());
        return data;
    }

    @Test
    public void testVerifySmali() throws IOException {
        File outDir = new File(outRootDir, testDir.getName());
        outDir.mkdirs();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"), "titan-test");
        tmpDir.mkdirs();

        System.out.println("begin test for " + testName);

        SmaliReader smaliReader = new SmaliReader(
                SmaliReader.SmaliPath.createFromDir(outDir), tmpDir);

        smaliReader.toDexFileBytes();
    }
}
