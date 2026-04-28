package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.file.xml.XmlParseUtil;
import cn.net.pap.common.file.xml.record.Segment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmlParseUtilTest {
    private static final Logger log = LoggerFactory.getLogger(XmlParseUtilTest.class);

    /**
     * 更新xml中的特定节点
     */
    // @Test
    public void test1() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";

        Map<String, String> updateMap = new HashMap<String, String>();
        updateMap.put("/a/b/c/d", "123456");

        XmlParseUtil.updateXmlByXPath(desktop + File.separator + "input.xml", updateMap);
    }

    // @Test
    public void test2() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";

        Document documentByPath = XmlParseUtil.getDocumentByPath(desktop + File.separator + "input.xml");

        Map<String, String> nodeMap = new HashMap<>();
        nodeMap.put("chapterContents", "/contents");

        List<Map<String, Object>> result = XmlParseUtil.parse(documentByPath, "/root/chapter", nodeMap,
                null, null, null, null, ";", null);

        log.info("{}", result);

    }

    @Test
    public void test3() throws Exception {
        String firstNodeName = "student";
        String secondNodeName = "parent";
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";

        if(new File(desktop + File.separator + "input.xml").exists()) {
            Document documentByPath = XmlParseUtil.getDocumentByPath(desktop + File.separator + "input.xml");

            NodeList firstResultList = XmlParseUtil.parseChild(documentByPath, "/root/" + firstNodeName);

            for (int i = 0; i < firstResultList.getLength(); i++) {
                NodeList secondResultList = XmlParseUtil.parseChild(documentByPath, "/root/" + firstNodeName + "[" + i + "]/" + secondNodeName);
                for (int j = 0; j < secondResultList.getLength(); j++) {
                    Node secondNode = secondResultList.item(j);
                    StringBuilder sb = new StringBuilder();
                    for(int k = 0; k < secondNode.getChildNodes().getLength(); k++) {
                        Node thirdNode = secondNode.getChildNodes().item(k);
                        if (thirdNode.getNodeType() == Node.ELEMENT_NODE) {
                            sb.append(XmlParseUtil.getInnerContent(thirdNode) + "\n");
                        }
                    }
                    Map<String, String> anchorMap = XmlParseUtil.splitByAnchor(sb.toString());
                    for (Map.Entry<String, String> entry : anchorMap.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        log.info("{}", key + "=" + value);
                    }

                }
            }
        }

    }

    @Test
    @DisplayName("根据层级提取xml的结构并返回,获取XML指定层级的节点，保留完整根节点结构")
    public void getNodesByLevelTest() throws Exception {
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
        String nodesByLevel1 = XmlParseUtil.getXmlByLevel(xml.trim(), 1, true);
        String nodesByLevel2 = XmlParseUtil.getXmlByLevel(xml.trim(), 2, true);
        String nodesByLevel3 = XmlParseUtil.getXmlByLevel(xml.trim(), 3, true);
        String nodesByLevel4 = XmlParseUtil.getXmlByLevel(xml.trim(), 4, true);
        log.info("");
        assertTrue(nodesByLevel1.equals("<student/>"));
        assertTrue(nodesByLevel2.equals("<student><props/><propExts/></student>"));
        assertTrue(nodesByLevel3.equals("<student><props><prop/><prop/><prop/></props><propExts><propExt/><propExt/><propExt/></propExts></student>"));
    }

    @Test
    @DisplayName("xml字符串分割anchor，同时补充缺失节点")
    public void splitAnchorTest() throws Exception {
        String xml = """
                    二<glass id="002">章</glass><class id="002">内容<anchor fileName="1" pageNum="1" />测试</class>正文<anchor fileName="2" pageNum="2" />结尾
                """;
        List<Segment> segments = XmlParseUtil.splitByAnchorAddMissingNode(xml.trim());
        segments.forEach(s -> log.info("{}", s));
    }

}
