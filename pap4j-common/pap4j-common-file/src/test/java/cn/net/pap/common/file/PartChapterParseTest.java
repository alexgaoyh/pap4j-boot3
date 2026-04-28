package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class PartChapterParseTest {

    @Test
    public void parsePartChapter() throws Exception {
        // 安全配置：禁用外部实体解析以防止XXE攻击
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(TestResourceUtil.getFile("partc.xml").getAbsolutePath().toString());

        Element root = document.getDocumentElement();

        // 递归遍历所有元素，只为指定节点添加顺序号
        addSequenceNumbersToSpecificNodes(root, new String[]{"part", "chapter"}, "_seq", new BigDecimal("1.0"));

        List<ChapterInfo> chapters = parseChapters(root, new ArrayList<>());

        for (ChapterInfo info : chapters) {
            System.out.println("Chapter: " + info.getIdentifier() + ", Type: " + info.getType() + ", Seq: " + info.getSeq() + ", Chapter Element: " + info.getChapterElement().getTagName() + " with ID: " + info.getChapterElement().getAttribute("identifier"));
            System.out.println("Part titles: " + info.getPartTitles());

            System.out.println("Part Elements hierarchy:");
            for (PartInfo part : info.getPartHierarchy()) {
                System.out.println("  Part title: " + part.getTitle() + ", Element: " + part.getPartElement().getTagName() + ", Seq: " + part.getSeq());
            }
            System.out.println("-------------------");
        }
    }

    private static BigDecimal addSequenceNumbersToSpecificNodes(Element element, String[] targetNodeNames,
                                                                String attributeName,BigDecimal currentSequence) {
        for (String nodeName : targetNodeNames) {
            if (element.getNodeName().equals(nodeName)) {
                int decimalPlaces = countDecimalPlaces(currentSequence);
                String formattedSequence = currentSequence.setScale(decimalPlaces, RoundingMode.HALF_UP).toString();
                element.setAttribute(attributeName, formattedSequence);
                BigDecimal increment = BigDecimal.valueOf(Math.pow(10, -decimalPlaces));
                currentSequence = currentSequence.add(increment);
                break;
            }
        }
        // 这里是所有的节点，没有层的概念.
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                currentSequence = addSequenceNumbersToSpecificNodes((Element) node, targetNodeNames, attributeName, currentSequence);
            }
        }
        return currentSequence;
    }

    private static int countDecimalPlaces(BigDecimal number) {
        String str = number.toString();
        if (str.contains(".")) {
            return str.length() - str.indexOf('.') - 1;
        }
        return 0; // 没有小数
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
                    currentPart.setSeq(new BigDecimal(((Element) child).getAttribute("_seq")));

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
                    info.setSeq(new BigDecimal(((Element) child).getAttribute("_seq")));
                    chapters.add(info);
                }
            }
        }

        return chapters;
    }

    static class ChapterInfo {
        private Element chapterElement;
        private List<PartInfo> partHierarchy;
        private BigDecimal seq;

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

        public BigDecimal getSeq() {
            return seq;
        }

        public void setSeq(BigDecimal seq) {
            this.seq = seq;
        }
    }

    static class PartInfo {
        private String title;
        private Element partElement;
        private BigDecimal seq;

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
        public BigDecimal getSeq() {
            return seq;
        }
        public void setSeq(BigDecimal seq) {
            this.seq = seq;
        }
    }

}
