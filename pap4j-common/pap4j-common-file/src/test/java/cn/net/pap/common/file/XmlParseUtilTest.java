package cn.net.pap.common.file;

import cn.net.pap.common.file.xml.XmlParseUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

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

}
