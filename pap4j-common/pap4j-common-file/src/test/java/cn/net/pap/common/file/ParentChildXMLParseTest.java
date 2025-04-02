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


        Document doc2 = builder.parse("C:\\Users\\86181\\Desktop\\chapter2.xml");
        /**
         * <book>
         *   <chapter title="第一章">
         *     <section title="第一节">
         *       <paragraph title="第一小节"/>
         *     </section>
         *     <chapter title="第二节"/>
         *   </chapter>
         *   <appendix title="附录">
         *     <note title="注意事项"/>
         *   </appendix>
         * </book>
         */
        XmlNode root2 = parseXmlNode2(doc2.getDocumentElement());
        printStructure2(root2, 0);
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

    static class XmlNode {
        String nodeType;  // 节点类型，如chapter, section, paragraph等
        String title;
        List<XmlNode> children = new ArrayList<>();
    }

    private static XmlNode parseXmlNode2(Element element) {
        XmlNode node = new XmlNode();
        node.nodeType = element.getNodeName();
        node.title = element.hasAttribute("title") ? element.getAttribute("title") : "";

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                XmlNode child = parseXmlNode2((Element) childNode);
                node.children.add(child);
            }
        }
        return node;
    }

    private static void printStructure2(XmlNode node, int level) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < level; i++) indent.append("  ");

        // 显示节点类型和标题（如果有）
        String nodeInfo = node.nodeType;
        if (!node.title.isEmpty()) {
            nodeInfo += " [title: " + node.title + "]";
        }

        System.out.println(indent + "└─ " + nodeInfo);
        for (XmlNode child : node.children) {
            printStructure2(child, level + 1);
        }
    }

}
