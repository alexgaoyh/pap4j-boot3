package cn.net.pap.common.file.xml.xpath;

import cn.net.pap.common.file.xml.StaxXmlUtil;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import java.io.StringWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * InnerXmlFunction 是一个自定义的 XPathFunction，用于获取 XML 节点的内部 XML 内容。
 * 如果参数是 Node，则返回该节点的所有子节点的 XML 内容（不包含节点本身）。
 * 如果参数是 NodeList，则返回所有节点的内部 XML 拼接结果。
 * 如果参数为空或类型不匹配，则返回空字符串。
 * 使用场景：在 XPath 表达式中直接获取元素内部的 XML，而不是整个节点。
 */
public class InnerXmlFunction implements XPathFunction {

    private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(\\d+);");

    @Override
    public Object evaluate(List<?> args) throws XPathFunctionException {
        if (args == null || args.isEmpty()) {
            return "";
        }

        Object arg = args.get(0);

        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            StringBuilder sb = new StringBuilder();

            if (arg instanceof NodeList) {
                NodeList nodes = (NodeList) arg;
                for (int i = 0; i < nodes.getLength(); i++) {
                    appendInnerXml(nodes.item(i), transformer, sb);
                }
                return sb.toString();
            }

            if (arg instanceof Node) {
                appendInnerXml((Node) arg, transformer, sb);
                return sb.toString();
            }

            return "";

        } catch (Exception e) {
            throw new XPathFunctionException(e);
        }
    }

    /**
     * 如果传入数据是 <anchor number="1"></anchor>， 那么原封不动的输出，不会改为 <anchor number="1" />
     * @param node
     * @param transformer
     * @param sb
     * @throws Exception
     */
    private void appendInnerXml(Node node, Transformer transformer, StringBuilder sb) throws Exception {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            // 如果是空元素节点，手动输出 <tag ...></tag>
            if (child.getNodeType() == Node.ELEMENT_NODE && !child.hasChildNodes()) {
                sb.append("<").append(child.getNodeName());
                if (child.hasAttributes()) {
                    for (int j = 0; j < child.getAttributes().getLength(); j++) {
                        Node attr = child.getAttributes().item(j);
                        sb.append(" ")
                                .append(attr.getNodeName())
                                .append("=\"")
                                .append(StaxXmlUtil.escapeXml(attr.getNodeValue()))
                                .append("\"");
                    }
                }
                sb.append("></").append(child.getNodeName()).append(">");
            } else {
                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(child), new StreamResult(writer));
                sb.append(writer.toString());
            }
        }
    }

    private void appendInnerXml2(Node node, Transformer transformer, StringBuilder sb) throws Exception {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(children.item(i)), new StreamResult(writer));
            sb.append(writer.toString());
        }
    }

    /**
     * 如果传入数据是 <anchor number="1"></anchor>， 那么原封不动的输出，不会改为 <anchor number="1" />
     * 如果传入数据是 <anchor number="1" />， 那么原封不动的输出，不会改为 <anchor number="1"></anchor>
     * @param node
     * @param transformer
     * @param sb
     * @throws Exception
     */
    private void appendInnerXml3(Node node, Transformer transformer, StringBuilder sb) throws Exception {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            // 如果是元素节点且没有子节点
            if (child.getNodeType() == Node.ELEMENT_NODE && !child.hasChildNodes()) {
                sb.append("<").append(child.getNodeName());
                // 添加属性
                if (child.hasAttributes()) {
                    for (int j = 0; j < child.getAttributes().getLength(); j++) {
                        Node attr = child.getAttributes().item(j);
                        sb.append(" ")
                                .append(attr.getNodeName())
                                .append("=\"")
                                .append(StaxXmlUtil.escapeXml(attr.getNodeValue()))
                                .append("\"");
                    }
                }
                // 使用DOM Level 3的isElementContentWhitespace来判断
                // 如果下一个节点是空的文本节点，说明可能是自闭合格式
                boolean isSelfClosed = true;
                Node nextSibling = child.getNextSibling();
                if (nextSibling != null && nextSibling.getNodeType() == Node.TEXT_NODE && !((Text) nextSibling).isElementContentWhitespace()) {
                    // 如果有非空白文本节点，说明原始格式可能是分开标签
                    isSelfClosed = false;
                }
                if (isSelfClosed) {
                    sb.append(" />");
                } else {
                    sb.append("></").append(child.getNodeName()).append(">");
                }
            } else {
                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(child), new StreamResult(writer));
                String transformedXml = writer.toString();
                sb.append(unescapeNumericEntities(transformedXml));
            }
        }
    }

    /**
     * Java 老旧的 XML 引擎面对超出单 char 表示范围的生僻字时，由于你使用了字符流（StringWriter）作为输出目标，它出于防御性设计的本能，选择了最安全的 ASCII 实体转义策略。
     * 需要对生僻字做处理。
     * @param str
     * @return
     */
    private String unescapeNumericEntities(String str) {
        if (str == null || !str.contains("&#")) {
            return str;
        }
        Matcher m = NUMERIC_ENTITY_PATTERN.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int codePoint = Integer.parseInt(m.group(1));
                m.appendReplacement(sb, new String(Character.toChars(codePoint)));
            } catch (Exception e) {
                m.appendReplacement(sb, m.group(0));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }


}

