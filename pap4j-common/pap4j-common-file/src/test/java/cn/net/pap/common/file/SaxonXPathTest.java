package cn.net.pap.common.file;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 引入 Saxon-HE ，使用 XPath 3.1 语法。
 */
public class SaxonXPathTest {

    private static final Processor PROCESSOR;

    private static final DocumentBuilder DOCUMENT_BUILDER;

    private static final XPathCompiler XPATH_COMPILER;

    static {
        PROCESSOR = new Processor(false);
        DOCUMENT_BUILDER = PROCESSOR.newDocumentBuilder();
        XPATH_COMPILER = PROCESSOR.newXPathCompiler();
    }

    @Test
    public void testTeaBookXml() throws Exception {
        String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <student>
                      <props>
                        <prop>一<class id="001">章</class>内&gt;容<anchor number="1"></anchor></prop>
                        <prop>二<glass id="002">章</glass>内容<anchor number="2"></anchor></prop>
                        <prop>三章内<asdfg id="003">容</asdfg><anchor number="3"></anchor></prop>
                      </props>
                      <propExts>
                        <propExt>1;2;3;4</propExt>
                        <propExt>q;w;e;r</propExt>
                        <propExt>a;s;d;f</propExt>
                      </propExts>
                    </student>
                """;
        XdmNode document = DOCUMENT_BUILDER.build(new StreamSource(new StringReader(xml.trim())));
        XPathSelector selector = XPATH_COMPILER.compile("serialize(//prop[1])").load();
        selector.setContextItem(document);
        // default 自闭合
        assertEquals("<prop>一<class id=\"001\">章</class>内&gt;容<anchor number=\"1\"/></prop>", selector.evaluateSingle().getStringValue());

        // ! 是 XPath 3.1 新增的 map operator（映射操作符），相当于循环。
        XPathSelector selector2 = XPATH_COMPILER.compile("//prop ! serialize(.)").load();
        selector2.setContextItem(document);
        XdmValue propsXdmValue = selector2.evaluate();
        assertEquals("<prop>一<class id=\"001\">章</class>内&gt;容<anchor number=\"1\"/></prop>", propsXdmValue.itemAt(0).getStringValue());
        assertEquals("<prop>二<glass id=\"002\">章</glass>内容<anchor number=\"2\"/></prop>", propsXdmValue.itemAt(1).getStringValue());
        assertEquals("<prop>三章内<asdfg id=\"003\">容</asdfg><anchor number=\"3\"/></prop>", propsXdmValue.itemAt(2).getStringValue());

    }

}
