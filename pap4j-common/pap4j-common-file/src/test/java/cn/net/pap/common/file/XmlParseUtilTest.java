package cn.net.pap.common.file;

import cn.net.pap.common.file.xml.XmlParseUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class XmlParseUtilTest {

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

        System.out.println(result);

    }

    @Test
    public void test3() throws Exception {
        String firstNodeName = "student";
        String secondNodeName = "parent";
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";

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
                    System.out.println(key + "=" + value);
                }

            }
        }

    }

}
