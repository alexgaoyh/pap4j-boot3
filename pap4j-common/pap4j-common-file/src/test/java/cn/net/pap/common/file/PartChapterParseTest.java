package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;

public class PartChapterParseTest {

    // @Test
    public void parsePartChapter() throws Exception {
        // 安全配置：禁用外部实体解析以防止XXE攻击
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse("C:\\Users\\86181\\Desktop\\partc.xml");

        Element root = document.getDocumentElement();
        List<ChapterInfo> chapters = parseChapters(root, new ArrayList<>());

        for (ChapterInfo info : chapters) {
            System.out.println("Chapter: " + info.getIdentifier());
            System.out.println("Type: " + info.getType());
            System.out.println("Part titles: " + info.getPartTitles());
            System.out.println("Chapter Element: " + info.getChapterElement().getTagName() + " with ID: " + info.getChapterElement().getAttribute("identifier"));

            System.out.println("Part Elements hierarchy:");
            for (PartInfo part : info.getPartHierarchy()) {
                System.out.println("  Part title: " + part.getTitle() + ", Element: " + part.getPartElement().getTagName());
            }
            System.out.println("-------------------");
        }
    }

    private static List<ChapterInfo> parseChapters(Node node, List<PartInfo> currentPartHierarchy) {
        List<ChapterInfo> chapters = new ArrayList<>();

        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if ("part".equals(child.getNodeName())) {
                    List<PartInfo> newPartHierarchy = new ArrayList<>(currentPartHierarchy);

                    PartInfo currentPart = new PartInfo();
                    currentPart.setPartElement((Element) child);

                    NodeList partChildren = child.getChildNodes();
                    for (int j = 0; j < partChildren.getLength(); j++) {
                        Node partChild = partChildren.item(j);
                        if (partChild.getNodeType() == Node.ELEMENT_NODE && "title".equals(partChild.getNodeName())) {
                            currentPart.setTitle(partChild.getTextContent());
                            break;
                        }
                    }

                    newPartHierarchy.add(currentPart);

                    chapters.addAll(parseChapters(child, newPartHierarchy));
                } else if ("chapter".equals(child.getNodeName())) {
                    ChapterInfo info = new ChapterInfo();
                    info.setChapterElement((Element) child);
                    info.setPartHierarchy(new ArrayList<>(currentPartHierarchy));
                    chapters.add(info);
                }
            }
        }

        return chapters;
    }

    static class ChapterInfo {
        private Element chapterElement;
        private List<PartInfo> partHierarchy;

        public String getIdentifier() {
            return chapterElement.getAttribute("identifier");
        }

        public String getType() {
            return chapterElement.getAttribute("type");
        }

        public List<String> getPartTitles() {
            List<String> titles = new ArrayList<>();
            for (PartInfo part : partHierarchy) {
                titles.add(part.getTitle());
            }
            return titles;
        }

        public Element getChapterElement() {
            return chapterElement;
        }

        public void setChapterElement(Element chapterElement) {
            this.chapterElement = chapterElement;
        }

        public List<PartInfo> getPartHierarchy() {
            return partHierarchy;
        }

        public void setPartHierarchy(List<PartInfo> partHierarchy) {
            this.partHierarchy = partHierarchy;
        }
    }

    static class PartInfo {
        private String title;
        private Element partElement;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Element getPartElement() {
            return partElement;
        }

        public void setPartElement(Element partElement) {
            this.partElement = partElement;
        }
    }

}
