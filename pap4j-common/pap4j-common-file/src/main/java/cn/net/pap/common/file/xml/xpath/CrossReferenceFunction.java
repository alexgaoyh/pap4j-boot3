package cn.net.pap.common.file.xml.xpath;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import java.util.List;

/**
 * CrossReferenceFunction 是一个高度通用的 XPath 扩展函数。
 * 它允许根据一个“源值”去“目标节点集”中查找匹配项，并返回该项的指定属性。
 * <p>
 * 使用方式：<code>ext:xref(上下文根, 源值, 目标节点名, 目标匹配属性名, 目标结果属性名)</code>
 * </p>
 * <p>
 * 示例：在解析学生信息时获取年级标题
 * <code>ext:xref(/, @gradeId, 'grade', 'id', 'gradeTitle')</code>
 * </p>
 * <b>参数说明：</b>
 * <ul>
 *   <li><b>上下文根</b>: 通常传入 <code>/</code>，用于在整个文档范围内执行查找。</li>
 *   <li><b>源值</b>: 当前上下文中用于查找的键值（如 ID）。</li>
 *   <li><b>目标节点名</b>: 要在其内部搜索的目标元素标签名。</li>
 *   <li><b>目标匹配属性名</b>: 目标元素中用于与源值进行对比的属性名。</li>
 *   <li><b>目标结果属性名</b>: 匹配成功后，期望返回的目标元素属性名。</li>
 * </ul>
 */
public class CrossReferenceFunction implements XPathFunction {

    @Override
    public Object evaluate(List<?> args) throws XPathFunctionException {
        if (args == null || args.size() < 5) {
            throw new XPathFunctionException("Function 'xref' requires 5 arguments: (root, sourceValue, targetNodeName, matchAttrName, resultAttrName).");
        }

        Object root = args.get(0);
        org.w3c.dom.Document doc = null;
        if (root instanceof org.w3c.dom.NodeList) {
            org.w3c.dom.NodeList nl = (org.w3c.dom.NodeList) root;
            if (nl.getLength() > 0) {
                // 如果传的是 /，item(0) 通常就是 Document 本身
                doc = (org.w3c.dom.Document) nl.item(0);
            }
        }

        // 1. 源值 (如: '202609')
        String sourceValue = extractString(args.get(1));
        // 2. 目标节点名 (如: 'grade')
        String targetNodeName = extractString(args.get(2));
        // 3. 目标匹配属性名 (如: 'gradeName')
        String matchAttrName = extractString(args.get(3));
        // 4. 目标结果属性名 (如: 'gradeTitle')
        String resultAttrName = extractString(args.get(4));

        if (sourceValue.isEmpty() || targetNodeName.isEmpty() || matchAttrName.isEmpty() || resultAttrName.isEmpty()) {
            return "";
        }

        // 执行跨节点查找
        NodeList targetNodes = doc.getElementsByTagName(targetNodeName);
        for (int i = 0; i < targetNodes.getLength(); i++) {
            Element targetElem = (Element) targetNodes.item(i);
            if (sourceValue.equals(targetElem.getAttribute(matchAttrName))) {
                return targetElem.getAttribute(resultAttrName);
            }
        }

        return "";
    }

    private String extractString(Object arg) {
        if (arg instanceof NodeList) {
            NodeList nl = (NodeList) arg;
            return nl.getLength() > 0 ? nl.item(0).getTextContent() : "";
        } else if (arg instanceof Node) {
            return ((Node) arg).getTextContent();
        } else if (arg != null) {
            return arg.toString();
        }
        return "";
    }
}
