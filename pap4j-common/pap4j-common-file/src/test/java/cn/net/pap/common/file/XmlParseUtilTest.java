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

import static org.junit.jupiter.api.Assertions.*;

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

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("任意结构复杂 XML 转换为 List Map 验证")
    public void xmlToMapListTest() throws Exception {
        String complexXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <root>
                    <student id="S001" status="active">
                        <name>张三</name>
                        <age>20</age>
                        <scores>
                            <subject name="math">95</subject>
                            <subject name="english">88</subject>
                        </scores>
                        <extraInfo>
                            <address>河南省许昌市</address>
                            <phone/>
                        </extraInfo>
                    </student>
                    <school>
                        <name>郑州大学</name>
                        <address>郑州市</address>
                    </school>
                </root>
                """;

        List<Map<String, Object>> result = XmlParseUtil.xmlToMapList(complexXml.trim());
        log.info("XML to Map List result: {}", result);

        // 验证结果结构是否正确
        assertTrue(result != null && !result.isEmpty());
        assertTrue(result.size() == 2); // student 和 school 两个直接子节点
        
        // 验证第一个子节点 student
        Map<String, Object> firstChild = result.get(0);
        assertTrue(firstChild.containsKey("student"));
        Map<String, Object> studentMap = (Map<String, Object>) firstChild.get("student");
        assertTrue("S001".equals(studentMap.get("@id")));
        assertTrue("active".equals(studentMap.get("@status")));
        assertTrue("张三".equals(studentMap.get("name")));
        assertTrue("20".equals(studentMap.get("age")));
        
        // 验证包含相同标签的 List 重合节点 scores -> subject
        Map<String, Object> scoresMap = (Map<String, Object>) studentMap.get("scores");
        assertTrue(scoresMap.get("subject") instanceof List);
        List<Map<String, Object>> subjects = (List<Map<String, Object>>) scoresMap.get("subject");
        assertTrue(subjects.size() == 2);
        assertTrue("math".equals(subjects.get(0).get("@name")));
        assertTrue("95".equals(subjects.get(0).get("#text")));
        assertTrue("english".equals(subjects.get(1).get("@name")));
        assertTrue("88".equals(subjects.get(1).get("#text")));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("高级 XML 结构（CDATA、注释、命名空间、格式错误）转换为 List Map 验证")
    public void xmlToMapListAdvancedTest() throws Exception {
        String advancedXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <root>
                    <ns:student id="S002" xmlns:ns="http://example.com/ns">
                        <!-- 这是一个学生注释 -->
                        <name>李四<!-- 姓名内部注释 --></name>
                        <bio><![CDATA[具有 <html/> 标签的简介 & 特殊符号]]></bio>
                    </ns:student>
                </root>
                """;

        List<Map<String, Object>> result = XmlParseUtil.xmlToMapList(advancedXml.trim());
        log.info("Advanced XML to Map List result: {}", result);

        // 1. 验证基本结构
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        // 2. 验证命名空间节点
        Map<String, Object> firstChild = result.get(0);
        assertTrue(firstChild.containsKey("ns:student"));
        Map<String, Object> studentMap = (Map<String, Object>) firstChild.get("ns:student");

        // 3. 验证属性（含命名空间属性声明）
        assertEquals("S002", studentMap.get("@id"));
        assertEquals("http://example.com/ns", studentMap.get("@xmlns:ns"));

        // 4. 验证注释是否被过滤（getTextContent() 不会包含注释，不影响结果）
        assertEquals("李四", studentMap.get("name"));

        // 5. 验证 CDATA 内容解析
        assertEquals("具有 <html/> 标签的简介 & 特殊符号", studentMap.get("bio"));

        // 6. 验证格式错误的 XML 能够抛出异常
        String malformedXml = "<root><student>张三</root>";
        assertThrows(Exception.class, () -> {
            XmlParseUtil.xmlToMapList(malformedXml);
        });
    }

}
