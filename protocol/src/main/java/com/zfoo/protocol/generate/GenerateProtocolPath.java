/*
 * Copyright (C) 2020 The zfoo Authors
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.zfoo.protocol.generate;

import com.zfoo.protocol.collection.CollectionUtils;
import com.zfoo.protocol.collection.tree.GeneralTree;
import com.zfoo.protocol.collection.tree.TreeNode;
import com.zfoo.protocol.registration.IProtocolRegistration;
import com.zfoo.protocol.serializer.CodeLanguage;
import com.zfoo.protocol.serializer.enhance.EnhanceObjectProtocolSerializer;
import com.zfoo.protocol.util.AssertionUtils;
import com.zfoo.protocol.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * EN: When generating a protocol, the final generation path of the protocol will use this class.
 * CN: 生成协议的时候，协议的最终生成路径会使用这个类
 *
 * @author godotg
 * @version 3.0
 */
public abstract class GenerateProtocolPath {

    /**
     * EN: The paths and temporary variables generated by the protocol will be destroyed after startup is completed.
     * CN: 协议生成的路径，临时变量，启动完成就会销毁
     */
    private static Map<Short, String> protocolPathMap = new HashMap<>();


    public static void clear() {
        protocolPathMap.clear();
        protocolPathMap = null;
    }

    public static String protocolAbsolutePath(short protocolId, CodeLanguage language) {
        var path = getProtocolPath(protocolId);
        var name = EnhanceObjectProtocolSerializer.getProtocolClassSimpleName(protocolId);
        if (StringUtils.isBlank(path)) {
            path = name;
        } else {
            path = StringUtils.format("{}/{}", path, name);
        }

        switch (language) {
            case Cpp:
                break;
            case Go:
                break;
            case JavaScript:
                break;
            case TypeScript:
                break;
            case CSharp:
                break;
            case Protobuf:
                break;
            case Lua:
                break;
            case GdScript:
                break;
            case Python:
                if (StringUtils.isBlank(getProtocolPath(protocolId))) {
                    path = StringUtils.PERIOD;
                } else {
                    path = StringUtils.substringBeforeLast(path, StringUtils.SLASH);
                    path = path.replaceAll(StringUtils.SLASH, StringUtils.PERIOD);
                }
                break;
            case Enhance:
                break;
        }
        return path;
    }

    /**
     * 获取协议生成的路径
     */
    public static String getProtocolPath(short protocolId) {
        AssertionUtils.notNull(protocolPathMap
                , "[{}]The initialization has been completed. Get Protocol Path cannot be called after the initialization is completed."
                , GenerateProtocolPath.class.getSimpleName());

        var protocolPath = protocolPathMap.get(protocolId);
        if (StringUtils.isBlank(protocolPath)) {
            return StringUtils.EMPTY;
        }

        return protocolPath.replaceAll(StringUtils.PERIOD_REGEX, StringUtils.SLASH);
    }

    public static String getRelativePath(short protocolId, short relativeProtocolId) {
        // 不是折叠协议的话，protocolPathMap一定是空，这里返回“”，上层会解析为同一个文件下
        if (CollectionUtils.isEmpty(protocolPathMap)) {
            return StringUtils.EMPTY;
        }
        var protocolPath = protocolPathMap.get(protocolId);
        var relativePath = protocolPathMap.get(relativeProtocolId);
        if (relativePath.startsWith(protocolPath)) {
            return StringUtils.format(".{}", StringUtils.substringAfterFirst(relativePath, protocolPath).replaceAll(StringUtils.PERIOD_REGEX, StringUtils.SLASH));
        }

        var splits = protocolPath.split(StringUtils.PERIOD_REGEX);
        var builder = new StringBuilder();

        for (var i = splits.length; i > 0; i--) {
            builder.append("../");
            var path = StringUtils.joinWith(StringUtils.PERIOD, Arrays.stream(splits).limit(i).collect(Collectors.toList()).toArray());
            if (relativePath.startsWith(path)) {
                builder.append(StringUtils.substringAfterFirst(relativePath, path).replaceAll(StringUtils.PERIOD_REGEX, StringUtils.SLASH));
                return builder.toString();
            }
        }
        builder.append(relativePath.replaceAll(StringUtils.PERIOD_REGEX, StringUtils.SLASH));
        return builder.toString();
    }

    /**
     * 获取协议生成的首字母大写的路径
     */
    public static String getCapitalizeProtocolPath(short protocolId) {
        return StringUtils.joinWith(StringUtils.SLASH, Arrays.stream(getProtocolPath(protocolId).split(StringUtils.SLASH)).map(it -> StringUtils.capitalize(it)).toArray());
    }

    /**
     * 解析协议的路径
     *
     * @param protocolRegistrations 需要解析的路径
     */
    public static void initProtocolPath(List<IProtocolRegistration> protocolRegistrations) {
        AssertionUtils.notNull(protocolPathMap
                , "[{}]The initialization has been completed. Get Protocol Path cannot be called after the initialization is completed."
                , GenerateProtocolPath.class.getSimpleName());

        // 将需要生成的协议的路径添加到多叉树中
        var protocolPathTree = new GeneralTree<IProtocolRegistration>();
        protocolRegistrations.forEach(it -> protocolPathTree.addNode(it.protocolConstructor().getDeclaringClass().getCanonicalName(), it));

        var rootTreeNode = protocolPathTree.getRootNode();

        if (CollectionUtils.isEmpty(rootTreeNode.getChildren())) {
            return;
        }

        var queue = new LinkedList<>(rootTreeNode.getChildren());
        while (!queue.isEmpty()) {
            var childTreeNode = queue.poll();
            var childChildren = childTreeNode.getChildren();
            // 如果子节点为空，则以当前节点为路径
            if (CollectionUtils.isEmpty(childChildren)) {
                toProtocolPath(childTreeNode);
                continue;
            }

            // 如果子节点的协议数据有一个不为空的，则以当前节点为路径
            if (childChildren.stream().anyMatch(it -> it.getData() != null)) {
                toProtocolPath(childTreeNode);
                continue;
            }

            // 继续深度便利子节点的路径
            for (var subClassId : childTreeNode.getChildren()) {
                queue.offer(subClassId);
            }
        }
    }

    private static void toProtocolPath(TreeNode<IProtocolRegistration> protocolTreeNode) {
        var allChildren = protocolTreeNode.flatTreeNodes()
                .stream()
                .filter(it -> it.getData() != null)
                .collect(Collectors.toList());
        var pathBefore = StringUtils.substringBeforeLast(protocolTreeNode.fullName(), StringUtils.PERIOD);
        for (var child : allChildren) {
            var protocolSimpleName = child.getData().protocolConstructor().getDeclaringClass().getSimpleName();
            var splits = Arrays.stream(StringUtils.substringBeforeLast(StringUtils.substringAfterFirst(child.fullName(), pathBefore), protocolSimpleName)
                            .split(StringUtils.PERIOD_REGEX))
                    .filter(it -> StringUtils.isNotBlank(it))
                    .toArray();
            protocolPathMap.put(child.getData().protocolId(), StringUtils.joinWith(StringUtils.PERIOD, splits));
        }
    }

}
