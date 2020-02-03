package com.baidu.titan.dexlib.dex;

/**
 * Created by zhangdi on 2016/12/6.
 */
public class AnnotationSetItem {
    private int[] mEntries;

    public AnnotationSetItem(int[] entries) {
        this.mEntries = entries;
    }

    public int[] getAnnotationEntries() {
        return mEntries;
    }

}
