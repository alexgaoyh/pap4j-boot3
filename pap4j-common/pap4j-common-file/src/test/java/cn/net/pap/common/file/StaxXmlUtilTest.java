package cn.net.pap.common.file;

import cn.net.pap.common.file.xml.StaxXmlUtil;
import cn.net.pap.common.file.xml.XmlParseUtil;
import cn.net.pap.common.file.xml.xpath.ExtFunctionResolver;
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
import java.io.ByteArrayInputStream;
import java.io.File;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaxXmlUtilTest {

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
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";

        String xmlText = Files.readString(Paths.get(desktop + File.separator + "input1.xml"));
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
                    System.out.println(value);
                }
            }
        }
    }

    @Test
    public void test2() throws Exception {
        String firstNodeName = "firstNode";
        String secondNodeName = "secondNode";
        String thirdNodeName = "thirdNode";
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";

        String xmlText = Files.readString(Paths.get(desktop + File.separator + "input.xml"));
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
                    System.out.println(value);
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
        System.out.println(props);
        System.out.println(propExts);
        for(String prop : props) {
            String s = StaxXmlUtil.parseXMLInRootAndOriginalTags(prop, "prop", keepOriginalTags);
            System.out.println(s);
        }
        for(String prop : props) {
            Map<String, String> anchorAttrs = StaxXmlUtil.extractAllAttributes(prop, "anchor");
            System.out.println(anchorAttrs);
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
                System.out.println(path + " : " + XmlParseUtil.getValueByXPath(documentByContent, path));
            }
        } catch (Exception e) {
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

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.trim().getBytes(StandardCharsets.UTF_8)));
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
            System.out.println(s);
            System.out.println();
            String s2 = StaxXmlUtil.parseXMLWithCustomAttributes(zhengwen, null, new HashSet<>(), biaoshiMapList);
            System.out.println(s2);
            System.out.println("==================================");

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

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.trim().getBytes(StandardCharsets.UTF_8)));
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

}
