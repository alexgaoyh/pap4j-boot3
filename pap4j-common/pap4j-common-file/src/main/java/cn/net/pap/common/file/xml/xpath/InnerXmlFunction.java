package cn.net.pap.common.file.xml.xpath;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import java.io.StringWriter;
import java.util.List;

/**
 * InnerXmlFunction 是一个自定义的 XPathFunction，用于获取 XML 节点的内部 XML 内容。
 * 如果参数是 Node，则返回该节点的所有子节点的 XML 内容（不包含节点本身）。
 * 如果参数是 NodeList，则返回所有节点的内部 XML 拼接结果。
 * 如果参数为空或类型不匹配，则返回空字符串。
 * 使用场景：在 XPath 表达式中直接获取元素内部的 XML，而不是整个节点。
 */
public class InnerXmlFunction implements XPathFunction {

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

    private void appendInnerXml(Node node, Transformer transformer, StringBuilder sb) throws Exception {

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(children.item(i)), new StreamResult(writer));
            sb.append(writer.toString());
        }
    }
}

