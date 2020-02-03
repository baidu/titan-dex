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

package com.baidu.titan.dex.linker;

import com.baidu.titan.dex.DexConst;
import com.baidu.titan.dex.DexItemFactory;
import com.baidu.titan.dex.DexString;
import com.baidu.titan.dex.DexType;
import com.baidu.titan.dex.DexTypeList;
import com.baidu.titan.dex.extensions.DexInterfacesHierarchyFiller;
import com.baidu.titan.dex.extensions.DexSuperClassHierarchyFiller;
import com.baidu.titan.dex.node.DexClassNode;
import com.baidu.titan.dex.node.DexFieldNode;
import com.baidu.titan.dex.node.DexMethodNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 负责类、方法、字段等元素的link机制
 *
 * @author zhangdi07@baidu.com
 * @since 2018/1/9
 */
public class ClassLinker {

    private DexItemFactory mFactory;

    public ClassLinker(DexItemFactory factory) {
        this.mFactory = factory;
    }


    public DexFieldNode resolveFieldJLS(DexType owner,
                                        DexString name,
                                        DexType fieldType,
                                        DexClassLoader loader) {
        DexClassNode classNode = loader.findClass(owner);
        for (DexClassNode cur = classNode; cur != null; cur = findSuperClassNode(cur, loader)) {
            DexFieldNode fieldNode = findDeclaredField(cur, name, fieldType);
            if (fieldNode != null) {
                return fieldNode;
            }
        }
        return null;
    }

    public DexFieldNode resolveFieldJLS(DexClassLoader loader, DexConst.ConstFieldRef fieldRef) {
        DexClassNode dcn = resolveType(fieldRef.getOwner(), loader);
        return findField(dcn, fieldRef.getType(), fieldRef.getName());
    }

    private DexFieldNode findField(DexClassNode dcn, DexType fieldType, DexString fieldName) {
        DexFieldNode result = null;
        for (DexClassNode c = dcn; c != null; c = DexSuperClassHierarchyFiller.getSuperClass(c)) {
            result = findDeclaredField(c, fieldName, fieldType);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private DexFieldNode findDeclaredField(DexClassNode dcn, DexString name, DexType type) {
        for (DexFieldNode dfn : dcn.getFields()) {
            if (dfn.type.equals(type) && dfn.name.equals(name)) {
                return dfn;
            }
        }
        return null;
    }

    public DexClassNode resolveType(DexType type, DexClassLoader loader) {
        return loader.findClass(type);
    }

    public boolean isAssignableFrom(DexType testSuperType,
                                    DexType testSubType,
                                    DexClassLoader loader) {
        if (testSuperType.equals(testSubType)) {
            return true;
        } else if (testSuperType.equals(mFactory.objectClass.type)) {
            return testSubType.isArrayType() || testSubType.isReferenceType();
        } else {
            DexClassNode testSubClass = loader.findClass(testSubType);
            DexClassNode testSuperClass = loader.findClass(testSubType);
            if (testSubClass == null ||testSubClass == null) {
                return false;
            }
            if (testSubClass.isInterface()) {
                return implementInterfaces(testSuperType, testSubType, loader);
            } else if (testSubType.isArrayType()) {
                throw new UnsupportedOperationException("not implement now");
            } else {
                return !testSubClass.isInterface() && isSubClass(testSuperType, testSubType, loader);
            }
        }
    }

    public boolean isAssignableFrom(DexClassNode testSuperClass, DexClassNode testSubClass,
                                    DexClassLoader loader) {
        if (testSuperClass.type.equals(testSubClass.type)) {
            return true;
        } else if (testSuperClass.type.equals(mFactory.objectClass.type)) {
            return testSubClass.type.toShortDescriptor() == 'L' ||
                    testSubClass.type.toShortDescriptor() == '[';
        } else if (testSuperClass.isInterface()) {
            return implementInterfaces(testSuperClass, testSubClass);
        } else if (testSubClass.type.toTypeDescriptor().charAt(0) == '[') { // array class
            throw new RuntimeException("not implement");
        } else {
            return !testSubClass.isInterface() && isSubClass(testSuperClass, testSubClass);
        }
    }

    public boolean implementInterfaces(DexType itf, DexType type, DexClassLoader loader) {
        return getInterfaceTable(type, loader).contains(itf);
    }

    public boolean implementInterfaces(DexClassNode itf, DexClassNode dcn) {
        return getInterfaceTable(dcn).containsKey(itf.type);
    }

    public Set<DexType> getInterfaceTable(DexType type, DexClassLoader loader) {
        Set<DexType> itfResult = new HashSet<>();

        DexClassNode classNode = loader.findClass(type);

        if (classNode == null) {
            return itfResult;
        }

        DexType superType = classNode.superType;

        DexClassNode superClassNode = loader.findClass(superType);

        if (superClassNode != null) {
            Set<DexType> itfsFromSuper = getInterfaceTable(superType, loader);
            if (itfsFromSuper != null) {
                itfsFromSuper.forEach(t -> itfResult.add(t));
            }
        }

        DexTypeList interfaces = classNode.interfaces;
        if (interfaces != null) {
            interfaces.forEach(itf -> {
                itfResult.add(itf);
                getInterfaceTable(itf, loader).forEach(itfFromSuper -> itfResult.add(itfFromSuper));
            });
        }

        return itfResult;
    }

    public Map<DexType, DexClassNode> getInterfaceTable(DexClassNode dcn) {
        Map<DexType, DexClassNode> itfResult = new HashMap<>();


        DexClassNode superClassNode = DexSuperClassHierarchyFiller.getSuperClass(dcn);

        if (superClassNode != null) {
            Map<DexType, DexClassNode> itfsFromSuper = getInterfaceTable(superClassNode);
            if (itfsFromSuper != null) {
                for (Map.Entry<DexType, DexClassNode> entry : itfsFromSuper.entrySet()) {
                    itfResult.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }

        List<DexClassNode> directItfs = DexInterfacesHierarchyFiller.getInterfaces(dcn);
        if (directItfs != null) {
            directItfs.forEach(itf -> {
                itfResult.putIfAbsent(itf.type, itf);
                Map<DexType, DexClassNode> itfsFromItf = getInterfaceTable(superClassNode);
                itfsFromItf.forEach((t, c) -> {itfResult.putIfAbsent(t, c);});
            });
        }
        return itfResult;
    }

    public boolean isSubClass(DexType superType, DexType testType, DexClassLoader loader) {
        DexType currentType = testType;
        while (true) {
            if (currentType.equals(superType)) {
                return true;
            }

            DexClassNode currentClass = loader.findClass(currentType);
            if (currentClass == null) {
                return false;
            }
            currentType = currentClass.superType;
            if (currentType == null) {
                return false;
            }
        }
    }

    public boolean isSubClass(DexClassNode superDcn, DexClassNode testDcn) {
        for (DexClassNode c = testDcn; c != null; c = DexSuperClassHierarchyFiller.getSuperClass(c)) {
            if (superDcn.type.equals(c.type)) {
                return true;
            }
        }
        return false;
    }

    public DexMethodNode findDirectMethod(DexClassNode classNode,
                                          DexTypeList param,
                                          DexType returnType,
                                          DexString name) {
        for (DexClassNode cur = classNode; cur != null;
             cur = DexSuperClassHierarchyFiller.getSuperClass(cur)) {
            DexMethodNode dmn = findDeclaredDirectMethod(cur, param, returnType, name);
            if (dmn != null) {
                return dmn;
            }
        }
        return null;
    }

    public DexMethodNode findDirectMethod(DexType classType,
                                          DexTypeList param,
                                          DexType returnType,
                                          DexString name,
                                          DexClassLoader loader) {
        DexClassNode classNode = loader.findClass(classType);

        for (DexClassNode cur = classNode; cur != null; cur = findSuperClassNode(cur, loader)) {
            DexMethodNode dmn = findDeclaredDirectMethod(cur, param, returnType, name);
            if (dmn != null) {
                return dmn;
            }
        }
        return null;
    }

    private DexClassNode findSuperClassNode(DexClassNode classNode, DexClassLoader loader) {
        DexType superType = classNode.superType;
        return loader.findClass(superType);
    }

    public DexMethodNode findVirtualMethod(DexType classType,
                                           DexTypeList param,
                                           DexType returnType,
                                           DexString name,
                                           DexClassLoader loader) {
        DexClassNode classNode = loader.findClass(classType);

        for (DexClassNode cur = classNode; cur != null; cur = findSuperClassNode(cur, loader)) {
            DexMethodNode dmn = findDeclaredVirtualMethod(cur, param, returnType, name, loader);
            if (dmn != null) {
                return dmn;
            }
        }
        return null;
    }

    public DexMethodNode findVirtualMethod(DexClassNode classNode,
                                           DexTypeList param,
                                           DexType returnType,
                                           DexString name) {
        for (DexClassNode cur = classNode; cur != null;
             cur = DexSuperClassHierarchyFiller.getSuperClass(cur)) {
            DexMethodNode dmn = findDeclaredVirtualMethod(cur, param, returnType, name);
            if (dmn != null) {
                return dmn;
            }
        }
        return null;
    }

    public DexMethodNode findInterfaceMethod(DexType classType,
                                             DexTypeList param,
                                             DexType returnType,
                                             DexString name,
                                             DexClassLoader loader) {
        DexClassNode classNode = loader.findClass(classType);
        DexMethodNode method = findDeclaredVirtualMethod(classNode, param, returnType, name, loader);
        if (method != null) {
            return method;
        }
        Set<DexType> itfs = getInterfaceTable(classNode.type, loader);
        if (itfs != null) {
            for (DexType itf : itfs) {
                DexClassNode interfaceNode = loader.findClass(itf);
                if (interfaceNode != null) {
                    method = findDeclaredVirtualMethod(interfaceNode, param, returnType, name, loader);
                    if (method != null) {
                        return method;
                    }
                }

            }
        }
        return null;
    }

    public DexMethodNode findInterfaceMethod(DexClassNode classNode,
                                             DexTypeList param,
                                             DexType returnType,
                                             DexString name) {
        DexMethodNode method = findDeclaredVirtualMethod(classNode, param, returnType, name);
        if (method != null) {
            return method;
        }
        Map<DexType, DexClassNode> itfs = getInterfaceTable(classNode);
        if (itfs != null) {
            for (DexClassNode itf : itfs.values()) {
                method = findDeclaredVirtualMethod(itf, param, returnType, name);
                if (method != null) {
                    return method;
                }
            }
        }
        return null;
    }



    private DexMethodNode findDeclaredDirectMethod(DexClassNode classNode,
                                                   DexTypeList param,
                                                   DexType returnType,
                                                   DexString name) {
        Optional<DexMethodNode> optional = classNode.getMethods().stream()
                .filter(m -> {
                    if (!name.equals(m.name)) {
                        return false;
                    }
                    if (!param.equals(m.parameters)) {
                        return false;
                    }
                    if (!returnType.equals(m.returnType)) {
                        return false;
                    }
                    return true;
                })
                .findFirst();

        if (optional.isPresent()) {
            return optional.get();
        }
        return null;
    }

    private Map<DexType, DexClassNode> getDirectInterfaceTable(DexClassNode classNode) {
        Map<DexType, DexClassNode> result = new HashMap<>();
        List<DexClassNode> interfaces = DexInterfacesHierarchyFiller.getInterfaces(classNode);
        if (interfaces != null) {
            for (DexClassNode itf : interfaces) {
                result.putIfAbsent(itf.type, itf);
                Map<DexType, DexClassNode> superInterfaces = getDirectInterfaceTable(itf);
                superInterfaces.forEach((t, c) -> result.putIfAbsent(t, c));
            }
        }
        return result;
    }

    private DexMethodNode findDeclaredVirtualMethod(DexClassNode classNode, DexTypeList param,
                                                   DexType returnType, DexString name) {
        Optional<DexMethodNode> optional = classNode.getMethods().stream().filter(m -> {
            if (!name.equals(m.name)) {
                return false;
            }
            if (!param.equals(m.parameters)) {
                return false;
            }
            if (!returnType.equals(m.returnType)) {
                return false;
            }
            return true;
        }).findFirst();
        if (optional.isPresent()) {
            return optional.get();
        } else {
            // find Miranda methods

            Map<DexType, DexClassNode> interfaces = getDirectInterfaceTable(classNode);
            for (DexClassNode itf : interfaces.values()) {
                for (DexMethodNode im : itf.getMethods()) {
                    if (im.name.equals(name) && im.parameters.equals(param)
                            && im.returnType.equals(returnType)) {
                        return im;
                    }
                }
            }
            return null;
        }
    }


    private DexMethodNode findDeclaredVirtualMethod(DexClassNode classNode,
                                                    DexTypeList param,
                                                    DexType returnType,
                                                    DexString name,
                                                    DexClassLoader loader) {
        Optional<DexMethodNode> optional = classNode.getMethods().stream()
                .filter(m -> {
                    if (!name.equals(m.name)) {
                        return false;
                    }
                    if (!param.equals(m.parameters)) {
                        return false;
                    }
                    if (!returnType.equals(m.returnType)) {
                        return false;
                    }
                    return true;
                })
                .findFirst();

        if (optional.isPresent()) {
            return optional.get();
        } else {
            // find Miranda methods

            Set<DexType> interfaces = getInterfaceTable(classNode.type, loader);
            if (interfaces != null) {
                for (DexType interfaceType : interfaces) {
                    DexClassNode interfaceClassNode = loader.findClass(interfaceType);
                    for (DexMethodNode im : interfaceClassNode.getMethods()) {
                        if (im.name.equals(name) && im.parameters.equals(param)
                                && im.returnType.equals(returnType)) {
                            return im;
                        }
                    }
                }
            }
            return null;
        }
    }


}
