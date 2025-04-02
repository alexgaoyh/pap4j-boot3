package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.util.ArrayList;
import java.util.List;

public class ParentChildXMLParseTest {

    // @Test
    public void parseParentChild() throws Exception {
        // 安全配置：禁用外部实体解析以防止XXE攻击
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        /**
         * <book>
         *   <chapter title="第一章">
         *     <chapter title="第一节">
         *       <chapter title="第一小节"/>
         *     </chapter>
         *     <chapter title="第二节"/>
         *   </chapter>
         *   <chapter title="第二章">
         *     <chapter title="概述"/>
         *   </chapter>
         * </book>
         */
        Document doc = builder.parse("C:\\Users\\86181\\Desktop\\chapter.xml");

        Chapter root = parseChapter(doc.getDocumentElement());
        printStructure(root, 0);
    }

    static class Chapter {
        String title;
        List<Chapter> subChapters = new ArrayList<>();
    }

    private static Chapter parseChapter(Element element) {
        Chapter chapter = new Chapter();
        chapter.title = element.getAttribute("title");

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "chapter".equals(node.getNodeName())) {
                Chapter subChapter = parseChapter((Element) node);
                chapter.subChapters.add(subChapter);
            }
        }
        return chapter;
    }

    private static void printStructure(Chapter chapter, int level) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < level; i++) indent.append("  ");
        System.out.println(indent + "└─ " + chapter.title);
        for (Chapter sub : chapter.subChapters) {
            printStructure(sub, level + 1);
        }
    }

}
