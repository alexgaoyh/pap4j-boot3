package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.file.xml.StaxXmlUtil;
import cn.net.pap.common.file.xml.XmlParseUtil;
import cn.net.pap.common.file.xml.xpath.ExtFunctionResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.xml.sax.InputSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaxXmlUtilTest {
    private static final Logger log = LoggerFactory.getLogger(StaxXmlUtilTest.class);
    private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

    /**
     * <root><firstNode><secondNode><thirdNode>alexgaoyh</thirdNode></secondNode></firstNode></root>
     * <p>
     * parse big xml
     * 使用 Stax 按照节点名称来读取节点对应的xml文本，然后将xml文本传入下一个解析节点，因为解析文本的内容越来越少，所以速度还行.
     *
     * @throws Exception
     */
    @Test
    public void test1() throws Exception {
        String firstNodeName = "firstNode";
        String secondNodeName = "secondNode";
        String thirdNodeName = "thirdNode";

        String xmlText = Files.readString(Paths.get(TestResourceUtil.getFile("input1.xml").getAbsolutePath().toString()));
        // 处理 BOM 头
        xmlText = xmlText.startsWith("\uFEFF") ? xmlText.substring(1) : xmlText;

        // 第一层节点
        List<String> firstNodeXMLs = StaxXmlUtil.readChildrenXmlByStax(xmlText, firstNodeName);
        for (String firstNodeXML : firstNodeXMLs) {
            // 第二层节点
            List<String> secondNodeXMLs = StaxXmlUtil.readChildrenXmlByStax(firstNodeXML, secondNodeName);
            for (String secondNodeXML : secondNodeXMLs) {
                // 第三层节点
                List<String> thirdNodeXMLs = StaxXmlUtil.readChildrenXmlByStax(secondNodeXML, thirdNodeName);
                for (String thirdNodeXML : thirdNodeXMLs) {
                    String value = StaxXmlUtil.readNodeValueByStax(thirdNodeXML, thirdNodeName).orElse(null);
                    log.info("{}", value);
                }
            }
        }
    }

    @Test
    public void test2() throws Exception {
        String firstNodeName = "firstNode";
        String secondNodeName = "secondNode";
        String thirdNodeName = "thirdNode";

        String xmlText = Files.readString(Paths.get(TestResourceUtil.getFile("input1.xml").getAbsolutePath().toString()));
        // 处理 BOM 头
        xmlText = xmlText.startsWith("\uFEFF") ? xmlText.substring(1) : xmlText;
        // 第一层节点
        List<String> firstNodeXMLs = StaxXmlUtil.readChildrenXmlByStax(xmlText, firstNodeName);
        for (String firstNodeXML : firstNodeXMLs) {
            // 第二层节点
            List<String> secondNodeXMLs = StaxXmlUtil.readChildrenXmlByStax(firstNodeXML, secondNodeName);
            for (String secondNodeXML : secondNodeXMLs) {
                // 第三层节点
                List<String> thirdNodeXMLs = StaxXmlUtil.readChildrenXmlByStax(secondNodeXML, thirdNodeName);
                for (String thirdNodeXML : thirdNodeXMLs) {
                    String value = StaxXmlUtil.readChildrenXmlValueByStax(thirdNodeXML, thirdNodeName).orElse(null);
                    log.info("{}", value);
                }
            }
        }
    }

    @Test
    public void test3() throws Exception {
        Set<String> keepOriginalTags = new HashSet<String>();
        keepOriginalTags.add("class");
        keepOriginalTags.add("glass");
        keepOriginalTags.add("asdfg");
        keepOriginalTags.add("anchor");
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <student>
              <props>
                <prop>一<class id="001">章</class>内&gt;容<anchor number="1"></anchor></prop>
                <prop>二<glass id="002">章</glass>内容<anchor number="2"></anchor></prop>
                <prop>三章内<asdfg id="003">容</asdfg><anchor number="3"></anchor></prop>
              </props>
              <propExts>
                <propExt>1;2;3;4</propExt>
                <propExt>q;w;e;r</propExt>
                <propExt>a;s;d;f</propExt>
              </propExts>
            </student>
        """;
        List<String> props = StaxXmlUtil.readChildrenXmlByStax(xml.trim(), "prop");
        List<String> propExts = StaxXmlUtil.readChildrenXmlByStax(xml.trim(), "propExt");
        log.info("{}", props);
        log.info("{}", propExts);
        for(String prop : props) {
            String s = StaxXmlUtil.parseXMLInRootAndOriginalTags(prop, "prop", keepOriginalTags);
            log.info("{}", s);
        }
        for(String prop : props) {
            Map<String, String> anchorAttrs = StaxXmlUtil.extractAllAttributes(prop, "anchor");
            log.info("{}", anchorAttrs);
        }

    }

    @Test
    public void test4() {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <student>
              <props>
                <prop>一<class id="001">章</class>内&gt;容<anchor number="1"></anchor></prop>
                <prop>二<glass id="002">章</glass>内容<anchor number="2"></anchor></prop>
                <prop>三章内<asdfg id="003">容</asdfg><anchor number="3"></anchor></prop>
              </props>
              <propExts>
                <propExt>1;2;3;4</propExt>
                <propExt>q;w;e;r</propExt>
                <propExt>a;s;d;f</propExt>
              </propExts>
            </student>
        """;
        try {
            Document documentByContent = XmlParseUtil.getDocumentByContent(xml.trim());
            List<String> paths = StaxXmlUtil.extractAllPaths(xml.trim());
            for (String path : paths) {
                log.info("{}", path + " : " + XmlParseUtil.getValueByXPath(documentByContent, path));
            }
        } catch (Exception e) {
            log.error("解析 XML 并提取路径失败", e);
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInnerXml() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <student>
              <props>
                <prop>一<class id="001">章</class>内&gt;容<anchor number="1"></anchor></prop>
                <prop>二<glass id="002">章</glass>内容<anchor number="2"></anchor></prop>
                <prop>三章内<asdfg id="003">容</asdfg><anchor number="3"></anchor></prop>
              </props>
              <propExts>
                <propExt>1;2;3;4</propExt>
                <propExt>q;w;e;r</propExt>
                <propExt>a;s;d;f</propExt>
              </propExts>
            </student>
        """;

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml.trim())));
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setXPathFunctionResolver(new ExtFunctionResolver());
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if ("ext".equals(prefix)) {
                    return ExtFunctionResolver.EXT_NS;
                }
                return null;
            }
            @Override public String getPrefix(String uri) { return null; }
            @Override public Iterator<String> getPrefixes(String uri) { return null; }
        });

        NodeList anchorNodes = (NodeList) xpath.evaluate("//anchor", doc, XPathConstants.NODESET);
        for (int i = 0; i < anchorNodes.getLength(); i++) {
            Element anchorElement = (Element)anchorNodes.item(i);
            assertTrue(anchorElement.hasAttribute("number"));
        }


        // language=TEXT
        String result = (String) xpath.evaluate("ext:inner-xml(/student[1]/props[1]/prop[1])", doc, XPathConstants.STRING);
        assertTrue(result.contains("一"));
        assertTrue(result.contains("<class id=\"001\">章</class>"));
        assertTrue(result.contains("内&gt;容"));

        String result2 = (String) xpath.evaluate("/student[1]/props[1]/prop[1]", doc, XPathConstants.STRING);
        assertTrue(result2.contains("一"));
        assertTrue(!result2.contains("<class id=\"001\">章</class>"));
        assertTrue(result2.contains("内>容"));

        // 如果是多个值，那么循环解析，并且保证结构不发生变化。
        NodeList propNodes = (NodeList) xpath.evaluate("/student[1]/props[1]/prop", doc, XPathConstants.NODESET);
        for (int i = 0; i < propNodes.getLength(); i++) {
            // language=TEXT
            String propInnerXml = (String) xpath.evaluate("ext:inner-xml(.)", propNodes.item(i), XPathConstants.STRING);
            assertTrue(propInnerXml.contains("</anchor>"));
        }

        // xpath 语法， 获取符合条件的节点
        XPathExpression propAnchorXpath = xpath.compile("/student/props/prop[anchor/@number='1']");
        NodeList nodes = (NodeList) propAnchorXpath.evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            // language=TEXT
            assertTrue(((String) xpath.evaluate("ext:inner-xml(.)", nodes.item(i), XPathConstants.STRING)).contains("</anchor>"));
        }

        // xpath 自定义函数，查询到的节点在父节点下的索引位置(从1开始)
        // language=TEXT
        String positions = (String) xpath.evaluate("ext:position-in-parent(/student/props/prop[anchor/@number='1'])", doc, XPathConstants.STRING);
        assertTrue(positions.contains("1"));

    }

    /**
     * 重新生成 xml
     */
    @Test
    public void reGeneXMLTest() {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <book>
              <zhengwens>
                <zhengwen>
                    <Province>河南</Province> &amp; 许昌 😊
                    <anchor id="101"></anchor>
                    测试
                </zhengwen>
                <zhengwen>验证</zhengwen>
              </zhengwens>
              <biaoshis>
                <biaoshi>1;2;3;4;5;6;7;8;9;10;11;</biaoshi>
                <biaoshi>12;13;</biaoshi>
              </biaoshis>
            </book>
        """;
        List<String> zhengwens = StaxXmlUtil.readChildrenXmlByStax(xml.trim(), "zhengwen");
        List<String> biaoshis = StaxXmlUtil.readChildrenXmlByStax(xml.trim(), "biaoshi");
        for(int zhengwenIdx = 0; zhengwenIdx < zhengwens.size(); zhengwenIdx++) {
            String zhengwen = zhengwens.get(zhengwenIdx);
            // 【仅处理换行与回车】 1. 将所有的 \r (回车) 和 \n (换行) 直接替换为空字符串 2. 这样可以把多行合并为一行，同时保留原有的空格字符
            zhengwen = zhengwen.replaceAll("[\\r\\n]+\\s*", "");
            String biaoshi = StaxXmlUtil.concatAllNodeValuesByStax(biaoshis.get(zhengwenIdx).trim(), "biaoshi");
            List<Map<String, String>> biaoshiMapList = new ArrayList<>();
            String[] biaoshiSplit = biaoshi.split(";");
            for(int idx = 0; idx < biaoshiSplit.length; idx++) {
                if(biaoshiSplit[idx] != null && !"".endsWith(biaoshiSplit[idx])) {
                    Map<String, String> attrMap = new HashMap<>();
                    attrMap.put("class", "chars");
                    attrMap.put("data-type", "字符");
                    attrMap.put("data-sign", biaoshiSplit[idx]);
                    biaoshiMapList.add(attrMap);
                }
            }
            String s = StaxXmlUtil.parseXMLWithCustomAttributes(zhengwen, "zhengwen", new HashSet<>(), biaoshiMapList);
            log.info("{}", s);
            log.info("");
            String s2 = StaxXmlUtil.parseXMLWithCustomAttributes(zhengwen, null, new HashSet<>(), biaoshiMapList);
            log.info("{}", s2);
            log.info("==================================");

        }

    }

    @Test
    public void nodeNameSplitTest() throws Exception {
        Map<String, String> map0 = StaxXmlUtil.splitByAnchor(null);
        assertTrue(map0.size() == 0, "切分map0");
        map0 = StaxXmlUtil.splitByAnchor("");
        assertTrue(map0.size() == 0, "切分map0");

        String xml1 = """
                123<anchor pageNum="35"/>456<anchor pageNum="36"/><anchor pageNum="37"/>
                """;
        Map<String, String> map1 = StaxXmlUtil.splitByAnchor(xml1.trim());
        assertTrue(map1.size() == 3, "切分map1");

        String xml2 = """
                123<anchor pageNum="35"/>456<anchor pageNum="36"/><anchor pageNum="37"/>789
                """;
        Map<String, String> map2 = StaxXmlUtil.splitByAnchor(xml2.trim());
        assertTrue(map2.size() == 4, "切分map2");
        assertTrue(map2.containsKey("_tail_content"));

        String xml3 = """
                123
                """;
        Map<String, String> map3 = StaxXmlUtil.splitByAnchor(xml3.trim());
        assertTrue(map3.size() == 1, "切分map3");
        assertTrue(map3.containsKey("_initial_content"));

        String xml4 = """
                <anchor pageNum="36"/><anchor pageNum="37"/>
                """;
        Map<String, String> map4 = StaxXmlUtil.splitByAnchor(xml4.trim());
        assertTrue(map4.size() == 2, "切分map4");

        String xml5 = """
                <anchor pageNum="36"/><anchor pageNum="37"/>     
                """;
        Map<String, String> map5 = StaxXmlUtil.splitByAnchor(xml5);
        assertTrue(map5.size() == 2, "切分map5");

        String xml6 = """
                123<anchor pageNum="1"></anchor>456
                """;
        Map<String, String> map6 = StaxXmlUtil.splitByAnchor(xml6);
        assertTrue(map6.size() == 2, "切分map6");

        Map<String, String> map5Attrs = StaxXmlUtil.extractAllAttributes(map5.keySet().toArray()[0] + "", "anchor");
        assertTrue(map5Attrs.containsKey("pageNum"), "map5属性");

        Map<String, String> map6Attrs = StaxXmlUtil.extractAllAttributes(map6.keySet().toArray()[0] + "", "anchor");
        assertTrue(map6Attrs.containsKey("pageNum"), "map6属性");


    }

    @Test
    public void testFindSiblingsFromTextNode() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <root>
                <content>
                  <text>123<anchor pageNum="1"/>456</text>
                  <rect>1;2;3;4;5;6;</rect>
                  <translation>qwertyuiop</translation>
                </content>
                <content>
                  <text>789<anchor pageNum="2"/></text>
                  <rect>0;9;8;</rect>
                  <translation>zxc</translation>
                </content>
            </root>
        """;

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml.trim())));
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setXPathFunctionResolver(new ExtFunctionResolver());
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if ("ext".equals(prefix)) {
                    return ExtFunctionResolver.EXT_NS;
                }
                return null;
            }
            @Override public String getPrefix(String uri) { return null; }
            @Override public Iterator<String> getPrefixes(String uri) { return null; }
        });

        NodeList textNodes = (NodeList) xpath.evaluate("//text", doc, XPathConstants.NODESET);

        assertEquals(2, textNodes.getLength());

        for (int i = 0; i < textNodes.getLength(); i++) {
            Node currentTextNode = textNodes.item(i);

            assertTrue(currentTextNode.getTextContent() != null , "不为空");
            assertEquals("text", currentTextNode.getNodeName());
            // language=TEXT
            String currentTextNodeInnerXml = (String) xpath.evaluate("ext:inner-xml(.)", currentTextNode, XPathConstants.STRING);
            assertTrue(currentTextNodeInnerXml != null , "不为空");

            // 方式一：使用 following-sibling 轴，表示查找当前节点后面紧挨着的同级 rect 节点
            Node rectNode = (Node) xpath.evaluate("following-sibling::rect[1]", currentTextNode, XPathConstants.NODE);
            assertNotNull(rectNode, "未找到同级的 rect 节点");
            assertTrue(rectNode.getTextContent() != null , "不为空");
            assertEquals("rect", rectNode.getNodeName());

            // 方式二：通过返回父节点再去寻找指定的子节点 (../translation)
            Node translationNode = (Node) xpath.evaluate("../translation", currentTextNode, XPathConstants.NODE);
            assertNotNull(translationNode, "未找到同级的 translation 节点");
            assertTrue(translationNode.getTextContent() != null , "不为空");
            assertEquals("translation", translationNode.getNodeName());

        }
    }

    @Test
    public void crossReferenceFunctionTest() throws Exception {
        String schoolXml = """
           <?xml version="1.0" encoding="utf-8"?>
           <school>
             <grades>
               <grade id="G1" gradeTitle="一年级" teacher="王老师" />
               <grade id="G2" gradeTitle="二年级" teacher="张老师" />
             </grades>
             <students>
               <student name="小明" gradeId="G1" age="7" />
               <student name="小红" gradeId="G2" age="8" />
             </students>
           </school>
           """;
        Document schoolDoc = XmlParseUtil.getDocumentByContent(schoolXml.trim());
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setXPathFunctionResolver(new ExtFunctionResolver());
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if ("ext".equals(prefix)) {
                    return ExtFunctionResolver.EXT_NS;
                }
                return null;
            }
            @Override public String getPrefix(String uri) { return null; }
            @Override public Iterator<String> getPrefixes(String uri) { return null; }
        });

        // 逻辑：拿着小明的 gradeId (G1)，去查找 grade 节点的 id 为 G1 的 gradeTitle
        String gradeTitle = (String) xpath.evaluate(
            // language=TEXT
            "ext:xref(/, //student[@name='小明']/@gradeId, 'grade', 'id', 'gradeTitle')",
             schoolDoc, XPathConstants.STRING
         );

         assertEquals("一年级", gradeTitle);

         // 同样可以灵活获取老师名字（只需改变最后一个参数，即“出参”）
         String teacherName = (String) xpath.evaluate(
             // language=TEXT
             "ext:xref(/, //student[@name='小红']/@gradeId, 'grade', 'id', 'teacher')",
             schoolDoc, XPathConstants.STRING
         );
          assertEquals("张老师", teacherName);
     }

    @Test
    public void testInnerXmlConcurrency() {
        XPath xpath = XPATH_FACTORY.newXPath();
        xpath.setXPathFunctionResolver(new ExtFunctionResolver());
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if ("ext".equals(prefix)) {
                    return ExtFunctionResolver.EXT_NS;
                }
                return null;
            }
            @Override public String getPrefix(String uri) { return null; }
            @Override public Iterator<String> getPrefixes(String uri) { return null; }
        });

        java.util.stream.IntStream.range(0, 500).parallel().forEach(idx -> {
            try {
                String uniqueId = "ID-" + idx;
                String dynamicXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <student>
                      <props>
                        <prop>一<class id="%s">章</class>内&gt;容<anchor number="1"></anchor></prop>
                      </props>
                    </student>
                    """.formatted(uniqueId);

                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                        new InputSource(new StringReader(dynamicXml))
                );
                // language=TEXT
                String result = (String) xpath.evaluate("ext:inner-xml(/student[1]/props[1]/prop[1])", doc, XPathConstants.STRING);
                
                assertTrue(result.contains("id=\"" + uniqueId + "\""), "Data contamination! Expected: " + uniqueId + " but got: " + result);
                assertTrue(result.contains("一"));
            } catch (Exception e) {
                log.error("Concurrency test failed at index: {}", idx, e);
                org.junit.jupiter.api.Assertions.fail("Concurrency issue detected: " + e.getMessage());
            }
        });
    }

    @Test
    @DisplayName("StaxXmlUtil 增量综合: 一对多/嵌套/重复同级/CDATA/转义/边界/计数/属性/异常")
    public void staxXmlUtilIncrementalTest() {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <root>
              <students>
                <student id="S1"><name>小明</name><age>7</age><pic>P1</pic><pic>P2</pic><photo><src>a.jpg</src><desc>图A</desc></photo><photo><src>b.jpg</src><desc>图B</desc></photo></student>
                <student id="S2"><name>小红</name><age>8</age><pic>P3</pic><photo><src>c.jpg</src><desc>图C</desc></photo></student>
              </students>
              <library><shelf code="A"><book><title>书一</title></book><book><title>书二</title></book></shelf><shelf code="B"><book><title>书三</title></book></shelf></library>
              <cdata><item><![CDATA[<b>加粗</b> & 符号]]></item></cdata>
            </root>
            """;
        String src = xml.trim();

        // 一对多 + 属性保留 + 递归取子值
        List<String> students = StaxXmlUtil.readChildrenXmlByStax(src, "student");
        assertEquals(2, students.size(), "student 一对多数量");
        assertTrue(students.get(0).startsWith("<student id=\"S1\">"), "首个节点应保留属性");
        assertEquals("小明", StaxXmlUtil.readNodeValueByStax(students.get(0), "name").orElse(null));
        assertEquals("8", StaxXmlUtil.readNodeValueByStax(students.get(1), "age").orElse(null));

        // 无外层包装的重复同级子节点
        List<String> pics = StaxXmlUtil.readChildrenXmlByStax(src, "pic");
        assertEquals(3, pics.size(), "无 pics 包装的 pic 应全量提取");
        assertEquals("<pic>P1</pic>", pics.get(0));
        assertEquals(3, StaxXmlUtil.countNodesByStax(src, "pic"), "跨层级计数");

        // 与 pic 类似、但内含子节点的重复同级 photo（无外层包装）
        List<String> photos = StaxXmlUtil.readChildrenXmlByStax(src, "photo");
        assertEquals(3, photos.size(), "无 photo 包装的 photo 应全量提取");
        assertTrue(photos.get(0).contains("<src>a.jpg</src>") && photos.get(0).contains("<desc>图A</desc>"), "photo 子节点应完整保留");
        assertEquals("b.jpg", StaxXmlUtil.readNodeValueByStax(photos.get(1), "src").orElse(null));
        assertEquals("图C", StaxXmlUtil.readNodeValueByStax(photos.get(2), "desc").orElse(null));

        // 多层嵌套下的各自一对多
        List<String> shelves = StaxXmlUtil.readChildrenXmlByStax(src, "shelf");
        assertEquals(2, shelves.size(), "shelf 数量");
        assertEquals(2, StaxXmlUtil.readChildrenXmlByStax(shelves.get(0), "book").size(), "A 架书数");
        List<String> booksOfB = StaxXmlUtil.readChildrenXmlByStax(shelves.get(1), "book");
        assertEquals(1, booksOfB.size(), "B 架书数");
        assertEquals("书三", StaxXmlUtil.readNodeValueByStax(booksOfB.get(0), "title").orElse(null));

        // CDATA 转义保留
        List<String> items = StaxXmlUtil.readChildrenXmlByStax(src, "item");
        assertTrue(items.get(0).contains("&lt;b&gt;加粗&lt;/b&gt;") && items.get(0).contains("&amp;"), "CDATA 内容应转义保留");
        assertEquals("<b>加粗</b> & 符号", StaxXmlUtil.extractText(items.get(0)));

        // 转义往返 + null / 未知实体
        String raw = "a<b>&\"'";
        assertEquals(raw, StaxXmlUtil.unescapeXml(StaxXmlUtil.escapeXml(raw)), "转义应无损往返");
        assertNull(StaxXmlUtil.escapeXml(null), "null 应原样返回");

        // 未匹配 / 空输入安全
        assertTrue(StaxXmlUtil.readChildrenXmlByStax(src, "missing").isEmpty(), "未匹配应返回空集合");
        assertTrue(StaxXmlUtil.readChildrenXmlByStax(null, "pic").isEmpty(), "null 输入应返回空集合");
        assertEquals(Optional.empty(), StaxXmlUtil.readNodeValueByStax(src, "missing"), "未匹配应返回 Optional.empty");
        assertEquals(0, StaxXmlUtil.countNodesByStax(src, "missing"), "未匹配计数应为 0");

        // 属性提取 + 畸形 XML 异常
        assertEquals("JAVA", StaxXmlUtil.extractAllAttributes("<book isbn=\"001\" title=\"JAVA\" author=\"PAP\"/>", "book").get("title"));
        assertThrows(RuntimeException.class, () -> StaxXmlUtil.readChildrenXmlByStax("<root><a>1</b></root>", "a"), "标签不匹配应抛异常");
    }

}
