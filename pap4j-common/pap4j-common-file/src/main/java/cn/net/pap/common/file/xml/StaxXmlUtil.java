package cn.net.pap.common.file.xml;

import com.ibm.icu.text.BreakIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StaxXmlUtil {

    private static final Logger log = LoggerFactory.getLogger(StaxXmlUtil.class);

    // 将 Factory 设为静态单例，避免每次调用方法时重复创建的巨大开销. XMLInputFactory 是线程安全的（大部分实现中），或者至少创建 Reader 的方法是线程安全的
    private static final XMLInputFactory factory;

    // 将 SAXParserFactory 提取为静态单例，避免 extractAllPaths 频繁创建带来的性能损耗
    private static final SAXParserFactory saxFactory;

    // 预编译 Pattern，提升性能
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("(<anchor[^>]*(?:/>|>\\s*</anchor>))");

    static {
        factory = XMLInputFactory.newInstance();
        // 优化配置：合并连续文本、关闭命名空间（对应原逻辑）
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        // 禁用命名空间处理，只返回本地名称
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);

        // 强制关闭 DTD 和外部实体解析，防御 XXE 注入漏洞
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        // 初始化并安全配置 SAXParserFactory
        saxFactory = SAXParserFactory.newInstance();
        try {
            saxFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            saxFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            saxFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception e) {
            log.error("SAXParserFactory 安全配置失败", e);
        }
    }

    /**
     * 获取指定节点下的所有子节点 XML 文本
     *
     * @param xmlText  XML 文本
     * @param nodeName 节点名，例如 "personal"
     * @return List，每个元素是一个子节点的 XML 文本
     */
    public static List<String> readChildrenXmlByStax(String xmlText, String nodeName) {
        if (xmlText == null || xmlText.isBlank() || nodeName == null) {
            return Collections.emptyList();
        }
        
        List<String> result = new ArrayList<>();
        XMLStreamReader reader = null;
        try (StringReader sr = new StringReader(xmlText)) {

            reader = factory.createXMLStreamReader(sr);

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
        } catch (Exception e) {
            log.error("StAX 解析失败", e);
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.warn("关闭 XMLStreamReader 失败", e);
                }
            }
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
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
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
    public static Optional<String> readNodeValueByStax(String xmlText, String nodeName) {
        if (xmlText == null || xmlText.isBlank() || nodeName == null) {
            return Optional.empty();
        }
        
        XMLStreamReader reader = null;
        try {
            // 移除内部重复实例化的 XMLInputFactory，复用静态 factory 提升性能
            reader = factory.createXMLStreamReader(new StringReader(xmlText));

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && nodeName.equals(reader.getLocalName())) {
                    // 读取文本
                    StringBuilder textSb = new StringBuilder(); // 替换 String += 拼接，避免 GC 压力
                    while (reader.hasNext()) {
                        event = reader.next();
                        if (event == XMLStreamConstants.CHARACTERS) {
                            textSb.append(escapeXml(reader.getText()));
                        } else if (event == XMLStreamConstants.END_ELEMENT && nodeName.equals(reader.getLocalName())) {
                            return Optional.of(textSb.toString().trim());
                        }
                    }
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("StAX 解析失败", e);
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.warn("关闭 XMLStreamReader 失败", e);
                }
            }
        }
    }

    /**
     * @param xmlText
     * @param parentNodeName
     * @return
     */
    public static Optional<String> readChildrenXmlValueByStax(String xmlText, String parentNodeName) {
        if (xmlText == null || xmlText.isBlank() || parentNodeName == null) {
            return Optional.empty();
        }

        XMLStreamReader reader = null;
        try {
            // 复用静态 factory
            reader = factory.createXMLStreamReader(new StringReader(xmlText));

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
                            return Optional.of(sb.toString());
                        }
                    }
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("StAX 解析失败", e);
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.warn("关闭 XMLStreamReader 失败", e);
                }
            }
        }
    }


    /**
     * 统计节点数量
     */
    public static int countNodesByStax(String xmlText, String nodeName) {
        if (xmlText == null || xmlText.isBlank() || nodeName == null) {
            return 0;
        }

        int count = 0;
        XMLStreamReader reader = null;
        try {
            // 复用静态 factory
            reader = factory.createXMLStreamReader(new StringReader(xmlText));

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && nodeName.equals(reader.getLocalName())) {
                    count++;
                }
            }

            return count;
        } catch (Exception e) {
            log.error("StAX 解析失败", e);
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.warn("关闭 XMLStreamReader 失败", e);
                }
            }
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
        if (xmlString == null || xmlString.isBlank()) {
            return xmlString;
        }

        StringBuilder result = new StringBuilder("<").append(rootTag).append(">");
        int charSeq = 0;
        XMLStreamReader reader = null;
        try {
            // 复用静态 factory，移除局部的 newInstance()，静态块中已经配置过 IS_COALESCING = true
            reader = factory.createXMLStreamReader(new StringReader(xmlString));

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
            result.append("</").append(rootTag).append(">");
        } catch (Exception e) {
            log.error("StAX 解析失败", e);
            return xmlString;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.warn("关闭 XMLStreamReader 失败", e);
                }
            }
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
            return Collections.emptyMap();
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
            log.error("StAX 解析失败", e);
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.warn("关闭 XMLStreamReader 失败", e);
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

        XMLStreamReader reader = null;
        try {
            // 复用类中已有的静态线程安全 factory
            reader = factory.createXMLStreamReader(new StringReader(xmlText));

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

        } catch (Exception e) {
            log.error("StAX 解析失败", e);
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.warn("关闭 XMLStreamReader 失败", e);
                }
            }
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
        if (xmlString == null || xmlString.isBlank()) {
            return xmlString;
        }

        StringBuilder result = new StringBuilder("<").append(rootTag).append(">");
        int charIndex = 0; // 用于追踪当前字符在 attrList 中的全局下标

        XMLStreamReader reader = null;
        try {
            // 复用类中已有的 factory
            reader = factory.createXMLStreamReader(new StringReader(xmlString));

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
            // 移除了原本放在 try 块末尾冗余的 reader.close(); ，统一交由 finally 处理
            result.append("</").append(rootTag).append(">");
        } catch (Exception e) {
            log.error("StAX 解析失败", e);
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.warn("关闭 XMLStreamReader 失败", e);
                }
            }
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
        if (xmlContent == null || xmlContent.isBlank()) {
            return Collections.emptyList();
        }

        // 复用配置的静态单例 saxFactory
        SAXParser parser = saxFactory.newSAXParser();
        IndexedPathHandler handler = new IndexedPathHandler();
        parser.parse(new InputSource(new StringReader(xmlContent)), handler);
        return handler.getAllPaths();
    }

    /**
     * 根据 {@code <anchor.../>} 标签对传入的 XML 字符串进行切分，并将切分后的段落映射为键值对返回。
     * <p>
     * 本方法使用 {@link LinkedHashMap} 以保证返回结果与原始字符串中的出现顺序一致。
     * 具体的切分规则与边界情况处理如下：
     * </p>
     * <ul>
     * <li><strong>常规切分：</strong>以 {@code <anchor/>} 标签为界，将标签前的内容（去除首尾空格）作为 value，当前的 {@code <anchor/>} 标签完整文本作为 key。</li>
     * <li><strong>空值安全：</strong>传入 {@code null} 或纯空白字符串时，直接返回空 Map。</li>
     * <li><strong>纯文本（无标签）：</strong>如果输入字符串中没有任何 anchor 标签，去空后的整体内容将以 {@code "_initial_content"} 作为 key 存入。</li>
     * <li><strong>连续标签：</strong>当两个 anchor 标签紧挨着（或中间只有空白字符）时，后一个标签仍会作为 key 存入 Map，其对应的 value 为空字符串 {@code ""}。</li>
     * <li><strong>尾部内容捕获：</strong>如果最后一个 anchor 标签之后还有非空字符，这些字符将以 {@code "_tail_content"} 作为 key 存入。如果尾部仅有空白字符则会被安全过滤。</li>
     * </ul>
     *
     * @param xmlString 需要进行切分的原始 XML 字符串
     * @return 包含切分结果的有序 {@code Map<String, String>}
     */
    public static Map<String, String> splitByAnchor(String xmlString) {
        Map<String, String> result = new LinkedHashMap<>();
        if (xmlString == null || xmlString.trim().isEmpty()) {
            return result;
        }

        Matcher matcher = ANCHOR_PATTERN.matcher(xmlString);
        int lastEnd = 0;
        boolean firstSegment = true;
        while (matcher.find()) {
            // 获取当前 anchor 之前的内容
            String content = xmlString.substring(lastEnd, matcher.start()).trim();
            String anchorKey = matcher.group(1);

            // 这样即使两个 anchor 紧挨着，也能保留后一个 anchor 作为 key，value 为 ""
            result.put(anchorKey, content);

            lastEnd = matcher.end();
            firstSegment = false;
        }
        // 处理第一段内容（没有任何 anchor 的情况）
        if (firstSegment) {
            result.put("_initial_content", xmlString.trim());
        }
        // 处理最后一个 anchor 之后的尾部内容
        // 如果输入是 "123<anchor/>456"，循环结束后 456 会丢失，这里将其捕获
        if (!firstSegment && lastEnd < xmlString.length()) {
            String tailContent = xmlString.substring(lastEnd).trim();
            if (!tailContent.isEmpty()) {
                result.put("_tail_content", tailContent);
            }
        }
        return result;
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
