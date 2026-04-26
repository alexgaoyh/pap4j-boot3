package cn.net.pap.common.excel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Excel 数据读取
 */
public class ExcelHierarchyReaderTest {

    private static final Logger log = LoggerFactory.getLogger(ExcelHierarchyReaderTest.class);

    @Test
    public void readPrintTest() {
        try {
            String desktop = TestResourceUtil.getFile("tree.xlsx").getAbsolutePath();;
            String jsonResult = readExcelToJson(desktop);

            log.info("{}", jsonResult);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 定义Excel列名常量
    private static final int COL_ORDER = 0;      // A列：顺序码
    private static final int COL_LEVEL = 1;      // B列：层次码
    private static final int COL_LEVEL1 = 2;     // C列：一级
    private static final int COL_LEVEL2 = 3;     // D列：二级
    private static final int COL_LEVEL3 = 4;     // E列：三级
    private static final int COL_LEVEL4 = 5;     // F列：四级
    private static final int COL_LEVEL5 = 6;     // G列：五级
    private static final int COL_LEVEL6 = 7;     // H列：六级

    /**
     * 读取Excel文件并转换为JSON字符串
     *
     * @param filePath Excel文件路径
     * @return JSON字符串
     */
    public static String readExcelToJson(String filePath) throws IOException {
        if (new File(filePath).exists()) {
            List<ClassificationNode> nodeList = readExcelData(filePath);
            ClassificationNode root = buildHierarchyTree(nodeList);
            return convertToJson(root);
        } else {
            return null;
        }

    }

    /**
     * 读取Excel文件并返回列表
     */
    private static List<ClassificationNode> readExcelData(String filePath) throws IOException {
        List<ClassificationNode> nodes = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // 读取第一个sheet

            // 跳过标题行
            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) {
                rowIterator.next(); // 跳过表头行
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                ClassificationNode node = createNodeFromRow(row);
                if (node != null) {
                    nodes.add(node);
                }
            }
        }

        return nodes;
    }

    /**
     * 从Excel行创建节点
     */
    private static ClassificationNode createNodeFromRow(Row row) {
        ClassificationNode node = new ClassificationNode();

        // 获取顺序码
        Cell orderCell = row.getCell(COL_ORDER);
        if (orderCell != null) {
            node.setOrderCode(getCellValue(orderCell));
        }

        // 获取层次码
        Cell levelCodeCell = row.getCell(COL_LEVEL);
        if (levelCodeCell != null) {
            node.setLevelCode(getCellValue(levelCodeCell));
        }

        // 获取各层级名称
        node.setLevel1Name(getCellValue(row.getCell(COL_LEVEL1)));
        node.setLevel2Name(getCellValue(row.getCell(COL_LEVEL2)));
        node.setLevel3Name(getCellValue(row.getCell(COL_LEVEL3)));
        node.setLevel4Name(getCellValue(row.getCell(COL_LEVEL4)));
        node.setLevel5Name(getCellValue(row.getCell(COL_LEVEL5)));
        node.setLevel6Name(getCellValue(row.getCell(COL_LEVEL6)));

        // 判断当前节点的层级和名称
        determineNodeInfo(node);

        return node;
    }

    /**
     * 获取单元格值
     */
    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 处理整数和小数
                    double num = cell.getNumericCellValue();
                    if (num == (int) num) {
                        return String.valueOf((int) num);
                    } else {
                        return String.valueOf(num);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            default:
                return "";
        }
    }

    /**
     * 确定节点的层级和名称信息
     */
    private static void determineNodeInfo(ClassificationNode node) {
        // 根据层次码确定层级深度
        String levelCode = node.getLevelCode();
        int depth = 0;
        String[] parts = levelCode.split("\\.");
        depth = parts.length;
        node.setDepth(depth);

        // 确定节点的名称（取最低层级的名称）
        String name = "";
        if (!node.getLevel6Name().isEmpty()) {
            name = node.getLevel6Name();
            node.setNodeName(name);
            node.setCategoryLevel(6);
        } else if (!node.getLevel5Name().isEmpty()) {
            name = node.getLevel5Name();
            node.setNodeName(name);
            node.setCategoryLevel(5);
        } else if (!node.getLevel4Name().isEmpty()) {
            name = node.getLevel4Name();
            node.setNodeName(name);
            node.setCategoryLevel(4);
        } else if (!node.getLevel3Name().isEmpty()) {
            name = node.getLevel3Name();
            node.setNodeName(name);
            node.setCategoryLevel(3);
        } else if (!node.getLevel2Name().isEmpty()) {
            name = node.getLevel2Name();
            node.setNodeName(name);
            node.setCategoryLevel(2);
        } else if (!node.getLevel1Name().isEmpty()) {
            name = node.getLevel1Name();
            node.setNodeName(name);
            node.setCategoryLevel(1);
        }

        // 注意：这里不再设置完整路径，路径将在树构建完成后递归构建
    }

    /**
     * 构建层级树
     */
    private static ClassificationNode buildHierarchyTree(List<ClassificationNode> nodes) {
        // 创建根节点
        ClassificationNode root = new ClassificationNode();
        root.setNodeName("根节点");
        root.setLevelCode("0");
        root.setDepth(0);
        root.setFullPath(""); // 根节点路径为空

        // 根据层次码构建树结构
        Map<String, ClassificationNode> nodeMap = new HashMap<>();
        nodeMap.put("0", root);

        // 将所有节点放入映射表
        for (ClassificationNode node : nodes) {
            nodeMap.put(node.getLevelCode(), node);
        }

        // 建立父子关系
        for (ClassificationNode node : nodes) {
            String levelCode = node.getLevelCode();

            // 查找父节点：去掉最后一个点分隔的部分
            String parentCode = getParentCode(levelCode);

            ClassificationNode parent = nodeMap.get(parentCode);
            if (parent == null) {
                parent = root; // 如果没有找到父节点，就挂到根节点下
            }

            parent.addChild(node);
            node.setParent(parent);
        }

        // 构建完成树结构后，构建完整的路径
        buildFullPaths(root);

        return root;
    }

    /**
     * 递归构建所有节点的完整路径
     */
    private static void buildFullPaths(ClassificationNode node) {
        // 如果是根节点，路径已在创建时设置为空
        if (node.getParent() != null) {
            // 获取父节点的路径
            String parentPath = node.getParent().getFullPath();
            String currentNodeName = getCurrentLevelName(node);

            if (parentPath == null || parentPath.isEmpty()) {
                node.setFullPath(currentNodeName);
            } else {
                node.setFullPath(parentPath + "-" + currentNodeName);
            }
        }

        // 递归处理子节点
        for (ClassificationNode child : node.getChildren()) {
            buildFullPaths(child);
        }
    }

    /**
     * 获取当前节点的层级名称
     * 根据节点的分类层级返回对应的名称
     */
    private static String getCurrentLevelName(ClassificationNode node) {
        switch (node.getCategoryLevel()) {
            case 1:
                return node.getLevel1Name();
            case 2:
                return node.getLevel2Name();
            case 3:
                return node.getLevel3Name();
            case 4:
                return node.getLevel4Name();
            case 5:
                return node.getLevel5Name();
            case 6:
                return node.getLevel6Name();
            default:
                return node.getNodeName();
        }
    }

    /**
     * 获取父节点的层次码
     */
    private static String getParentCode(String levelCode) {
        if (levelCode == null || levelCode.isEmpty()) {
            return "0";
        }

        int lastDotIndex = levelCode.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return levelCode.substring(0, lastDotIndex);
        } else {
            // 如果是一级节点，父节点是根节点
            String[] parts = levelCode.split("\\.");
            if (parts.length == 1) {
                return "0";
            }
        }

        return "0";
    }

    /**
     * 转换为JSON字符串
     */
    private static String convertToJson(ClassificationNode root) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectNode rootJson = mapper.createObjectNode();
        rootJson.put("nodeName", root.getNodeName());
        rootJson.put("levelCode", root.getLevelCode());
        rootJson.put("depth", root.getDepth());
        rootJson.put("fullPath", root.getFullPath());

        ArrayNode childrenArray = mapper.createArrayNode();
        for (ClassificationNode child : root.getChildren()) {
            childrenArray.add(convertNodeToJson(mapper, child));
        }
        rootJson.set("children", childrenArray);

        return mapper.writeValueAsString(rootJson);
    }

    /**
     * 将单个节点转换为JSON
     */
    private static ObjectNode convertNodeToJson(ObjectMapper mapper, ClassificationNode node) {
        ObjectNode nodeJson = mapper.createObjectNode();

        // 基本属性
        nodeJson.put("orderCode", node.getOrderCode());
        nodeJson.put("levelCode", node.getLevelCode());
        nodeJson.put("nodeName", node.getNodeName());
        nodeJson.put("fullPath", node.getFullPath());
        nodeJson.put("depth", node.getDepth());
        nodeJson.put("categoryLevel", node.getCategoryLevel());

        // 各层级名称
        nodeJson.put("level1Name", node.getLevel1Name());
        nodeJson.put("level2Name", node.getLevel2Name());
        nodeJson.put("level3Name", node.getLevel3Name());
        nodeJson.put("level4Name", node.getLevel4Name());
        nodeJson.put("level5Name", node.getLevel5Name());
        nodeJson.put("level6Name", node.getLevel6Name());

        // 如果有子节点，递归处理
        if (!node.getChildren().isEmpty()) {
            ArrayNode childrenArray = mapper.createArrayNode();
            for (ClassificationNode child : node.getChildren()) {
                childrenArray.add(convertNodeToJson(mapper, child));
            }
            nodeJson.set("children", childrenArray);
        }

        return nodeJson;
    }

    /**
     * 分类节点类
     */
    static class ClassificationNode {
        private String orderCode;      // 顺序码
        private String levelCode;      // 层次码
        private String nodeName;       // 节点名称（最低层级的名称）
        private String fullPath;       // 完整路径
        private int depth;             // 深度（根据层次码的点分隔数）
        private int categoryLevel;     // 分类层级（1-6）

        // 各层级名称
        private String level1Name = "";
        private String level2Name = "";
        private String level3Name = "";
        private String level4Name = "";
        private String level5Name = "";
        private String level6Name = "";

        private ClassificationNode parent;
        private List<ClassificationNode> children = new ArrayList<>();

        // Getters and Setters
        public String getOrderCode() {
            return orderCode;
        }

        public void setOrderCode(String orderCode) {
            this.orderCode = orderCode;
        }

        public String getLevelCode() {
            return levelCode;
        }

        public void setLevelCode(String levelCode) {
            this.levelCode = levelCode;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getFullPath() {
            return fullPath;
        }

        public void setFullPath(String fullPath) {
            this.fullPath = fullPath;
        }

        public int getDepth() {
            return depth;
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }

        public int getCategoryLevel() {
            return categoryLevel;
        }

        public void setCategoryLevel(int categoryLevel) {
            this.categoryLevel = categoryLevel;
        }

        public String getLevel1Name() {
            return level1Name;
        }

        public void setLevel1Name(String level1Name) {
            this.level1Name = level1Name;
        }

        public String getLevel2Name() {
            return level2Name;
        }

        public void setLevel2Name(String level2Name) {
            this.level2Name = level2Name;
        }

        public String getLevel3Name() {
            return level3Name;
        }

        public void setLevel3Name(String level3Name) {
            this.level3Name = level3Name;
        }

        public String getLevel4Name() {
            return level4Name;
        }

        public void setLevel4Name(String level4Name) {
            this.level4Name = level4Name;
        }

        public String getLevel5Name() {
            return level5Name;
        }

        public void setLevel5Name(String level5Name) {
            this.level5Name = level5Name;
        }

        public String getLevel6Name() {
            return level6Name;
        }

        public void setLevel6Name(String level6Name) {
            this.level6Name = level6Name;
        }

        public ClassificationNode getParent() {
            return parent;
        }

        public void setParent(ClassificationNode parent) {
            this.parent = parent;
        }

        public List<ClassificationNode> getChildren() {
            return children;
        }

        public void setChildren(List<ClassificationNode> children) {
            this.children = children;
        }

        public void addChild(ClassificationNode child) {
            this.children.add(child);
        }

        @Override
        public String toString() {
            return "ClassificationNode{" +
                    "orderCode='" + orderCode + '\'' +
                    ", levelCode='" + levelCode + '\'' +
                    ", nodeName='" + nodeName + '\'' +
                    ", fullPath='" + fullPath + '\'' +
                    ", depth=" + depth +
                    '}';
        }
    }
}