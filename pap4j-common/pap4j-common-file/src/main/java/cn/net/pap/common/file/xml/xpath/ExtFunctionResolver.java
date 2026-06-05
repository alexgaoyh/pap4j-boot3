package cn.net.pap.common.file.xml.xpath;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionResolver;

/**
 * 扩展 XPath 标准函数集，增加自定义功能。
 * <p>
 * 该解析器通过注册自定义的 {@link XPathFunction} 实现，扩展了标准 XPath 的能力。
 * 目前支持：
 * <ul>
 *   <li><b>inner-xml</b>: 获取节点的内部 XML 文本。</li>
 *   <li><b>position-in-parent</b>: 获取节点在父节点中的索引位置。</li>
 *   <li><b>xref</b>: 通用的跨节点引用查找函数（支持 5 参数）。</li>
 * </ul>
 */
public class ExtFunctionResolver implements XPathFunctionResolver {

    /**
     * 自定义函数所在的命名空间
     */
    public static final String EXT_NS = "http://pap.net.cn/ext";

    @Override
    public XPathFunction resolveFunction(QName name, int arity) {
        if (EXT_NS.equals(name.getNamespaceURI()) && "inner-xml".equals(name.getLocalPart()) && arity == 1) {
            // ExtFunctionResolver 是一个自定义的 XPathFunctionResolver，用于解析自定义扩展函数 "inner-xml"。
            // 该解析器会在 XPath 表达式中遇到命名空间为 {@value #EXT_NS} 且函数名为"inner-xml" 的函数时返回对应的 {@link InnerXmlFunction} 实例。
            // 使用场景：在处理 XML 文档时，需要获取某个节点的内部 XML 内容而不是整个节点。
            return new InnerXmlFunction();
        }
        if (EXT_NS.equals(name.getNamespaceURI()) && "position-in-parent".equals(name.getLocalPart()) && arity == 1) {
            // position-in-parent 函数：获取节点在其父节点中的位置
            return new PositionInParentFunction();
        }
        if (EXT_NS.equals(name.getNamespaceURI()) && "xref".equals(name.getLocalPart()) && arity == 5) {
            // xref 函数：从其他节点获取非主键属性
            return new CrossReferenceFunction();
        }

        return null;
    }

}

