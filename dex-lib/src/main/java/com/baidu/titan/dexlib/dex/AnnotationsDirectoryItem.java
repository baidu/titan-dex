package com.baidu.titan.dexlib.dex;

/**
 * Created by zhangdi on 2016/12/6.
 */
public class AnnotationsDirectoryItem {
    private int mClassAnnotationOffset;
    private FieldAnnotation[] mFieldAnnotations;
    private MethodAnnotation[] mMethodAnnotations;
    private ParameterAnnotation[] mParameterAnnotations;
    static AnnotationsDirectoryItem createFromSection(Dex.Section section) {
        return new AnnotationsDirectoryItem(section);
    }

    AnnotationsDirectoryItem(Dex.Section section) {
        mClassAnnotationOffset = section.readInt();
        int fieldAnnotationSize = section.readInt();
        int methodAnnotationSize = section.readInt();
        int parametersAnnotationSize = section.readInt();

        if (fieldAnnotationSize > 0) {
            mFieldAnnotations = new FieldAnnotation[fieldAnnotationSize];
            for (int i = 0; i < fieldAnnotationSize; i++) {
                int fieldIdx = section.readInt();
                int fieldAnnotaionOff = section.readInt();
                mFieldAnnotations[i] = new FieldAnnotation(fieldIdx, fieldAnnotaionOff);
            }
        }

        if (methodAnnotationSize > 0) {
            mMethodAnnotations = new MethodAnnotation[methodAnnotationSize];
            for (int i = 0; i < methodAnnotationSize; i++) {
                int methodIdx = section.readInt();
                int methodAnnotaionOff = section.readInt();
                mMethodAnnotations[i] = new MethodAnnotation(methodIdx, methodAnnotaionOff);
            }
        }

        if (parametersAnnotationSize > 0) {
            mParameterAnnotations = new ParameterAnnotation[parametersAnnotationSize];
            for (int i = 0; i < parametersAnnotationSize; i++) {
                int methodIdx = section.readInt();
                int methodAnnotaionOff = section.readInt();
                mParameterAnnotations[i] = new ParameterAnnotation(methodIdx, methodAnnotaionOff);
            }
        }
    }


    public static class FieldAnnotation {
        private int mFieldIndex;
        private int mAnnotationOffeset;

        FieldAnnotation(int fieldIdx, int annotationOff) {
            this.mFieldIndex = fieldIdx;
            this.mAnnotationOffeset = annotationOff;
        }

        public int getFieldIndex() {
            return mFieldIndex;
        }

        public int getAnnotationOffeset() {
            return mAnnotationOffeset;
        }
    }

    public static class MethodAnnotation {
        private int mMethodIndex;
        private int mAnnotationOffeset;

        MethodAnnotation(int methodIdx, int annotationOff) {
            this.mMethodIndex = methodIdx;
            this.mAnnotationOffeset = annotationOff;
        }

        public int getMethodIndex() {
            return mMethodIndex;
        }

        public int getAnnotationOffeset() {
            return mAnnotationOffeset;
        }
    }

    public static class ParameterAnnotation {
        private int mMethodIndex;
        private int mAnnotationOffeset;

        ParameterAnnotation(int methodIdx, int annotationOff) {
            this.mMethodIndex = methodIdx;
            this.mAnnotationOffeset = annotationOff;
        }

        public int getMethodIndex() {
            return mMethodIndex;
        }

        public int getAnnotationOffeset() {
            return mAnnotationOffeset;
        }
    }




    public int getClassAnnotationsOffeset() {
        return this.mClassAnnotationOffset;
    }

    public FieldAnnotation[] getFieldAnnotations() {
        return this.mFieldAnnotations;
    }

    public MethodAnnotation[] getMethodAnnotations() {
        return this.mMethodAnnotations;
    }

    public ParameterAnnotation[] getParameterAnnotations() {
        return mParameterAnnotations;
    }

}
