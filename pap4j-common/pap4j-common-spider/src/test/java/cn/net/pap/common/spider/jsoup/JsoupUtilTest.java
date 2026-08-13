package cn.net.pap.common.spider.jsoup;

import cn.net.pap.common.spider.jsoup.dto.SpiderDTO;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsoupUtilTest {

    private static final Logger log = LoggerFactory.getLogger(JsoupUtilTest.class);

    @Test
    public void parseTest() {
        List<SpiderDTO> spiderDTOList = new ArrayList<>();
        spiderDTOList.add(new SpiderDTO("0", "0", "0"));
        spiderDTOList.add(new SpiderDTO("1", "1", "1"));
        spiderDTOList.add(new SpiderDTO("2", "2", "2"));
        spiderDTOList.add(new SpiderDTO("3", "3", "3"));
        spiderDTOList.add(new SpiderDTO("4", "4", "4"));
        spiderDTOList.add(new SpiderDTO("5", "5", "5"));
        spiderDTOList.add(new SpiderDTO("6", "6", "6"));
        spiderDTOList.add(new SpiderDTO("7", "7", "7"));

        List<String> indexList = new ArrayList<>();
        indexList.add("0-2");
        indexList.add("4-6");

        String parse = JsoupUtil.parse(spiderDTOList, indexList, "<span class=\"outerClassName\">", "</span>");
        log.info("{}", parse);
    }

    @Test
    public void parse2Test() {
        List<SpiderDTO> spiderDTOList = new ArrayList<>();
        spiderDTOList.add(new SpiderDTO("0", "0", "0", 1));
        spiderDTOList.add(new SpiderDTO("1", "1", "1", 1));
        spiderDTOList.add(new SpiderDTO("2", "2", "2", 1));
        spiderDTOList.add(new SpiderDTO("3", "3", "3", 2));
        spiderDTOList.add(new SpiderDTO("4", "4", "4", 2));
        spiderDTOList.add(new SpiderDTO("5", "5", "5", 2));
        spiderDTOList.add(new SpiderDTO("6", "6", "6", 3));
        spiderDTOList.add(new SpiderDTO("7", "7", "7", 3));

        List<String> indexList = new ArrayList<>();
        indexList.add("0-2");
        indexList.add("3-4");
        indexList.add("4-6");

        // 改造后返回按页码分组的HTML
        Map<Integer, String> pageHtmlMap = JsoupUtil.parseByPage(spiderDTOList, indexList, "<span class=\"outerClassName\">", "</span>");

        // 打印每个页码对应的HTML
        for (Map.Entry<Integer, String> entry : pageHtmlMap.entrySet()) {
            log.info("Page {}:", entry.getKey());
            log.info("{}", entry.getValue());
            log.info("-------------------");
        }
    }

    @Test
    public void parse3Test() {
        String html = """
                <p>
                  <span class="chars">原</span>
                  <span class="chars">於</span>
                  <span class="chars">江</span>
                  <span class="chars">南</span>
                  <span class="chars">屈</span>
                  <span class="chars">👨‍👩‍👦‍👦</span>
                  <span data-id="pap.net.cn">
                      <span class="chars">長</span>
                      <span class="chars">沙</span>
                      <span class="chars">羅</span>
                      <span class="chars">縣</span>
                      <span class="chars">西</span>
                      <span class="chars">北</span>
                  </span>
                  <span class="chars">淮</span>
                  <span class="chars">南</span>
                  <span class="chars">王</span>
                  <span class="chars">安</span>
                </p>
                """;
        String s = JsoupUtil.highlightSequential(html, "屈👨‍👩‍👦‍👦長沙", "background:yellow;color:red;");
        log.info("{}", s);
    }

    // ==================== 正常剥离（精确断言） ====================

    @Test
    void unwrapSingleSpan() {
        String html = "<span data-type=\"to_citation\">hello</span>";
        assertEquals("hello", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanKeepsSurroundingText() {
        String html = "<p>before <span data-type=\"to_citation\">cited text</span> after</p>";
        assertEquals("<p>before cited text after</p>", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapMultipleSpans() {
        String html = "<p><span data-type=\"to_citation\">A</span> mid <span data-type=\"to_citation\">B</span></p>";
        assertEquals("<p>A mid B</p>", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithOtherAttributes() {
        String html = "<span class=\"ref\" style=\"color:red\" data-type=\"to_citation\">text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanPreservesInnerMarkup() {
        String html = "<span data-type=\"to_citation\">foo <em>bar</em> <a href=\"x\">baz</a></span>";
        assertEquals("foo <em>bar</em> <a href=\"x\">baz</a>", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithSingleQuotedValue() {
        String html = "<span data-type='to_citation'>text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithUnquotedValue() {
        String html = "<span data-type=to_citation>text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapOnlyMatchingDataType() {
        String html = "<p><span data-type=\"to_citation\">cite</span><span data-type=\"footnote\">note</span></p>";
        assertEquals("<p>cite<span data-type=\"footnote\">note</span></p>",
                JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapDifferentDataTypeParam() {
        String html = "<span data-type=\"footnote\">note</span><span data-type=\"to_citation\">cite</span>";
        assertEquals("note<span data-type=\"to_citation\">cite</span>",
                JsoupUtil.unwrapAttributeSpans(html, "data-type", "footnote"));
    }

    @Test
    void unwrapNestedSameTypeSpans() {
        String html = "<span data-type=\"to_citation\">outer <span data-type=\"to_citation\">inner</span></span>";
        assertEquals("outer inner", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapPreservesWhitespaceAndNewlines() {
        String html = "<div>line1\n<span data-type=\"to_citation\">  keep   spacing  </span>\nline3</div>";
        assertEquals("<div>line1\n  keep   spacing  \nline3</div>",
                JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapPreservesCrlfNewlines() {
        String html = "<div>line1\r\n<span data-type=\"to_citation\">  keep   spacing  </span>\r\nline3</div>";
        assertEquals("<div>line1\r\n  keep   spacing  \r\nline3</div>",
                JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }


    // ==================== 降级 / 无操作（返回原 HTML） ====================

    @Test
    void returnsNullForNullHtml() {
        assertNull(JsoupUtil.unwrapAttributeSpans(null, "data-type", "to_citation"));
    }

    @Test
    void returnsEmptyForEmptyHtml() {
        assertEquals("", JsoupUtil.unwrapAttributeSpans("", "data-type", "to_citation"));
    }

    @Test
    void returnsUnchangedForNullDataType() {
        String html = "<span data-type=\"to_citation\">text</span>";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", null));
    }

    @Test
    void returnsUnchangedForEmptyDataType() {
        String html = "<span data-type=\"to_citation\">text</span>";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", ""));
    }

    @Test
    void returnsUnchangedWhenNoMatch() {
        String html = "<p>hello world</p>";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void returnsUnchangedWhenDataTypeOnlyInText() {
        String html = "<p>the word to_citation appears in text</p>";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void returnsUnchangedWhenValueIsPrefixOnly() {
        String html = "<span data-type=\"to_citation_extra\">text</span>";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void returnsUnchangedWhenValueCaseDiffers() {
        String html = "<span data-type=\"TO_CITATION\">text</span>";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void ignoresNonSpanElementWithSameDataType() {
        String html = "<div data-type=\"to_citation\">text</div>";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void returnsUnchangedForUnclosedSpan() {
        // span 未显式闭合，由外层块级元素隐式关闭 → 开/闭区间数不平衡，降级返回原 HTML
        String html = "<div><span data-type=\"to_citation\">unclosed</div>";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanContainingBlockElement() {
        // Jsoup 允许 span 内嵌套块级元素（此处 <p>），开/闭标签仍为显式，可正常剥离并保留块级内容
        String html = "<span data-type=\"to_citation\"><p>block</p></span>";
        assertEquals("<p>block</p>", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    // ==================== Unicode / 扩展区表情（验证原始剪切不拆分代理对） ====================

    @Test
    void unwrapSpanWithAstralEmoji() {
        // 😀 U+1F600 位于增补平面，由代理对表示
        String html = "<span data-type=\"to_citation\">emoji 😀 end</span>";
        assertEquals("emoji 😀 end", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithZwgFamilyEmoji() {
        // 👨‍👩‍👧‍👦 多码点 ZWJ 序列
        String html = "<span data-type=\"to_citation\">family 👨‍👩‍👧‍👦 here</span>";
        assertEquals("family 👨‍👩‍👧‍👦 here", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithSkinToneEmoji() {
        // 👍🏽 = 👍 U+1F44D + 肤色修饰符 U+1F3FD
        String html = "<span data-type=\"to_citation\">👍🏽</span>";
        assertEquals("👍🏽", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithVariationSelector() {
        // ❤️ = ❤ U+2764 + 变体选择符 U+FE0F
        String html = "<span data-type=\"to_citation\">❤️</span>";
        assertEquals("❤️", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithCjkText() {
        String html = "<span data-type=\"to_citation\">中文引用：世界你好。</span>";
        assertEquals("中文引用：世界你好。", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithCombiningMark() {
        // café 的分解形式：e + 组合重音 U+0301，验证组合字符不被拆分
        String html = "<span data-type=\"to_citation\">café</span>";
        assertEquals("café", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    // ==================== HTML 边界 / 其他异常 ====================

    @Test
    void unwrapSpanWithUppercaseTagName() {
        // HTML 标签名大小写不敏感，<SPAN> 解析后仍命中选择器，并剥离原始大写的开/闭标签
        String html = "<SPAN data-type=\"to_citation\">text</SPAN>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithUppercaseAttributeName() {
        // HTML 属性名大小写不敏感
        String html = "<span DATA-TYPE=\"to_citation\">text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanPreservesHtmlEntityLiterally() {
        // 原始字符串剪切，实体不被解码/二次转义，保持字面 &amp;
        String html = "<span data-type=\"to_citation\">a &amp; b</span>";
        assertEquals("a &amp; b", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapEmptySpan() {
        String html = "<span data-type=\"to_citation\"></span>";
        assertEquals("", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapAdjacentSpans() {
        String html = "<span data-type=\"to_citation\">a</span><span data-type=\"to_citation\">b</span>";
        assertEquals("ab", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void returnsUnchangedForSelfClosingSpan() {
        // 自闭合 span 在 HTML 中按未闭合处理 → 隐式关闭 → 开/闭不平衡 → 降级
        String html = "<span data-type=\"to_citation\"/>text";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithDataTypeContainingSpace() {
        // dataType 值包含空格时，属性等值匹配仍能正确命中并剥离
        String html = "<span data-type=\"to citation\">text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to citation"));
    }

    @Test
    void unwrapSpanWithDataTypeContainingSelectorChar() {
        // dataType 含 ']' 等选择器元字符：等值匹配不拼接选择器，可正常剥离（回归缺口：选择器语义漂移）
        String html = "<span data-type=\"to]citation\">text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to]citation"));
    }

    @Test
    void unwrapSpanWithEntityEncodedAttributeValue() {
        // 属性值经实体编码：attr() 返回解码后的值，等值匹配仍能命中并剥离（回归缺口：实体编码漏剥）
        String html = "<span data-type=\"to_&amp;_citation\">a</span>";
        assertEquals("a", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_&_citation"));
    }

    @Test
    void unwrapSpanByDifferentAttributeName() {
        // 泛化 API：属性名也是入参，可按任意属性过滤（data-note），与 data-type 无关
        String html = "<span data-note=\"ref\">a</span><span data-type=\"to_citation\">b</span>";
        assertEquals("a<span data-type=\"to_citation\">b</span>",
                JsoupUtil.unwrapAttributeSpans(html, "data-note", "ref"));
    }

    @Test
    void unwrapSpanWithUppercaseAttributeNameParam() {
        // attrName 入参大小写不敏感：传 DATA-TYPE 也能命中（内部按 HTML 规范归一化小写）
        String html = "<span data-type=\"to_citation\">text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "DATA-TYPE", "to_citation"));
    }

    // ==================== 畸形 HTML ====================

    @Test
    void returnsUnchangedForUnclosedSpanAtEof() {
        // span 未闭合直到 EOF，被隐式关闭 → 开/闭不平衡 → 降级
        String html = "<div><span data-type=\"to_citation\">unclosed";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void preservesStrayClosingTagLiterally() {
        // 多余的 </span> 不作为元素参与匹配，但原始剪切会逐字保留它
        String html = "<span data-type=\"to_citation\">text</span></span>extra";
        assertEquals("text</span>extra", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanPreservingCommentInside() {
        String html = "<span data-type=\"to_citation\"><!-- note -->text</span>";
        assertEquals("<!-- note -->text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithDuplicateAttribute() {
        // 重复属性按 HTML 规范取首个，值仍为 to_citation
        String html = "<span data-type=\"to_citation\" data-type=\"to_citation\">text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void unwrapSpanWithWhitespaceAroundEquals() {
        String html = "<span data-type = \"to_citation\" >text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void returnsUnchangedWhenSpanInsideScript() {
        // <script> 内容按原文处理，span 不进入 DOM，无法匹配 → 返回原 HTML
        String html = "<script><span data-type=\"to_citation\">x</span></script>";
        assertEquals(html, JsoupUtil.unwrapAttributeSpans(html, "data-type", "to_citation"));
    }

    @Test
    void returnsUnchangedForDataTypeBreakingSelector() {
        // dataType 含 ']' 会拼出非法选择器，异常被 catch(Throwable) 兜底 → 降级返回原 HTML
        String html = "<span data-type=\"to]citation\">text</span>";
        assertEquals("text", JsoupUtil.unwrapAttributeSpans(html, "data-type", "to]citation"));
    }

    // ==================== 白盒：直接验证防御分支 ====================

    @Test
    void removeRangesReturnsNullForOverlappingRanges() {
        List<int[]> ranges = new ArrayList<>();
        ranges.add(new int[]{5, 10});
        ranges.add(new int[]{8, 12}); // 与 [5,10) 重叠
        assertNull(JsoupUtil.removeRanges("0123456789012345", ranges, "to_citation"));
    }

    @Test
    void removeRangesReturnsNullForReversedRanges() {
        List<int[]> ranges = new ArrayList<>();
        ranges.add(new int[]{5, 10});
        ranges.add(new int[]{12, 10}); // 逆序区间
        assertNull(JsoupUtil.removeRanges("0123456789012345", ranges, "to_citation"));
    }

    @Test
    void verifyCleanedReturnsFalseWhenSpanRemains() {
        String cleaned = "<p><span data-type=\"to_citation\">x</span></p>";
        assertFalse(JsoupUtil.verifyCleaned(cleaned, "data-type", "to_citation"));
    }

    @Test
    void verifyCleanedReturnsTrueWhenClean() {
        String cleaned = "<p>no span here</p>";
        assertTrue(JsoupUtil.verifyCleaned(cleaned, "data-type", "to_citation"));
    }

}
