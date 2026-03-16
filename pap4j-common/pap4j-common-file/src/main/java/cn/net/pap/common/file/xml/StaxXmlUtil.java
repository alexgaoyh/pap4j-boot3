package cn.net.pap.common.file.xml;

import com.ibm.icu.text.BreakIterator;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class StaxXmlUtil {

    // 将 Factory 设为静态单例，避免每次调用方法时重复创建的巨大开销. XMLInputFactory 是线程安全的（大部分实现中），或者至少创建 Reader 的方法是线程安全的
    private static final XMLInputFactory factory;

    static {
        factory = XMLInputFactory.newInstance();
        // 优化配置：合并连续文本、关闭命名空间（对应原逻辑）
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        // 禁用命名空间处理，只返回本地名称
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
    }

    /**
     * 获取指定节点下的所有子节点 XML 文本
     *
     * @param xmlText  XML 文本
     * @param nodeName 节点名，例如 "personal"
     * @return List，每个元素是一个子节点的 XML 文本
     */
    public static List<String> readChildrenXmlByStax(String xmlText, String nodeName) {
        List<String> result = new ArrayList<>();
        try (StringReader sr = new StringReader(xmlText)) {

            XMLStreamReader reader = factory.createXMLStreamReader(sr);

            StringBuilder sb = null;
            int depth = 0; // 用于记录嵌套层级

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT && nodeName.equals(reader.getLocalName())) {
                    // 找到目标节点
                    sb = new StringBuilder(1024 * 4); // 初始 4KB，减少内存浪费
                    depth = 1;
                    sb.append("<").append(nodeName);
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        sb.append(" ").append(reader.getAttributeLocalName(i)).append("=\"").append(escapeXml(reader.getAttributeValue(i))).append("\"");
                    }
                    sb.append(">");
                } else if (sb != null) {
                    switch (event) {
                        case XMLStreamConstants.START_ELEMENT:
                            depth++;
                            sb.append("<").append(reader.getLocalName());
                            for (int i = 0; i < reader.getAttributeCount(); i++) {
                                sb.append(" ").append(reader.getAttributeLocalName(i)).append("=\"").append(escapeXml(reader.getAttributeValue(i))).append("\"");
                            }
                            sb.append(">");
                            break;
                        case XMLStreamConstants.CHARACTERS:
                        case XMLStreamConstants.CDATA: // 支持 CDATA
                            sb.append(escapeXml(reader.getText()));
                            break;
                        case XMLStreamConstants.END_ELEMENT:
                            sb.append("</").append(reader.getLocalName()).append(">");
                            depth--;
                            if (depth == 0) {
                                // 一个完整节点读取完成
                                result.add(sb.toString());
                                sb = null;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }

            reader.close();
        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        }
        return result;
    }

    public static String escapeXml(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String unescapeXml(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 这里用 StringBuilder 处理
        StringBuilder sb = new StringBuilder(text.length());
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '&') {
                // 尝试匹配 XML 实体
                if (text.startsWith("&amp;", i)) {
                    sb.append('&');
                    i += 4; // 跳过 amp;
                } else if (text.startsWith("&lt;", i)) {
                    sb.append('<');
                    i += 3; // 跳过 lt;
                } else if (text.startsWith("&gt;", i)) {
                    sb.append('>');
                    i += 3; // 跳过 gt;
                } else if (text.startsWith("&quot;", i)) {
                    sb.append('"');
                    i += 5; // 跳过 quot;
                } else if (text.startsWith("&apos;", i)) {
                    sb.append('\'');
                    i += 5; // 跳过 apos;
                } else {
                    // 不认识的 &... 直接原样保留
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }




    /**
     * 获取某节点文本内容
     */
    public static String readNodeValueByStax(String xmlText, String nodeName) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlText));

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && nodeName.equals(reader.getLocalName())) {
                    // 读取文本
                    String text = "";
                    while (reader.hasNext()) {
                        event = reader.next();
                        if (event == XMLStreamConstants.CHARACTERS) {
                            text += escapeXml(reader.getText());
                        } else if (event == XMLStreamConstants.END_ELEMENT && nodeName.equals(reader.getLocalName())) {
                            reader.close();
                            return text.trim();
                        }
                    }
                }
            }

            reader.close();
            return null;
        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * @param xmlText
     * @param parentNodeName
     * @return
     */
    public static String readChildrenXmlValueByStax(String xmlText, String parentNodeName) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlText));

            StringBuilder sb = null;
            int depth = 0;
            boolean insideParent = false;

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT && parentNodeName.equals(reader.getLocalName())) {
                    // 进入父节点
                    insideParent = true;
                    depth = 0; // 子节点深度
                    sb = new StringBuilder();
                } else if (insideParent) {
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        depth++;
                        sb.append("<").append(reader.getLocalName());
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            sb.append(" ").append(reader.getAttributeLocalName(i)).append("=\"").append(escapeXml(reader.getAttributeValue(i))).append("\"");
                        }
                        sb.append(">");
                    } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                        sb.append(escapeXml(reader.getText()));
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if (depth > 0) {
                            sb.append("</").append(reader.getLocalName()).append(">");
                            depth--;
                        } else if (parentNodeName.equals(reader.getLocalName())) {
                            // 父节点结束
                            reader.close();
                            return sb.toString();
                        }
                    }
                }
            }

            reader.close();
            return null;
        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        }
    }


    /**
     * 统计节点数量
     */
    public static int countNodesByStax(String xmlText, String nodeName) {
        int count = 0;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlText));

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && nodeName.equals(reader.getLocalName())) {
                    count++;
                }
            }

            reader.close();
            return count;
        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 StAX 流式方式解析 XML，并将其转换为统一的展示结构：
     * <p>
     * 转换规则说明：
     * 1. 根标签（rootTag）只作为最外层容器，不参与内容转换
     * 2. keepOriginalTags 中的标签（如 anchor）保持原样输出（自闭合）
     * 3. 其它所有标签统一转换为：
     * <p type="原标签名" ...原有属性...>
     * 4. 文本节点会被拆分成“逐字符包装”（由 wrapEachCharacter 实现）
     *
     * @param xmlString        原始 XML 字符串
     * @param rootTag          根标签名（例如 content、body）
     * @param keepOriginalTags 需要保持原样输出的标签集合（如 anchor、ref）
     * @return 转换后的 XML 字符串
     */
    public static String parseXMLInRootAndOriginalTags(String xmlString, String rootTag, Set<String> keepOriginalTags) {
        StringBuilder result = new StringBuilder("<").append(rootTag).append(">");
        int charSeq = 0;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // StAX 文本合并 连续的 CHARACTERS / CDATA 会自动合并 表情符号会以 完整字符串 传入
            factory.setProperty(XMLInputFactory.IS_COALESCING, true);
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlString));

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tagName = reader.getLocalName();
                    // 根标签：忽略
                    if (rootTag.equals(tagName)) {
                        continue;
                    }
                    // 原样保留的标签（如 anchor）
                    if (keepOriginalTags.contains(tagName)) {
                        result.append("<").append(tagName);
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            result.append(" ").append(reader.getAttributeLocalName(i)).append("=\"").append(reader.getAttributeValue(i)).append("\"");
                        }
                        result.append("/>");
                    } else {
                        // 其它标签统一转为 p
                        result.append("<p type=\"").append(tagName).append("\"");
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            result.append(" ").append(reader.getAttributeLocalName(i)).append("=\"").append(reader.getAttributeValue(i)).append("\"");
                        }
                        result.append(">");
                    }

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String tagName = reader.getLocalName();
                    if (!rootTag.equals(tagName) && !keepOriginalTags.contains(tagName)) {
                        result.append("</p>");
                    }
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    String text = reader.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        charSeq = wrapEachCharacter(text, result, charSeq);
                    }
                }
            }
            reader.close();
            result.append("</").append(rootTag).append(">");
        } catch (Exception e) {
            e.printStackTrace();
            return xmlString;
        }
        return result.toString();
    }

    /**
     * 从XML字符串中提取指定元素的所有属性
     *
     * @param xmlString XML字符串
     * @param elementName 元素名称
     * @return 包含所有属性名和属性值的Map，如果未找到元素或没有属性则返回空Map
     * @throws Exception XML解析异常
     */
    public static Map<String, String> extractAllAttributes(String xmlString, String elementName) {
        Map<String, String> attributes = new HashMap<>();
        if (xmlString == null || elementName == null || xmlString.trim().isEmpty()) {
            return attributes;
        }

        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new StringReader(xmlString));
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT &&
                    elementName.equals(reader.getLocalName())) {

                    int attributeCount = reader.getAttributeCount();
                    for (int i = 0; i < attributeCount; i++) {
                        String attrName = reader.getAttributeLocalName(i);
                        String attrValue = reader.getAttributeValue(i);
                        attributes.put(attrName, attrValue);
                    }
                    break;
                }
            }
            return attributes;
        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * 将文本中的每个字符用p标签包裹，并添加序号
     */
    private static int wrapEachCharacter(String text, StringBuilder result, int startSeq) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.CHINA);
        iterator.setText(text);

        int start = iterator.first();
        int end = iterator.next();
        int seq = startSeq;

        while (end != BreakIterator.DONE) {
            String ch = text.substring(start, end);
            result.append("<p type=\"char\" seq=\"").append(seq).append("\">").append(escapeXml(ch)).append("</p>");
            seq++;
            start = end;
            end = iterator.next();
        }

        return seq;
    }

    /**
     * 获取所有指定节点的文本内容，并将它们拼接成一个完整的字符串。
     * 支持提取多个同名节点的内容，且兼容节点内部包含子节点的复杂情况。
     *
     * @param xmlText   XML 文本
     * @param nodeName  目标节点名，例如 "contentRec"
     * @return 拼接后的完整字符串
     */
    public static String concatAllNodeValuesByStax(String xmlText, String nodeName) {
        if (xmlText == null || xmlText.trim().isEmpty() || nodeName == null) {
            return "";
        }

        // 初始容量设为较大值，避免拼接长文本时频繁扩容
        StringBuilder sb = new StringBuilder(1024);

        try {
            // 复用类中已有的静态线程安全 factory
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlText));

            boolean inTargetNode = false;
            int targetDepth = 0; // 用于处理可能存在的同名节点嵌套情况

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if (nodeName.equals(reader.getLocalName())) {
                            inTargetNode = true;
                            targetDepth++;
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                        // 只要处于目标节点内部，就追加文本内容
                        if (inTargetNode) {
                            String text = reader.getText();
                            if (text != null && !text.trim().isEmpty()) {
                                // 如果需要跟已有的 readNodeValueByStax 保持绝对一致，可以包一层 escapeXml()
                                // 但通常提取纯数据值进行拼接时，原样保留 getText() 是最准确的
                                sb.append(text.trim());
                            }
                        }
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        // 当闭合标签是我们寻找的目标标签时，递减深度
                        if (inTargetNode && nodeName.equals(reader.getLocalName())) {
                            targetDepth--;
                            if (targetDepth == 0) {
                                inTargetNode = false; // 完全退出目标节点
                            }
                        }
                        break;

                    default:
                        break;
                }
            }
            reader.close();

        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        }

        return sb.toString();
    }

    /**
     * 基于 parseXMLInRootAndOriginalTags 改造：
     * 移除 charSeq，改为按字符顺序从传入的 List<Map<String, String>> 中获取属性，并注入到字符包装标签中。
     *
     * @param xmlString        原始 XML 字符串
     * @param rootTag          根标签名（例如 content、body）
     * @param keepOriginalTags 需要保持原样输出的标签集合（如 anchor、ref）
     * @param attrList         按字符顺序对应的属性 Map 列表
     * @return 转换后的 XML 字符串
     */
    public static String parseXMLWithCustomAttributes(String xmlString, String rootTag, Set<String> keepOriginalTags, List<Map<String, String>> attrList) {
        StringBuilder result = new StringBuilder("<").append(rootTag).append(">");
        int charIndex = 0; // 用于追踪当前字符在 attrList 中的全局下标

        try {
            // 复用类中已有的 factory
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlString));

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tagName = reader.getLocalName();
                    // 根标签：忽略
                    if (rootTag.equals(tagName)) {
                        continue;
                    }
                    // 原样保留的标签（如 anchor）
                    if (keepOriginalTags.contains(tagName)) {
                        result.append("<").append(tagName);
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            result.append(" ").append(reader.getAttributeLocalName(i))
                                    .append("=\"").append(escapeXml(reader.getAttributeValue(i))).append("\"");
                        }
                        result.append("/>");
                    } else {
                        // 其它标签统一转为 span
                        result.append("<span type=\"").append(tagName).append("\"");
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            result.append(" ").append(reader.getAttributeLocalName(i))
                                    .append("=\"").append(escapeXml(reader.getAttributeValue(i))).append("\"");
                        }
                        result.append(">");
                    }

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String tagName = reader.getLocalName();
                    if (!rootTag.equals(tagName) && !keepOriginalTags.contains(tagName)) {
                        result.append("</span>");
                    }
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    String text = reader.getText();
                    // 只有非空文本才进行单字符拆分包装
                    if (text != null && !text.trim().isEmpty()) {
                        charIndex = wrapEachCharacterWithAttributes(text, result, charIndex, attrList);
                    }
                }
            }
            reader.close();
            result.append("</").append(rootTag).append(">");
        } catch (Exception e) {
            e.printStackTrace();
            return xmlString; // 解析失败降级返回原文本
        }
        return result.toString();
    }

    /**
     * 将文本中的每个字符用 span 标签包裹，并从传入的 List 中获取对应的属性 Map 注入。
     *
     * @param text       当前需要拆分的文本段
     * @param result     StringBuilder 结果收集器
     * @param startIndex 当前文本段第一个字符在全局的下标
     * @param attrList   属性集合
     * @return 更新后的全局下标
     */
    private static int wrapEachCharacterWithAttributes(String text, StringBuilder result, int startIndex, List<Map<String, String>> attrList) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.CHINA);
        iterator.setText(text);

        int start = iterator.first();
        int end = iterator.next();
        int currentIndex = startIndex;

        while (end != BreakIterator.DONE) {
            String ch = text.substring(start, end);

            result.append("<span");

            // 防御性判断：确保 currentIndex 没有超出 attrList 的范围
            if (attrList != null && currentIndex < attrList.size()) {
                Map<String, String> attrs = attrList.get(currentIndex);
                if (attrs != null && !attrs.isEmpty()) {
                    // 遍历 Map，将 key-value 作为属性拼接到标签内
                    for (Map.Entry<String, String> entry : attrs.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (key != null && value != null) {
                            // 调用原有的 escapeXml 确保属性值里的特殊字符(如 & < >)被正确转义
                            result.append(" ").append(key).append("=\"").append(escapeXml(value)).append("\"");
                        }
                    }
                }
            }

            result.append(">").append(escapeXml(ch)).append("</span>");

            currentIndex++;
            start = end;
            end = iterator.next();
        }

        return currentIndex;
    }

    /**
     * 静态方法：解析XML并返回所有可能的XPath路径
     * @param xmlContent XML字符串内容
     * @return 所有路径的列表
     */
    public static List<String> extractAllPaths(String xmlContent) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        IndexedPathHandler handler = new IndexedPathHandler();
        parser.parse(new InputSource(new StringReader(xmlContent)), handler);
        return handler.getAllPaths();
    }

    /**
     * SAX处理器 - 专门处理复杂XML结构
     */
    private static class IndexedPathHandler extends DefaultHandler {
        private final List<String> allPaths = new ArrayList<>();
        private final Stack<ElementContext> contextStack = new Stack<>();
        private final Map<String, Integer> siblingCount = new HashMap<>();
        private final StringBuilder textBuffer = new StringBuilder();
        private boolean hasTextContent = false;

        @Override
        public void startElement(String uri, String localName,
                                 String qName, Attributes attributes) {
            // 处理上一个元素的文本内容
            flushTextBuffer();

            // 获取当前路径
            String parentPath = contextStack.isEmpty() ? "" : contextStack.peek().fullPath;

            // 构建当前元素的完整路径（带索引）
            String elementPath = buildIndexedPath(parentPath, qName);

            // 记录上下文
            ElementContext context = new ElementContext(qName, elementPath);
            contextStack.push(context);

            // 添加元素路径
            allPaths.add(elementPath);

            // 添加属性路径
            if (attributes != null) {
                for (int i = 0; i < attributes.getLength(); i++) {
                    String attrPath = elementPath + "/@" + attributes.getQName(i);
                    allPaths.add(attrPath);
                }
            }

            // 重置文本缓冲区
            textBuffer.setLength(0);
            hasTextContent = false;
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            // 累积文本内容
            String text = new String(ch, start, length);
            if (!text.trim().isEmpty()) {
                hasTextContent = true;
                textBuffer.append(text);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            // 处理当前元素的文本内容
            flushTextBuffer();

            // 弹出上下文
            if (!contextStack.isEmpty()) {
                contextStack.pop();
            }
        }

        /**
         * 构建带索引的路径
         */
        private String buildIndexedPath(String parentPath, String elementName) {
            // 生成路径键（用于计数）
            String pathKey = parentPath + "/" + elementName;

            // 获取并增加计数
            int index = siblingCount.getOrDefault(pathKey, 0) + 1;
            siblingCount.put(pathKey, index);

            // 构建带索引的路径
            if (parentPath.isEmpty()) {
                return "/" + elementName + "[" + index + "]";
            } else {
                return parentPath + "/" + elementName + "[" + index + "]";
            }
        }

        /**
         * 刷新文本缓冲区，添加text()路径
         */
        private void flushTextBuffer() {
            if (hasTextContent && !contextStack.isEmpty()) {
                String currentPath = contextStack.peek().fullPath;
                allPaths.add(currentPath + "/text()");
                hasTextContent = false;
                textBuffer.setLength(0);
            }
        }

        @Override
        public void endDocument() {
            flushTextBuffer();
        }

        public List<String> getAllPaths() {
            return new ArrayList<>(allPaths);
        }

        /**
         * 元素上下文类
         */
        private static class ElementContext {
            final String name;
            final String fullPath;

            ElementContext(String name, String fullPath) {
                this.name = name;
                this.fullPath = fullPath;
            }
        }
    }

}
