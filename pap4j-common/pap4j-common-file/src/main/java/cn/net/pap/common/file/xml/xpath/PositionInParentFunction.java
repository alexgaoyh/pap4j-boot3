package cn.net.pap.common.file.xml.xpath;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import java.util.List;

/**
 * PositionInParentFunction 是一个自定义的 XPathFunction，用于获取 XML 节点在其父节点中的位置索引。
 * 如果参数是 Node，则返回该节点在其父节点中同类型元素的位置（从1开始计数）。
 * 如果参数是 NodeList，则返回所有节点的位置信息拼接结果，格式："位置1,位置2,位置3"。
 * 如果参数为空或类型不匹配，则返回空字符串。
 * 使用场景：在 XPath 表达式中直接获取元素在其父节点中的下标位置。
 */
public class PositionInParentFunction implements XPathFunction {

    @Override
    public Object evaluate(List<?> args) throws XPathFunctionException {
        if (args == null || args.isEmpty()) {
            return "";
        }
        Object arg = args.get(0);
        try {
            if (arg instanceof NodeList) {
                NodeList nodes = (NodeList) arg;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < nodes.getLength(); i++) {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    sb.append(getPositionInParent(nodes.item(i)));
                }
                return sb.toString();
            }
            if (arg instanceof Node) {
                return String.valueOf(getPositionInParent((Node) arg));
            }
            return "";
        } catch (Exception e) {
            throw new XPathFunctionException(e);
        }
    }

    /**
     * 获取节点在其父节点中的位置索引（从1开始）
     * 只计算同类型的元素节点，忽略文本节点、注释节点等
     *
     * @param node 要计算位置的节点
     * @return 位置索引，如果节点没有父节点则返回0
     */
    private int getPositionInParent(Node node) {
        if (node == null) {
            return 0;
        }
        Node parent = node.getParentNode();
        if (parent == null) {
            return 0;
        }
        int position = 0;
        NodeList children = parent.getChildNodes();
        String targetNodeName = node.getNodeName();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            // 只计算同类型的元素节点
            if (child.getNodeType() == Node.ELEMENT_NODE && targetNodeName.equals(child.getNodeName())) {
                position++;
                if (child == node) {
                    return position;
                }
            }
        }
        return 0;
    }

}