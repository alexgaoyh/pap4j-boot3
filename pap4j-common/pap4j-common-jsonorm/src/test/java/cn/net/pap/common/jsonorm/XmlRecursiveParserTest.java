package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.parser.XmlRecursiveParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * XML 递归解析工具类 测试类
 */
public class XmlRecursiveParserTest {

    @Test
    public void parseTest1() throws Exception {
        String xml = """
                <books>
                    <book id="101">
                        <title><![CDATA[XML & XSLT <for> Beginners]]></title>
                        <author>John Doe</author>
                        <description>
                            This is a <![CDATA[special <book> about XML]]> and its features.
                            Normal text here.
                        </description>
                        <year>2020</year>
                    </book>
                    <book id="102">
                        <title>Advanced XML</title>
                        <author>Jane Smith</author>
                        <content><![CDATA[<html><body>XML Content</body></html>]]></content>
                    </book>
                </books>
                """;

        List<Map<String, Object>> result = XmlRecursiveParser.parseToUniversalList(xml);
        System.out.println(result);
    }

    @Test
    public void parseTest2() throws Exception {
        String filePath = "C:\\Users\\86181\\Desktop\\140013020250391.xml";
        if(new File(filePath).exists()) {
            String xml = Files.readString(Paths.get(filePath));
            List<Map<String, Object>> result = XmlRecursiveParser.parseToUniversalList(xml);
            System.out.println(result);

            // 节点解析 数组
            Object catalogItems = XmlRecursiveParser.extract(result, "$[0].catalog[0].catalogItem");
            System.out.println(catalogItems);

            // 节点解析 属性
            Object page = XmlRecursiveParser.extract(result, "$[0].catalog[0].catalogItem[0].@page");
            System.out.println(page);
        }

    }

    @Test
    public void parseTest3() throws Exception {
        String xml = """
                <pap>
                  <details>
                    <detail page="0001" rec="1,1,1,1">0001</detail>
                  </details>
                  <details>
                    <detail page="0002" rec="2,2,2,2">0002.1<geo id="0002.2">0002.2</geo>0002.3<geo>0002.4</geo><geo>0002.5</geo><personal>0002.6</personal>0002.7</detail>
                  </details>
                </pap>
                """;
        List<Map<String, Object>> result = XmlRecursiveParser.parseToUniversalList(xml);
        System.out.println(result);

        // 把解析过得集合重新还原为xml
        String reconstructedXml = XmlRecursiveParser.convertToXmlString(result);
        System.out.println(reconstructedXml);

        // 取特定节点下的数据，然后还原为xml
        List<Map<String, Object>> details2List = (List<Map<String, Object>>)XmlRecursiveParser.extract(result, "$[0]._children[1]._children");
        String reconstructedXml2 = XmlRecursiveParser.convertToXmlString(details2List);
        System.out.println(reconstructedXml2);

    }


}
