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

import com.baidu.titan.dex.util.ZipUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 多Dex读取和存储工具类
 *
 * @author zhangdi07@baidu.com
 * @since 2018/5/15
 */
public class MultiDexFileBytes implements Iterable<MultiDexFileBytes.Entry> {

    private Map<Integer, DexFileBytes> mMultiDexFileBytes =
            Collections.synchronizedMap(new TreeMap<>());

    public boolean addDexFileBytes(int dexId, DexFileBytes dexFileBytes) {
        return this.mMultiDexFileBytes.put(dexId, dexFileBytes) == null;
    }

    public DexFileBytes getDexFileBytes(int dexId) {
        return this.mMultiDexFileBytes.get(dexId);
    }

    public boolean isValid() {
        if (mMultiDexFileBytes.size() == 0) {
            return false;
        }

        int nextDexId = 1;
        for (Entry entry : this) {
            if (entry.dexId != nextDexId) {
                return false;
            }
            nextDexId++;
        }
        return true;
    }

    public static MultiDexFileBytes createFromOrderedDexBytes(byte[]... dexBytes) {
        MultiDexFileBytes multiDexFileBytes = new MultiDexFileBytes();
        int dexId = 1;
        for (byte[] dexContents : dexBytes) {
            multiDexFileBytes.addDexFileBytes(dexId++, new DexFileBytes(dexContents));
        }
        return multiDexFileBytes;
    }

    public static MultiDexFileBytes createFromZipFile(File zipFile) {
        MultiDexFileBytes multiDexFileBytes = new MultiDexFileBytes();
        Map<Integer, byte[]> dexBytes = ZipUtil.getDexContentsFromZipFile(zipFile);
        dexBytes.forEach((dexId, content) -> {
            multiDexFileBytes.addDexFileBytes(dexId, new DexFileBytes(content));
        });
        return multiDexFileBytes;
    }

    public static MultiDexFileBytes createFromDirectory(File dexDir) {
        MultiDexFileBytes multiDexFileBytes = new MultiDexFileBytes();
        byte[] dex1Bytes = getFileBytes(new File(dexDir, "classes.dex"));
        if (dex1Bytes != null) {
            multiDexFileBytes.addDexFileBytes(1, new DexFileBytes(dex1Bytes));

            int nextDexid = 2;
            while (true) {
                byte[] dexNBytes = getFileBytes(new File(dexDir, "classes" + nextDexid + ".dex"));
                if (dexNBytes != null) {
                    multiDexFileBytes.addDexFileBytes(nextDexid, new DexFileBytes(dexNBytes));
                } else {
                    break;
                }
                nextDexid++;
            }
        }

        return multiDexFileBytes;

    }

    private static byte[] getFileBytes(File file) {
        if (file.exists()) {
            FileInputStream in = null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                in = new FileInputStream(file);
                byte[] buf = new byte[16 * 1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    baos.write(buf, 0, len);
                }
                return baos.toByteArray();
            } catch (IOException e) {

            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {

                    }
                }
            }
        }

        return null;
    }

    public static class Entry {

        public int dexId;

        public DexFileBytes dexFileBytes;

        public Entry(int dexId, DexFileBytes dexFileBytes) {
            this.dexId = dexId;
            this.dexFileBytes = dexFileBytes;
        }

        public int getDexId() {
            return this.dexId;
        }

        public DexFileBytes getDexFileBytes() {
            return dexFileBytes;
        }

        public String getDexFileName() {
            return this.dexId == 1 ? "classes.dex" : "classes" + this.dexId + ".dex";
        }

    }

    @Override
    public Iterator<Entry> iterator() {

        return new Iterator<Entry>() {

            private Iterator<Map.Entry<Integer, DexFileBytes>> entriesIterator =
                    mMultiDexFileBytes.entrySet().iterator();

            @Override
            public boolean hasNext() {
                return entriesIterator.hasNext();
            }

            @Override
            public Entry next() {
                Map.Entry<Integer, DexFileBytes> mapEntry = entriesIterator.next();
                return new Entry(mapEntry.getKey(), mapEntry.getValue());
            }
        };

    }


    public void forEach(BiConsumer<Integer, DexFileBytes> action) {
        Objects.requireNonNull(action);
        forEach(e -> {
            action.accept(e.dexId, e.dexFileBytes);
        });
    }

    public void writeToDir(File dir) throws IOException {
        dir.mkdirs();
        for (Entry entry : this) {
            File dexFile = new File(dir, entry.getDexFileName());
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(dexFile);
                out.write(entry.dexFileBytes.getDexFileBytes());
            } finally {
                if (out != null) {
                    out.close();
                }
            }
        }
    }

    public void writeToZipFile(File zipFile) throws IOException {
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new FileOutputStream(zipFile));
            for (Entry entry : this) {
                zos.putNextEntry(new ZipEntry(entry.getDexFileName()));

                zos.write(entry.dexFileBytes.getDexFileBytes());
                zos.closeEntry();
            }
        } finally {
            if (zos != null) {
                zos.close();
            }
        }
    }


}
