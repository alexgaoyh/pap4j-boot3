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

}
