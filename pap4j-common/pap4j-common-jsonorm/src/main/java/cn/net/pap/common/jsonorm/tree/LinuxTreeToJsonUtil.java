package cn.net.pap.common.jsonorm.tree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * 处理  linux 下 tree 命令得到的数据，转换为 json
 * <p>
 * .
 * +--- 256
 * |   +--- 001
 * |   |   +--- 0001
 * |   |   |   +--- 0001.jpg
 * |   |   |   +--- 0002.jpg
 * |   |   |   +--- 0003.jpg
 * |   |   |   +--- 0004.jpg
 * |   |   +--- 0002
 * |   |   |   +--- 0001.jpg
 * |   |   |   +--- 0002.jpg
 * |   |   |   +--- 0003.jpg
 * |   |   +--- 0003
 * |   |   |   +--- 0001.jpg
 * |   |   +--- 0004
 * |   |   |   +--- 0001.jpg
 * |   |   |   +--- 0002.jpg
 * |   |   |   +--- 0003.jpg
 * |   |   |   +--- 0004.jpg
 * |   |   |   +--- 0005.jpg
 * |   |   |   +--- 0006.jpg
 */
public class LinuxTreeToJsonUtil {

    public static class TreeNode {
        private String name;
        private String type;
        private List<TreeNode> children;

        public TreeNode(String name, String type) {
            this.name = name;
            this.type = type;
            this.children = new ArrayList<>();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<TreeNode> getChildren() {
            return children;
        }

        public void setChildren(List<TreeNode> children) {
            this.children = children;
        }
    }

    public static TreeNode parseTreeFile(String filename) throws IOException {
        List<String> lines = new ArrayList<>();

        // 读取文件内容
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }

        if (lines.isEmpty()) {
            return null;
        }

        // 创建根节点
        String rootName = lines.get(0).trim();
        if (rootName.isEmpty()) {
            rootName = ".";
        }
        TreeNode root = new TreeNode(rootName, "dir");

        // 递归构建树结构
        buildTree(root, lines, 1, 0);

        return root;
    }

    /**
     * 递归构建树结构
     * @param parent 父节点
     * @param lines 所有行数据
     * @param startIndex 开始处理的行的索引
     * @param parentLevel 父节点的层级
     * @return 下一个要处理的行的索引
     */
    private static int buildTree(TreeNode parent, List<String> lines, int startIndex, int parentLevel) {
        int i = startIndex;

        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                i++;
                continue;
            }

            // 计算当前行的层级
            int currentLevel = calculateLevelByPrefix(line);

            // 如果当前层级小于等于父节点层级，说明已经回到上层，结束当前递归
            if (currentLevel <= parentLevel) {
                return i;
            }

            // 如果当前层级正好是父节点层级+1，说明是直接子节点
            if (currentLevel == parentLevel + 1) {
                String name = extractNameFromLine(line);
                String type = determineType(name);

                TreeNode node = new TreeNode(name, type);
                parent.getChildren().add(node);

                // 如果是目录，递归处理其子节点
                if ("dir".equals(type)) {
                    i = buildTree(node, lines, i + 1, currentLevel);
                } else {
                    i++;
                }
            } else {
                // 如果层级跳跃了（不应该发生），跳过该行
                i++;
            }
        }

        return i;
    }

    /**
     * 通过前缀模式计算层级
     * 规则：每遇到一个 "|   " 或 "+---" 模式，层级增加
     */
    private static int calculateLevelByPrefix(String line) {
        int level = 0;
        int pos = 0;

        while (pos < line.length()) {
            // 检查是否匹配层级模式
            if (pos + 4 <= line.length()) {
                String segment = line.substring(pos, pos + 4);
                if (segment.equals("|   ") || segment.equals("+---")) {
                    level++;
                    pos += 4;
                    continue;
                }
            }

            // 检查是否匹配缩进模式
            if (pos + 3 <= line.length()) {
                String segment = line.substring(pos, pos + 3);
                if (segment.equals("   ")) {
                    pos += 3;
                    continue;
                }
            }

            break;
        }

        return level;
    }

    /**
     * 从行中提取名称
     */
    private static String extractNameFromLine(String line) {
        // 找到最后一个 "+---" 模式后的内容
        int lastPatternIndex = -1;

        for (int i = 0; i <= line.length() - 4; i++) {
            String segment = line.substring(i, i + 4);
            if (segment.equals("+---")) {
                lastPatternIndex = i + 4;
            }
        }

        if (lastPatternIndex != -1) {
            return line.substring(lastPatternIndex).trim();
        }

        // 如果没有找到 "+---"，直接返回去除前导空格的内容
        return line.trim();
    }

    /**
     * 确定节点类型
     */
    private static String determineType(String name) {
        // 简单的判断：如果有文件扩展名且不以斜杠结尾，则认为是文件
        if (name.contains(".") && !name.endsWith("/") && !name.endsWith(":")) {
            return "file";
        }
        return "dir";
    }

    /**
     * 转换为JSON
     */
    public static ObjectNode convertToJson(TreeNode node) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();

        jsonNode.put("name", node.getName());
        jsonNode.put("type", node.getType());

        if (!node.getChildren().isEmpty()) {
            ArrayNode childrenArray = mapper.createArrayNode();
            for (TreeNode child : node.getChildren()) {
                childrenArray.add(convertToJson(child));
            }
            jsonNode.set("children", childrenArray);
        }

        return jsonNode;
    }

}
