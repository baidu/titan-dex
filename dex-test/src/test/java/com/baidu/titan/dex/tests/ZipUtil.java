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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * @author zhangdi07@baidu.com
 * @since 2018/1/28
 */
public class ZipUtil {

    public static Map<Integer, byte[]> getDexContentsFromApk(File apkFile) {
        Map<Integer, byte[]> result = new HashMap<>();
        ZipFile zip = null;
        try {
            zip = new ZipFile(apkFile);
            byte[] dex1 = getZipEntryContent(zip, "classes.dex");
            if (dex1 != null) {
                result.put(1, dex1);
            }
            int i = 2;
            while(true) {
                byte[] dexN = getZipEntryContent(zip, "classes" + i + ".dex");
                if (dexN != null) {
                    result.put(i, dexN);
                } else {
                    break;
                }
                i++;
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }

        return result;
    }


    private static byte[] getZipEntryContent(ZipFile zipFile, String entryName) throws IOException {
        InputStream in = null;
        try {
            ZipEntry zipEntry = zipFile.getEntry(entryName);
            if (zipEntry == null) {
                return null;
            }
            System.out.println("[Process Zip Entry] " + entryName);
            in = zipFile.getInputStream(zipEntry);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }

            return baos.toByteArray();


//            File outDir = new File(baseOutDir, new File(zipFile.getName()).getName());
//            outDir.mkdirs();
//            boolean appendRuntime = "classes.dex".equals(entryName);
//            //new File(outDir, entryName).mkdirs();
//            FileOutputStream fo = new FileOutputStream(new File(outDir, entryName));
//            TitanInstrument.instrumentDex(baos.toByteArray(), fo);

//            return true;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {

                }
            }
        }

    }


    public static void writeDexBytesToApk(File apkFile, Map<Integer, byte[]> dexBytes)
            throws Exception {
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new FileOutputStream(apkFile));

            for (Map.Entry<Integer, byte[]> dexItem : dexBytes.entrySet()) {
                zos.putNextEntry(new ZipEntry(dexItem.getKey() == 1 ?
                        "classes.dex" : "classes" + dexItem.getKey() + ".dex"));
                zos.write(dexItem.getValue());
            }
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
