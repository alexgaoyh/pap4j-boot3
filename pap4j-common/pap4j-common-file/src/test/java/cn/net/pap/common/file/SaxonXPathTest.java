package cn.net.pap.common.file;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引入 Saxon-HE ，使用 XPath 3.1 语法处理古籍结构化数据。
 */
public class SaxonXPathTest {

    private static final Processor PROCESSOR;

    private static final DocumentBuilder DOCUMENT_BUILDER;

    private static final XPathCompiler XPATH_COMPILER;

    static {
        // false 表示不使用需要 License 的专业版特性，免费版 HE 已足够
        PROCESSOR = new Processor(false);
        DOCUMENT_BUILDER = PROCESSOR.newDocumentBuilder();
        XPATH_COMPILER = PROCESSOR.newXPathCompiler();

        // 【关键补充】声明 XPath 3.1 map 和 array 的标准命名空间
        XPATH_COMPILER.declareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
        XPATH_COMPILER.declareNamespace("array", "http://www.w3.org/2005/xpath-functions/array");
    }

    private XdmNode document;

    @BeforeEach
    public void setUp() throws Exception {
        // 将 XML 解析提取到 setUp，方便复用
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
        document = DOCUMENT_BUILDER.build(new StreamSource(new StringReader(xml.trim())));
    }

    @Test
    @DisplayName("测试原生序列化和 Simple Map Operator (!)")
    public void testTeaBookXml() throws Exception {
        // 测试 serialize() 函数
        XPathSelector selector = XPATH_COMPILER.compile("serialize(//prop[1])").load();
        selector.setContextItem(document);
        // default 自闭合
        assertEquals("<prop>一<class id=\"001\">章</class>内&gt;容<anchor number=\"1\"/></prop>", selector.evaluateSingle().getStringValue());

        // 测试 ! (Simple Map Operator)
        XPathSelector selector2 = XPATH_COMPILER.compile("//prop ! serialize(.)").load();
        selector2.setContextItem(document);
        XdmValue propsXdmValue = selector2.evaluate();

        assertEquals("<prop>一<class id=\"001\">章</class>内&gt;容<anchor number=\"1\"/></prop>", propsXdmValue.itemAt(0).getStringValue());
        assertEquals("<prop>二<glass id=\"002\">章</glass>内容<anchor number=\"2\"/></prop>", propsXdmValue.itemAt(1).getStringValue());
        assertEquals("<prop>三章内<asdfg id=\"003\">容</asdfg><anchor number=\"3\"/></prop>", propsXdmValue.itemAt(2).getStringValue());
    }

    @Test
    @DisplayName("测试 XPath 3.1 字符串分割与数组封装 (处理 propExt)")
    public void testTokenizeAndArray() throws Exception {
        // fn:tokenize 是极其常用的函数，这里将第一个 propExt (1;2;3;4) 按分号拆分
        // 并将其打包成 XPath 3.1 的 Array 结构
        String xpath = "array { tokenize(//propExt[1], ';') }";

        XPathSelector selector = XPATH_COMPILER.compile(xpath).load();
        selector.setContextItem(document);
        XdmValue result = selector.evaluate();

        // 验证数组结果。注意：XPath 的数组可以直接转为字符串查看内部结构
        assertEquals("[\"1\",\"2\",\"3\",\"4\"]", result.toString());
    }

    @Test
    @DisplayName("测试 XPath 3.1 提取纯文本与正则替换 (处理混排节点)")
    public void testRegexAndStringExtraction() throws Exception {
        // 古籍中经常有 mixed-content (文字夹杂 XML 标签)
        // string() 会剥离所有的内部标签（如 <class>, <anchor>），只保留纯文本
        // fn:replace 可以直接利用正则对提取出的纯文本进行清洗
        String xpath = "replace(string(//prop[1]), '内>容', '【正文内容】')";

        XPathSelector selector = XPATH_COMPILER.compile(xpath).load();
        selector.setContextItem(document);
        String result = selector.evaluateSingle().getStringValue();

        // 原本是：一章内>容，替换后变成：一章【正文内容】
        assertEquals("一章【正文内容】", result);
    }

    @Test
    @DisplayName("测试 XPath 3.1 高阶函数 fn:filter (动态过滤数据)")
    public void testHighOrderFunctionFilter() throws Exception {
        // 提取第二个 propExt (q;w;e;r)
        // 使用 tokenize 拆分后，利用高阶函数 filter 和匿名函数 function($x) 过滤掉 'e'
        String xpath = """
                let $tokens := tokenize(//propExt[2], ';')
                return filter($tokens, function($x) { $x != 'e' })
                """;

        XPathSelector selector = XPATH_COMPILER.compile(xpath).load();
        selector.setContextItem(document);
        XdmValue result = selector.evaluate();

        // 结果应该只剩下 q, w, r
        assertEquals("q", result.itemAt(0).getStringValue());
        assertEquals("w", result.itemAt(1).getStringValue());
        assertEquals("r", result.itemAt(2).getStringValue());
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("测试 FLWOR 表达式与 Map 构建 (实现复杂的多节点联合查询)")
    public void testFlworAndMap() throws Exception {
        // 修正：在 XPath 3.1 中，for 和 let 需要各自的 return 嵌套，或者在 let 中用逗号分隔多个变量
        String xpath = """
                for $i in 1 to count(//prop) return
                    let $innerElement := local-name(//prop[$i]/*[1]),
                        $extValues := //propExt[$i]/text()
                    return map {
                        "index": $i,
                        "firstTag": $innerElement,
                        "ext": $extValues
                    }
                """;

        XPathSelector selector = XPATH_COMPILER.compile(xpath).load();
        selector.setContextItem(document);
        XdmValue result = selector.evaluate();

        // 验证第一组匹配结果（prop[1] 的内部首标签是 class，对应 propExt 是 1;2;3;4）
        assertTrue(result.itemAt(0).toString().contains("\"index\":1"));
        assertTrue(result.itemAt(0).toString().contains("\"firstTag\":\"class\""));
        assertTrue(result.itemAt(0).toString().contains("\"ext\":1;2;3;4"));
    }

}