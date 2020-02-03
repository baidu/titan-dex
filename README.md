# Titan-Dex字节码操作框架

## 描述
Titan-Dex是面向Android Dalvik(ART)字节码（bytecode）格式的操纵框架，可以在二进制格式下实现修改已有的类，或者动态生成新的类。  
功能类似于著名的针对JVM字节码格式的[ASM](https://asm.ow2.io/)框架，在Android平台上从JVM Class格式到Dex格式要经过dx、d8等工具的转换，
有些情况下会造成期望结果的失真，而Titan-Dex针对Dalvik(ART)字节码格式，可以所操纵就所得的效果，在有些场景下更加适合，比如在Titan-Hotfix,还有classes.dex体积分析与优化等项目中就使用了Titan-Dex。  
基于Titan-Dex最基础的操纵框架能力，还包含了一系列的字节码转换、代码流图分析、multi-dex分包等工具集合。

## 特性
* 支持Dex最新格式，提供Node和Visitor两种模式，方便操纵Dex元素  
* 支持读取和输出smali格式  
* 支持字节码指令流图分析功能，可以校验字节码类型安全性，以及获取pc偏移出的寄存器类型集合  
* 支持基于Titan-Dex的多种扩展，比如multi-dex分包能力等。

## 快速开始
使用上非常类似于[ASM框架](https://asm.ow2.io/asm4-guide.pdf)，如果需要修改或者新增Dalvki(ART)字节码，请同时参考[Android官方文档](https://source.android.com/devices/tech/dalvik/dalvik-bytecode)

最新版本已经发布到jcenter上

```
implementation("com.baidu.titan.dex:dex-core:1.0.6")
implementation("com.baidu.titan.dex:dex-io:1.0.6")
```

## 使用示例

```java
DexItemFactory dexFactory = new DexItemFactory();

MultiDexFileBytes mdfb = MultiDexFileBytes.createFromZipFile(new File("test.apk"));

// dex reader
MultiDexFileReader mdReader = new MultiDexFileReader(dexFactory);
mdfb.forEach((dexId, dexBytes) -> {
    mdReader.addDexContent(dexId, dexBytes.getDexFileBytes());
});
mdReader.accept(new MultiDexFileVisitor() {

    @Override
    public DexFileVisitor visitDexFile(int dexId) {
        return new DexFileVisitor() {
            @Override
            public DexClassVisitor visitClass(DexClassVisitorInfo classInfo) {
                return new DexClassVisitor() {
                    @Override
                    public void visitBegin() {
                    }

                    @Override
                    public void visitSourceFile(DexString sourceFile) {
                    }
                    @Override
                    public DexAnnotationVisitor visitAnnotation(DexAnnotationVisitorInfo annotationInfo) {
                        return super.visitAnnotation(annotationInfo);
                    }

                    @Override
                    public DexFieldVisitor visitField(DexFieldVisitorInfo fieldInfo) {
                        return super.visitField(fieldInfo);
                    }

                    @Override
                    public DexMethodVisitor visitMethod(DexMethodVisitorInfo methodInfo) {
                        return super.visitMethod(methodInfo);
                    }

                    @Override
                    public void visitEnd() {
                    }
                };
            }
        };
    }
});


// dex node
MultiDexFileNode mdfn = new MultiDexFileNode();
mdReader.accept(mdfn.asVisitor());
Map<Integer, DexFileNode> dexFiles = mdfn.getDexNodes();
dexFiles.forEach((dexId, dexFileNode) -> {
    List<DexClassNode> classes = dexFileNode.getClassesList();
    // ...
});

```

## 测试
在dex-test/src/test目录下编写了多个测试用例，可以通过命令行，或者在android studio中执行测试

## 如何贡献
欢迎一起参与改进Titan-Dex！  
提交的代码需通过code style检查，完成自测，有相应的单元测试用例。

## License
This project is licensed under the Apache-2.0 license - see the LICENSE file for details