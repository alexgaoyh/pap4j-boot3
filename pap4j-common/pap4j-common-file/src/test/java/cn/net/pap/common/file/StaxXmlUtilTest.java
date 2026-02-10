package cn.net.pap.common.file;

import cn.net.pap.common.file.xml.StaxXmlUtil;
import cn.net.pap.common.file.xml.XmlParseUtil;
import cn.net.pap.common.file.xml.xpath.ExtFunctionResolver;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                    String value = StaxXmlUtil.readNodeValueByStax(thirdNodeXML, thirdNodeName);
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
                    String value = StaxXmlUtil.readChildrenXmlValueByStax(thirdNodeXML, thirdNodeName);
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
            String propInnerXml = (String) xpath.evaluate("ext:inner-xml(.)", propNodes.item(i), XPathConstants.STRING);
            assertTrue(propInnerXml.contains("</anchor>"));
        }


    }

}
