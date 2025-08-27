package cn.net.pap.common.jsonorm.parser;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XML 递归解析工具类（支持CDATA）
 * 功能：
 * 1. 将任意 XML 解析为 List<Map<String, Object>>
 * 2. 自动处理嵌套元素、属性和CDATA节点
 * 3. 支持文本内容、CDATA、属性和嵌套结构
 */
public class XmlRecursiveParser {

    /**
     * 将 XML 字符串解析为 List<Map<String, Object>>
     *
     * @param xmlString XML 字符串
     * @return List<Map < String, Object>>，每个Map代表一个元素
     * @throws IllegalArgumentException 如果XML格式无效
     */
    public static List<Map<String, Object>> parseToUniversalList(String xmlString) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 启用CDATA节点处理
            factory.setCoalescing(true);
            factory.setIgnoringComments(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlString.getBytes()));

            List<Map<String, Object>> result = new ArrayList<>();
            Node root = document.getDocumentElement();

            if (root.getNodeType() == Node.ELEMENT_NODE) {
                result.add(parseElement((Element) root));
            }

            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid XML string", e);
        }
    }

    /**
     * 递归解析 XML 元素
     */
    private static Map<String, Object> parseElement(Element element) {
        Map<String, Object> map = new LinkedHashMap<>();

        // 处理元素名称
        map.put("_name", element.getNodeName());

        // 处理属性
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null && attributes.getLength() > 0) {
            Map<String, String> attrMap = new LinkedHashMap<>();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                attrMap.put(attr.getNodeName(), attr.getNodeValue());
            }
            map.put("_attributes", attrMap);
        }

        // 处理子节点
        NodeList children = element.getChildNodes();
        List<Object> childList = new ArrayList<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                childList.add(parseElement((Element) child));
            } else if (child.getNodeType() == Node.TEXT_NODE && !child.getNodeValue().trim().isEmpty()) {
                Map<String, Object> textNode = new LinkedHashMap<>();
                textNode.put("_text", child.getNodeValue().trim());
                childList.add(textNode);
            } else if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                Map<String, Object> cdataNode = new LinkedHashMap<>();
                cdataNode.put("_cdata", child.getNodeValue().trim());
                childList.add(cdataNode);
            }
        }

        if (!childList.isEmpty()) {
            map.put("_children", childList);
        }

        return map;
    }


    /**
     * 处理文本内容（普通文本或CDATA）
     */
    private static void handleTextContent(Map<String, List<Object>> childrenMap, String content, boolean isCData) {
        String key = isCData ? "_cdata" : "_text";

        if (!childrenMap.containsKey(key)) {
            childrenMap.put(key, new ArrayList<>());
        }

        // 如果是CDATA，用特殊结构包装
        if (isCData) {
            Map<String, Object> cdataMap = new LinkedHashMap<>();
            cdataMap.put("_type", "cdata");
            cdataMap.put("_value", content);
            childrenMap.get(key).add(cdataMap);
        } else {
            childrenMap.get(key).add(content);
        }
    }

    /**
     * 根据路径规则从结果中提取值
     *
     * @param result   解析后的数据结构
     * @param pathRule 路径规则，如 "$[0].catalog[0].title"
     *                 路径规则，如 "$[0]._children[0]._attributes.id"
     *                 路径规则，如 "$[0]._children[0]._children[?@_name=ids]"
     *                 路径规则，如 "$[0]._children[0]._children[?@_name=names]._children"
     * @return 提取的值
     */
    public static Object extract(List<Map<String, Object>> result, String pathRule) {
        String[] parts = pathRule.split("\\.");
        Object current = result;

        for (String part : parts) {
            if (current == null) {
                return null; // 中途遇到null直接返回
            }

            if (part.startsWith("$[")) {
                // 处理数组索引，如 $[0]
                int index = Integer.parseInt(part.substring(2, part.length() - 1));
                current = safeGetFromList((List<?>) current, index);

            } else if (part.contains("[?@")) {
                // 增强功能：处理过滤表达式，如 catalog[?@id=100]  意味着从数组里面取出来属性 id=100 的数据值
                String key = part.substring(0, part.indexOf("["));
                String filterExpr = part.substring(part.indexOf("[?@") + 3, part.length() - 1); // id=100
                String[] kv = filterExpr.split("=", 2);
                String attrKey = kv[0].trim();
                String attrVal = kv[1].trim();

                Object listObj = safeGetFromMap((Map<?, ?>) current, key);
                if (listObj instanceof List) {
                    current = filterListByAttribute((List<?>) listObj, attrKey, attrVal);
                } else {
                    current = null;
                }

            } else if (part.contains("[")) {
                // 处理带数组的key，如 catalog[0]
                String key = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));
                current = safeGetFromMap((Map<?, ?>) current, key);
                current = safeGetFromList((List<?>) current, index);
            } else if (part.startsWith("@")) {
                // 处理属性访问，如 @id 或 @attributes.name
                String attrPath = part.substring(1);
                current = getAttributeValue(current, attrPath);
            } else {
                // 普通map key
                current = safeGetFromMap((Map<?, ?>) current, part);
            }
        }

        return current;
    }

    private static Object filterListByAttribute(List<?> list, String attrKey, String attrVal) {
        if (list == null) return null;
        for (Object item : list) {
            if (item instanceof Map) {
                Object val = ((Map<?, ?>) item).get(attrKey);
                if (val != null && attrVal.equals(val.toString())) {
                    return item; // 找到第一个匹配的就返回
                }
            }
        }
        return null;
    }

    // 安全从List获取
    private static Object safeGetFromList(List<?> list, int index) {
        try {
            return list.get(index);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    // 安全从Map获取
    private static Object safeGetFromMap(Map<?, ?> map, String key) {
        return map != null ? map.get(key) : null;
    }

    // 获取属性值（支持多级属性）
    private static Object getAttributeValue(Object current, String attrPath) {
        if (!(current instanceof Map)) {
            return null;
        }

        Map<?, ?> map = (Map<?, ?>) current;
        Object attributes = map.get("_attributes");

        // 如果没有_attributes字段，尝试直接获取
        if (attributes == null) {
            return map.get(attrPath);
        }

        // 多级属性处理（如 @attributes.name）
        String[] attrParts = attrPath.split("\\.");
        Object value = attributes;

        for (String part : attrParts) {
            if (!(value instanceof Map)) {
                return null;
            }
            value = ((Map<?, ?>) value).get(part);
        }

        return value;
    }

}