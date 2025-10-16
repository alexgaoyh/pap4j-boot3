package cn.net.pap.common.file.xml;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class XmlParseUtil {

    private static final String DEFAULT_DELIMITER = ";";

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

    // 私有构造器防止实例化
    private XmlParseUtil() {
    }

    /**
     * 从文件路径创建 Document 对象
     */
    public static Document getDocumentByPath(String filePath) throws IOException {
        try {
            String xmlContent = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
            // 处理 BOM 头
            xmlContent = xmlContent.startsWith("\uFEFF") ? xmlContent.substring(1) : xmlContent;

            var builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            return builder.parse(new java.io.ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("Failed to parse XML document", e);
        }
    }

    /**
     * 核心解析方法
     *
     * @param document
     * @param root                  根节点XPath路径
     * @param nodeMap               单节点映射配置
     * @param nodesMap              多节点映射配置
     * @param nodeFromAttributeMap  从属性提取单值配置
     * @param nodesFromAttributeMap 从属性提取多值配置
     * @param attributesMap         带属性的节点配置
     * @param delimiter             多值合并分隔符
     * @param nodesDelimiterMap     特定字段分隔符配置
     * @return
     */
    public static List<Map<String, Object>> parse(Document document, String root, Map<String, String> nodeMap, Map<String, String> nodesMap, Map<String, Map<String, String>> nodeFromAttributeMap, Map<String, Map<String, String>> nodesFromAttributeMap, Map<String, Map<String, String>> attributesMap, String delimiter, Map<String, String> nodesDelimiterMap) {

        XPath xpath = XPATH_FACTORY.newXPath();
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            NodeList rootNodes = (NodeList) xpath.evaluate(root, document, XPathConstants.NODESET);

            for (int i = 0; i < rootNodes.getLength(); i++) {
                Node rootNode = rootNodes.item(i);
                Map<String, Object> dataMap = new HashMap<>();
                String currentRoot = "%s[%d]".formatted(root, i + 1);

                // 处理各种节点类型
                processNodes(document, xpath, currentRoot, nodeMap, attributesMap, dataMap, false);
                processNodes(document, xpath, currentRoot, nodesMap, attributesMap, dataMap, true);
                processNodesWithDelimiter(document, xpath, currentRoot, nodesDelimiterMap, Optional.ofNullable(delimiter).orElse(DEFAULT_DELIMITER), dataMap);
                processNodesFromAttributes(document, xpath, currentRoot, nodeFromAttributeMap, dataMap, false);
                processNodesFromAttributes(document, xpath, currentRoot, nodesFromAttributeMap, dataMap, true);

                if (!dataMap.isEmpty()) {
                    result.add(dataMap);
                }
            }
        } catch (Exception e) {
            throw new XmlParseException("XML parsing failed", e);
        }

        return result;
    }

    /**
     * 检查 XML 中是否存在指定路径
     */
    public static boolean hasRootPath(Document document, String rootPath) {
        try {
            XPath xpath = XPATH_FACTORY.newXPath();
            NodeList nodes = (NodeList) xpath.evaluate(rootPath, document, XPathConstants.NODESET);
            return nodes != null && nodes.getLength() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ============== 私有方法 ==============

    private static void processNodes(Document document, XPath xpath, String currentRoot, Map<String, String> nodePathMap, Map<String, Map<String, String>> attributesMap, Map<String, Object> dataMap, boolean isMultiple) throws Exception {

        if (nodePathMap == null || nodePathMap.isEmpty()) return;

        for (var entry : nodePathMap.entrySet()) {
            String fieldName = entry.getKey();
            String xpathExpr = entry.getValue();

            if (xpathExpr == null || xpathExpr.isEmpty()) continue;

            String fullPath = currentRoot + xpathExpr;

            if (isMultiple) {
                NodeList nodes = (NodeList) xpath.evaluate(fullPath, document, XPathConstants.NODESET);
                List<Object> values = new ArrayList<>();

                if (nodes != null && nodes.getLength() > 0) {
                    for (int i = 0; i < nodes.getLength(); i++) {
                        values.add(processNodeValue(nodes.item(i), fieldName, attributesMap));
                    }
                } else {
                    values.add(createEmptyAttributeMap(fieldName, attributesMap));
                }

                dataMap.put(fieldName, values);
            } else {
                Node node = (Node) xpath.evaluate(fullPath, document, XPathConstants.NODE);
                Object value = node != null ? processNodeValue(node, fieldName, attributesMap) : createEmptyAttributeMap(fieldName, attributesMap);
                dataMap.put(fieldName, value);
            }
        }
    }

    private static void processNodesWithDelimiter(Document document, XPath xpath, String currentRoot, Map<String, String> nodesDelimiterMap, String delimiter, Map<String, Object> dataMap) throws Exception {

        if (nodesDelimiterMap == null || nodesDelimiterMap.isEmpty()) return;

        for (var entry : nodesDelimiterMap.entrySet()) {
            String fieldName = entry.getKey();
            String xpathExpr = entry.getValue();

            if (xpathExpr == null || xpathExpr.isEmpty()) {
                dataMap.put(fieldName, "");
                continue;
            }

            NodeList nodes = (NodeList) xpath.evaluate(currentRoot + xpathExpr, document, XPathConstants.NODESET);
            if (nodes != null && nodes.getLength() > 0) {
                String joinedValue = joinNodeValues(nodes, delimiter);
                dataMap.put(fieldName, joinedValue);
            } else {
                dataMap.put(fieldName, "");
            }
        }
    }

    private static String joinNodeValues(NodeList nodes, String delimiter) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                values.add(node.getTextContent().trim());
            }
        }
        return String.join(delimiter, values);
    }

    private static void processNodesFromAttributes(Document document, XPath xpath, String currentRoot, Map<String, Map<String, String>> attributeMappings, Map<String, Object> dataMap, boolean isMultiple) throws Exception {

        if (attributeMappings == null || attributeMappings.isEmpty()) return;

        for (var entry : attributeMappings.entrySet()) {
            String xpathExpr = entry.getKey();
            Map<String, String> fieldAttrMap = entry.getValue();

            if (fieldAttrMap == null || fieldAttrMap.isEmpty()) continue;

            String fullPath = currentRoot + "/" + xpathExpr;

            if (isMultiple) {
                NodeList nodes = (NodeList) xpath.evaluate(fullPath, document, XPathConstants.NODESET);
                if (nodes != null && nodes.getLength() > 0) {
                    List<Map<String, Object>> values = new ArrayList<>();
                    for (int i = 0; i < nodes.getLength(); i++) {
                        values.add(extractAttributes(nodes.item(i), fieldAttrMap));
                    }
                    dataMap.put(xpathExpr, values);
                }
            } else {
                Node node = (Node) xpath.evaluate(fullPath, document, XPathConstants.NODE);
                if (node != null) {
                    dataMap.putAll(extractAttributes(node, fieldAttrMap));
                }
            }
        }
    }

    private static Object processNodeValue(Node node, String fieldName, Map<String, Map<String, String>> attributesMap) {

        if (node.getNodeType() != Node.ELEMENT_NODE) return "";

        Element element = (Element) node;
        var attrMapping = attributesMap != null ? attributesMap.get(fieldName) : null;

        if (attrMapping == null || attrMapping.isEmpty()) {
            return element.getTextContent().trim();
        }

        Map<String, Object> attributeMap = new HashMap<>();
        attrMapping.forEach((attrField, attrName) -> {
            String value = (attrName == null || fieldName.equals(attrName)) ? element.getTextContent().trim() : element.getAttribute(attrName);
            attributeMap.put(attrField, value != null ? value : "");
        });
        return attributeMap;
    }

    private static Map<String, Object> createEmptyAttributeMap(String fieldName, Map<String, Map<String, String>> attributesMap) {

        var attrMapping = attributesMap != null ? attributesMap.get(fieldName) : null;
        if (attrMapping == null || attrMapping.isEmpty()) {
            return Map.of();
        }

        return attrMapping.keySet().stream().collect(Collectors.toMap(key -> key, key -> ""));
    }

    private static Map<String, Object> extractAttributes(Node node, Map<String, String> fieldAttrMap) {

        Map<String, Object> result = new HashMap<>();
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            fieldAttrMap.forEach((field, attr) -> result.put(field, ""));
            return result;
        }

        Element element = (Element) node;
        fieldAttrMap.forEach((field, attr) -> {
            result.put(field, element.getAttribute(attr));
        });
        return result;
    }

    public static final class XmlParseException extends RuntimeException {
        public XmlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 为平铺的目录列表添加父节点ID
     *
     * @param flatList 平铺的目录项列表
     * @return 添加了parentId的列表
     */
    public static List<Map<String, Object>> addParentInfo(List<Map<String, Object>> flatList) {
        List<Map<String, Object>> result = new ArrayList<>();
        // 记录各级别最后一个节点的sequence (level -> sequence)
        Map<Integer, String> levelLastNodeMap = new HashMap<>();

        for (Map<String, Object> originalItem : flatList) {
            // 创建新对象避免修改原数据
            Map<String, Object> newItem = new HashMap<>();

            // 首先处理可能存在的"."键值
            if (originalItem.containsKey(".")) {
                // 如果有"."键，将其内容合并到顶层
                @SuppressWarnings("unchecked") Map<String, Object> dotContent = ((List<Map<String, Object>>) originalItem.get(".")).get(0);
                newItem.putAll(dotContent);
            } else {
                // 没有"."键，直接使用原数据
                newItem.putAll(originalItem);
            }

            String sequence = (String) newItem.get("sequence");
            int level = Integer.parseInt((String) newItem.get("level"));

            // 清除当前级别及更高级别的记录
            levelLastNodeMap.keySet().removeIf(l -> l >= level);

            // 设置父节点ID (上一级别的最后一个节点)
            if (level > 1) {
                String parentId = levelLastNodeMap.get(level - 1);
                newItem.put("parentId", parentId);
            } else {
                newItem.put("parentId", null); // 根节点没有父节点
            }

            // 更新当前级别的最后一个节点
            levelLastNodeMap.put(level, sequence);

            result.add(newItem);
        }

        return result;
    }

    /**
     * 打印带父子关系和缩进的结果
     */
    public static void printWithParent(List<Map<String, Object>> list) {
        System.out.println("===== 带父子关系的目录结构（缩进表示层级） =====");
        // 记录各级别的缩进字符串
        Map<Integer, String> levelIndentMap = new HashMap<>();
        levelIndentMap.put(1, "");

        for (Map<String, Object> item : list) {
            // 处理可能存在的"."键
            Map<String, Object> effectiveItem = item.containsKey(".") ? (Map<String, Object>) item.get(".") : item;

            String sequence = (String) effectiveItem.get("sequence");
            String levelStr = (String) effectiveItem.get("level");
            int level = Integer.parseInt(levelStr);
            String parentId = (String) effectiveItem.get("parentId");
            String title = (String) effectiveItem.get("title");

            // 动态生成缩进字符串
            String indent = levelIndentMap.computeIfAbsent(level, l -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < l; i++) {
                    sb.append("│   ");
                }
                return sb.toString();
            });

            // 格式化输出
            System.out.printf("%s├─ Seq: %-3s | Level: %s | Parent: %-3s | Title: %s%n", indent, sequence, levelStr, parentId != null ? parentId : "N/A", title);

            // 更新缩进映射
            if (!levelIndentMap.containsKey(level + 1)) {
                levelIndentMap.put(level + 1, indent + "│   ");
            }
        }
    }

    /**
     * 使用XPath修改XML文件中的节点属性或文本内容
     * @param filePath XML文件的绝对路径
     * @param updates 要修改的XPath表达式和值的映射
     *                key: XPath表达式 (如: "//server/@port", "//title/text()")
     *                value: 新的值
     * @throws Exception 如果操作失败
     */
    public static void updateXmlByXPath(String filePath, Map<String, String> updates) throws Exception {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        // 创建DocumentBuilderFactory并配置安全特性
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        // 解析XML文件
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new File(filePath));

        // 创建XPath对象
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();

        boolean hasChanges = false;

        // 遍历所有要修改的XPath表达式
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String xpathExpression = entry.getKey();
            String newValue = entry.getValue();

            try {
                // 执行XPath查询
                XPathExpression expr = xpath.compile(xpathExpression);
                NodeList nodeList = (NodeList) expr.evaluate(document, XPathConstants.NODESET);

                // 更新所有匹配的节点
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);

                    if (node instanceof Attr) {
                        // 属性节点
                        ((Attr) node).setValue(newValue);
                    } else if (node instanceof Text) {
                        // 文本节点
                        node.setNodeValue(newValue);
                    } else if (node instanceof Element) {
                        // 元素节点 - 设置文本内容
                        ((Element) node).setTextContent(newValue);
                    }
                    hasChanges = true;
                }
            } catch (XPathExpressionException e) {
                throw new RuntimeException("XPath表达式错误: " + xpathExpression, e);
            }
        }

        // 如果有修改，则保存文件
        if (hasChanges) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);
        }
    }

}