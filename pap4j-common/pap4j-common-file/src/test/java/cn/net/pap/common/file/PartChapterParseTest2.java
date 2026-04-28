package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 层级处理测试类
 * 演示如何使用 DOM + XPath 扁平化解析包含复杂嵌套、无序排列的 XML 结构，
 * 并为其分配顺序号、还原完整的父子/祖孙层级链路。
 */
public class PartChapterParseTest2 {
    private static final Logger log = LoggerFactory.getLogger(PartChapterParseTest2.class);

    @Test
    public void parsePartChapter() throws Exception {
        // 1. 涵盖了标准结构、多子节点、父子 Part 嵌套、孤儿 Chapter、无用标签干扰、空 Part 等各类极端场景
        String xmlData = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <part identifier="P-Standard">
                        <title>第一篇：标准结构</title>
                        <chapter identifier="C-Std-1" type="NORMAL"/>
                        <chapter identifier="C-Std-2" type="NORMAL"/>
                    </part>
                    <part identifier="P-Parent">
                        <title>第二篇：父 Part</title>
                        <part identifier="P-Child">
                            <title>第二篇的子 Part</title>
                            <chapter identifier="C-Deep-1" type="NESTED"/>
                        </part>
                    </part>
                    <chapter identifier="C-Orphan" type="ROOT_LEVEL"/>
                    <wrapper_div>
                        <part identifier="P-NoTitle">
                            <noise_tag>
                                <chapter identifier="C-NoType"/>
                            </noise_tag>
                        </part>
                    </wrapper_div>
                    <part identifier="P-Empty">
                        <title>这是一个空的 Part，里面没有 Chapter</title>
                    </part>
                    <part identifier="P-Grandparent">
                        <title>第三篇：终极套娃测试</title>
                        <chapter identifier="C-Level1" type="BIG_CHAP">
                            <part identifier="P-Level2-Father">
                                <title>藏在Level1里的Part</title>
                                <chapter identifier="C-Level3-Child" type="MID_CHAP">
                                    <chapter identifier="C-Level4-Grandchild" type="SMALL_CHAP">
                                        <part identifier="P-Level5-Abyss">
                                            <title>深渊 Part</title>
                                            <chapter identifier="C-Level6-Target" type="ULTRA_DEEP"/>
                                        </part>
                                    </chapter>
                                </chapter>
                            </part>
                        </chapter>
                    </part>
                </root>
                """;

        // 2. 安全配置：防范 XXE (XML External Entity) 注入攻击
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // 禁用 DTD 声明
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false); // 禁用外部普通实体
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); // 禁用外部参数实体
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        // 3. 将 XML 字符串解析为 DOM Document 对象
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8)));

        // 4. 初始化 XPath 解析器
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();

        // 5. 预编译 XPath 表达式 (预编译可提升循环内部的执行性能)
        // 获取所有目标节点。"|" 运算符天然保证返回结果严格遵循节点在 XML 文档中出现的自上而下的顺序 (Document Order)
        XPathExpression allNodesExpr = xpath.compile("//part | //chapter");
        // 获取当前节点向上的所有目标祖先。"|" 依然保证返回的祖先列表按从外层(根)到内层(当前节点)的顺序排列
        XPathExpression ancestorsExpr = xpath.compile("ancestor::part | ancestor::chapter");
        // 获取当前节点下的直接子元素 title。"./" 防止误抓取到子孙级别(更深层)的同名标签
        XPathExpression titleExpr = xpath.compile("./title");

        // 6. 核心处理流程
        // 将 Part 和 Chapter 统称为 StructureNode，统一处理，放入 catalog 目录集合
        List<StructureNode> catalog = new ArrayList<>();
        NodeList targetNodes = (NodeList) allNodesExpr.evaluate(document, XPathConstants.NODESET);
        BigDecimal currentSequence = new BigDecimal("1.0"); // 初始序号

        // 在一次遍历中，同时完成【分配序号】和【解析层级】
        for (int i = 0; i < targetNodes.getLength(); i++) {
            Element element = (Element) targetNodes.item(i);

            // --- 6.1 分配 _seq 序号并回写到 DOM ---
            int decimalPlaces = countDecimalPlaces(currentSequence);
            String formattedSequence = currentSequence.setScale(decimalPlaces, RoundingMode.HALF_UP).toString();
            element.setAttribute("_seq", formattedSequence); // 将序号注入到 DOM 节点，供后续祖先回溯时读取
            BigDecimal currentSeqValue = new BigDecimal(formattedSequence);

            // 计算下一个序号的自增值
            BigDecimal increment = BigDecimal.valueOf(Math.pow(10, -decimalPlaces));
            currentSequence = currentSequence.add(increment);

            // --- 6.2 提取当前节点的基础信息 ---
            StructureNode currentNode = new StructureNode();
            currentNode.setTagName(element.getTagName());
            currentNode.setIdentifier(element.getAttribute("identifier"));
            currentNode.setSeq(currentSeqValue);

            // 根据标签类型，差异化提取 title 或 type（加入判空容错机制）
            if ("part".equals(element.getTagName())) {
                Node titleNode = (Node) titleExpr.evaluate(element, XPathConstants.NODE);
                currentNode.setTitle(titleNode != null ? titleNode.getTextContent() : "【未定义标题】");
            } else {
                String typeStr = element.getAttribute("type");
                currentNode.setType(typeStr.isEmpty() ? "【未定义Type】" : typeStr);
            }

            // --- 6.3 提取当前节点的完整祖先溯源 (Ancestors) ---
            List<StructureNode> ancestorsList = new ArrayList<>();
            NodeList ancestors = (NodeList) ancestorsExpr.evaluate(element, XPathConstants.NODESET);

            for (int j = 0; j < ancestors.getLength(); j++) {
                Element ancElement = (Element) ancestors.item(j);
                StructureNode ancNode = new StructureNode();
                ancNode.setTagName(ancElement.getTagName());
                ancNode.setIdentifier(ancElement.getAttribute("identifier"));

                // 【关键逻辑】由于 allNodesExpr 保证了按文档顺序遍历，外层祖先一定比当前内层节点先被处理过，
                // 因此此时 ancElement 的 DOM 元素上必定已经成功附带了前置步骤设置的 "_seq" 属性。
                ancNode.setSeq(new BigDecimal(ancElement.getAttribute("_seq")));

                if ("part".equals(ancElement.getTagName())) {
                    Node titleNode = (Node) titleExpr.evaluate(ancElement, XPathConstants.NODE);
                    ancNode.setTitle(titleNode != null ? titleNode.getTextContent() : "【未定义标题】");
                } else {
                    String typeStr = ancElement.getAttribute("type");
                    ancNode.setType(typeStr.isEmpty() ? "【未定义Type】" : typeStr);
                }
                ancestorsList.add(ancNode);
            }
            currentNode.setHierarchyChain(ancestorsList);

            // 将当前节点（包含其所有的祖先血脉记录）加入结果集
            catalog.add(currentNode);
        }

        // 7. 打印完整目录结果，验证解析准确性
        log.info("================ 完整文档目录解析结果 ================\n");
        for (StructureNode node : catalog) {
            String nodeDisplay = "part".equals(node.getTagName()) ? "[Part] Title: " + node.getTitle() : "[Chapter] Type: " + node.getType();

            log.info("{}", "【当前节点】" + nodeDisplay + " | ID: " + node.getIdentifier() + " | Seq: " + node.getSeq());
            log.info("  完整层级溯源:");

            if (node.getHierarchyChain().isEmpty()) {
                log.info("    (顶层/孤儿节点，无父级)");
            } else {
                for (int j = 0; j < node.getHierarchyChain().size(); j++) {
                    StructureNode anc = node.getHierarchyChain().get(j);
                    // 模拟层级缩进，越内层的祖先缩进越深
                    String indent = "  ".repeat(j + 2);
                    String ancDisplay = "part".equals(anc.getTagName()) ? "[Part] ID: " + anc.getIdentifier() + " | Title: " + anc.getTitle() : "[Chapter] ID: " + anc.getIdentifier() + " | Type: " + anc.getType();
                    log.info("{}", indent + "-> " + ancDisplay + " | Seq: " + anc.getSeq());
                }
            }
            log.info("---------------------------------------------------");
        }
    }

    /**
     * 工具方法：计算 BigDecimal 数字的小数位数
     * 用于决定后续自增时的步长精度（例如 1位小数步长为0.1，0位小数步长为1）
     * * @param number 待计算的数值
     *
     * @return 小数位数，若无小数则返回 0
     */
    private static int countDecimalPlaces(BigDecimal number) {
        String str = number.toString();
        if (str.contains(".")) {
            return str.length() - str.indexOf('.') - 1;
        }
        return 0;
    }

    // ================= 统一的数据结构 =================

    /**
     * 文档结构节点 DTO 类。
     * 因为 Part 和 Chapter 在业务上都可以视为文档目录结构的一个层级节点，
     * 故将其抽象为统一的 StructureNode 进行存储，方便后续输出和构建树形结构。
     */
    static class StructureNode {
        private String tagName;     // 节点标签名：区分是 "part" 还是 "chapter"
        private String identifier;  // 节点唯一标识
        private String title;       // 节点标题 (仅当 tagName 为 "part" 时有实际意义)
        private String type;        // 节点类型 (仅当 tagName 为 "chapter" 时有实际意义)
        private BigDecimal seq;     // 动态计算出的文档顺序号

        // 记录从根部到该节点的所有直系祖先链路，索引0为最外层，索引末尾为直系父级
        private List<StructureNode> hierarchyChain;

        public String getTagName() {
            return tagName;
        }

        public void setTagName(String tagName) {
            this.tagName = tagName;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public BigDecimal getSeq() {
            return seq;
        }

        public void setSeq(BigDecimal seq) {
            this.seq = seq;
        }

        public List<StructureNode> getHierarchyChain() {
            return hierarchyChain;
        }

        public void setHierarchyChain(List<StructureNode> hierarchyChain) {
            this.hierarchyChain = hierarchyChain;
        }
    }

}