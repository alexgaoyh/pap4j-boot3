package cn.net.pap.common.file;

import cn.net.pap.common.file.xml.StaxXmlUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

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

    }

}
