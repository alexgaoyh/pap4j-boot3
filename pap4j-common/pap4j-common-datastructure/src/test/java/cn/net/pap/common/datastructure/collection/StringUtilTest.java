package cn.net.pap.common.datastructure.collection;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringUtilTest {

    private static final Logger log = LoggerFactory.getLogger(StringUtilTest.class);

    @Test
    public void indexOf2Test() {
        String str1 = "扫地僧\uD85D\uDC64一个扫地僧";
        String str2 = "一个";

        int indexOf2 = StringUtil.indexOf2(str1, str2);
        int indexOf = str1.indexOf(str2);
        assertEquals(indexOf2, 4);
        assertEquals(indexOf, 5);

    }

    @Test
    public void indexOf3Test() {
        String str = "扫地僧\uD85D\uDC64一个扫地僧";
        StringUtil.print(str);
        log.info("");
        str.chars().mapToObj(c -> (char) c).forEach(item -> log.info("{}", item));
        log.info("");

    }

    @Test
    public void groupSpecialStringsTest() {
        String input = "这是示例文本，包含一二三和五六七等特殊字符串。扫地僧\uD85D\uDC64一个扫地僧";
        List<String> specialStrings = new ArrayList<>();
        specialStrings.add("一二三");
        specialStrings.add("五六七");
        specialStrings.add("僧\uD85D\uDC64一个");

        List<String> matchedStrings = StringUtil.groupSpecialStrings(input, specialStrings);

        for (String match : matchedStrings) {
            log.info(match);
        }
    }

    @Test
    public void replaceFirstTest() {
        String s = StringUtil.replaceFirst("一二三(四五六)七八九十", ")", "");
        log.info(s);

        s = StringUtil.replaceFirst("一二三(四五六)七八九十", ".*", "");
        log.info(s);

        s = "一二三(四五六)七八九十".replaceFirst(".*", "");
        log.info(s);
    }

    @Test
    public void splitTest() {
        String input = "苹果、香蕉#西瓜 葡萄,橙子";
        String delimiters = "、# ,";

        log.info("原始字符串: {}", input);
        log.info("分隔符: {}", delimiters);

        String[] result = StringUtil.split(input, delimiters);
        log.info("拆分结果: {}", Arrays.toString(result));

        List<String> filteredResult = StringUtil.splitAndFilter(input, delimiters);
        log.info("拆分并过滤空字符串结果: {}", filteredResult);
    }

    @Test
    public void emptyTest() {
        String input = "    ";
        assertTrue(input.trim().equals(""));
    }

    @Test
    public void stringBuilderTest() {
        for (char b1 = '\u4E00'; b1 <= '\u9FA5'; b1++) {
            // 对比初始化不同的 capacity
            // StringBuilder builder = new StringBuilder();
            StringBuilder builder = new StringBuilder(1024 * 1024);
            for (char b2 = '\u4E00'; b2 <= '\u9FA5'; b2++) {
                if (!String.valueOf(b1).equals(String.valueOf(b2))) {
                    builder.append(String.valueOf(b1));
                    builder.append(String.valueOf(b2));
                }
            }
        }
    }

    record Segment(String fileName, int pageNum, String content) {

    }

    ;


    @Test
    public void anchorRegexSplitTest() {
        String content = "<p data-sign=\"1\">一</p><p>\uD85D\uDC64</p><p>\uD83D\uDC68\u200D\uD83D\uDC68\u200D\uD83D\uDC66\u200D\uD83D\uDC66</p>...<anchor fileName=\"0030\" pageNum=\"30\" /><p data-sign=\"a\">请</p><p>安</p><p>找</p>...<anchor fileName=\"0031\" pageNum=\"31\" />";
        Pattern anchorPattern = Pattern.compile(
                "<anchor\\s+" +
                "(?=[^>]*fileName=\"(.*?)\")" +
                "(?=[^>]*pageNum=\"(\\d+)\")" +
                "[^>]*/>"
        );
        java.util.regex.Matcher matcher = anchorPattern.matcher(content);

        List<Segment> segments = new ArrayList<>();
        int lastIndex = 0;

        while (matcher.find()) {
            String fileName = matcher.group(1);
            int pageNum = Integer.parseInt(matcher.group(2));
            String segmentContent = content.substring(lastIndex, matcher.start()).trim();
            segments.add(new Segment(fileName, pageNum, segmentContent));
            lastIndex = matcher.end();
        }

        for (Segment seg : segments) {
            log.info("{}", seg);
        }
    }

    /**
     * 黑色菱形问号
     */
    @Test
    public void chineseErrorTest() {
        String replacementChar = "\uFFFD";
        log.info("直接打印: {}", replacementChar);
    }

    /**
     * unicode 判断
     */
    @Test
    public void unicodeDefinitionTest() {
        // \u08B6 是一个在更高版本 Unicode 中才被定义的字符
        char testChar = '\u08B6';
        boolean isDefined = Character.isDefined(testChar);
        log.info("Java Version: {}", System.getProperty("java.version"));
        log.info("字符 \\u08B6 是否属于有效 Unicode 字符: {}", isDefined);
        // 在 1.8.0_281， 这段代码返回的是 isDefined == false
    }

    @Test
    void testReplaceVsReplaceAll() {
        // ========== 1. 点号 . ==========
        String text1 = "a.b.c.d";
        assertEquals("a-b-c-d", text1.replace(".", "-"));
        assertEquals("-------", text1.replaceAll(".", "-"));  // 正则 . 匹配任意字符
        assertEquals("a-b-c-d", text1.replaceAll(Pattern.quote("."), "-")); // Pattern.quote 形式

        // ========== 2. 美元符号 $ ==========
        String text2 = "price = $100";
        assertEquals("price = USD100", text2.replace("$", "USD"));
        assertEquals("price = USD100", text2.replaceAll("\\$", "USD"));
        assertEquals("price = $100USD", text2.replaceAll("$", "USD")); // 正则，$ 是行尾锚点，匹配字符串的末尾
        assertEquals("price = USD100", text2.replaceAll(Pattern.quote("$"), "USD")); // Pattern.quote 形式

        // ========== 3. 反斜杠 \ ==========
        String text3 = "C:\\Windows\\System32";
        assertEquals("C:/Windows/System32", text3.replace("\\", "/"));
        assertEquals("C:/Windows/System32", text3.replaceAll("\\\\", "/"));
        assertEquals("C:/Windows/System32", text3.replaceAll(Pattern.quote("\\"), "/")); // Pattern.quote 形式

        // ========== 4. 方括号 [] ==========
        String text4 = "Hello [world] (java)";
        assertEquals("Hello {world] (java)", text4.replace("[", "{"));
        assertEquals("Hello {world] (java)", text4.replaceAll("\\[", "{"));
        assertEquals("Hello {world] (java)", text4.replaceAll(Pattern.quote("["), "{")); // Pattern.quote 形式

        // ========== 5. 捕获组（replaceAll 独有能力）==========
        String text5 = "2024-01-15";
        // replaceAll 可以引用捕获组重新格式化
        assertEquals("01/15/2024", text5.replaceAll("(\\d{4})-(\\d{2})-(\\d{2})", "$2/$3/$1"));
        // replace 不支持捕获组，当做普通字符串处理
        assertEquals("2024-01-15", text5.replace("(\\d{4})-(\\d{2})-(\\d{2})", "$2/$3/$1"));
        // Pattern.quote 不适用于此场景，因为这里需要使用捕获组功能，不能转义正则

        // ========== 6. replacement 中的 $ 符号 ==========
        String text6 = "value = 100";
        // 想替换成 "value = $100"，replacement 中的 $ 需要转义
        assertEquals("value = $100", text6.replaceAll("(value = )\\d+", "$1\\$100"));
        // Pattern.quote 不适用于 replacement 参数，只用于正则表达式字符串

        // ========== 7. 多个正则特殊字符 ==========
        String text7 = "1.2+3*4?5^6$7|8(9)";
        // replace: 逐个字面量替换
        assertEquals("1_2_3_4?5^6$7|8(9)", text7.replace(".", "_").replace("+", "_").replace("*", "_"));
        // replaceAll: 使用正则字符类批量替换
        assertEquals("1_2_3_4?5^6$7|8(9)", text7.replaceAll("[.+*]", "_"));
        // Pattern.quote 不能用于字符类，但可以单个使用
        assertEquals("1_2_3_4?5^6$7|8(9)", text7.replaceAll(Pattern.quote("."), "_").replaceAll(Pattern.quote("+"), "_").replaceAll(Pattern.quote("*"), "_"));

        // ========== 8. 使用 Pattern.quote 自动转义 ==========
        String text8 = "A+B-C?D";
        assertEquals("A-B-C?D", text8.replace("+", "-"));
        assertEquals("A-B-C?D", text8.replaceAll("\\+", "-"));
        // 推荐：使用 Pattern.quote 自动处理转义
        assertEquals("A-B-C?D", text8.replaceAll(Pattern.quote("+"), "-"));

        // ========== 9. 问号 ? ==========
        String text9 = "Is it true? Really?";
        assertEquals("Is it true! Really!", text9.replace("?", "!"));
        assertEquals("Is it true! Really!", text9.replaceAll("\\?", "!"));
        assertEquals("Is it true! Really!", text9.replaceAll(Pattern.quote("?"), "!")); // Pattern.quote 形式

        // ========== 10. 星号 * ==========
        String text10 = "abc*def*ghi";
        assertEquals("abc-def-ghi", text10.replace("*", "-"));
        assertEquals("abc-def-ghi", text10.replaceAll("\\*", "-"));
        assertEquals("abc-def-ghi", text10.replaceAll(Pattern.quote("*"), "-")); // Pattern.quote 形式

        // ========== 11. 加号 + ==========
        String text11 = "a+b+c";
        assertEquals("a-b-c", text11.replace("+", "-"));
        assertEquals("a-b-c", text11.replaceAll("\\+", "-"));
        assertEquals("a-b-c", text11.replaceAll(Pattern.quote("+"), "-")); // Pattern.quote 形式

        // ========== 12. 竖线 | ==========
        String text12 = "a|b|c";
        assertEquals("a-b-c", text12.replace("|", "-"));
        assertEquals("a-b-c", text12.replaceAll("\\|", "-"));
        assertEquals("a-b-c", text12.replaceAll(Pattern.quote("|"), "-")); // Pattern.quote 形式

        // ========== 13. 花括号 {} ==========
        String text13 = "Hello {world}";
        assertEquals("Hello (world)", text13.replace("{", "(").replace("}", ")"));
        assertEquals("Hello (world)", text13.replaceAll("\\{", "(").replaceAll("\\}", ")"));
        assertEquals("Hello (world)", text13.replaceAll(Pattern.quote("{"), "(").replaceAll(Pattern.quote("}"), ")")); // Pattern.quote 形式

        // ========== 14. 圆括号 () ==========
        String text14 = "func(a,b)";
        assertEquals("func[a,b)", text14.replace("(", "[").replace("]", ")"));
        assertEquals("func[a,b]", text14.replaceAll("\\(", "[").replaceAll("\\)", "]"));
        assertEquals("func[a,b]", text14.replaceAll(Pattern.quote("("), "[").replaceAll(Pattern.quote(")"), "]")); // Pattern.quote 形式

        // ========== 15. 实际应用场景：清理用户输入 ==========
        String userInput = "用户输入了特殊字符 .*+?^${}()|[]\\";
        // 使用 replace 简单替换单个字符
        assertEquals("用户输入了特殊字符 _*+?^${}()|[]\\", userInput.replace(".", "_"));
        // 使用 replaceAll 批量替换多个正则元字符
        assertEquals("用户输入了特殊字符 ______________", userInput.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "_"));
        // 使用 Pattern.quote 替换特定字符（需要多次调用或循环）
        assertEquals("用户输入了特殊字符 _*+?^${}()|[]\\", userInput.replaceAll(Pattern.quote("."), "_"));
    }

    /**
     * 验证错误解析。
     */
    @Test
    public void chineseCharTest() {
        String text = "𠮷";
        log.info("原始字符串: {}", text);
        log.info("字符串长度 (char count): {}", text.length());
        char high = text.charAt(0);
        log.info("仅打印高位: [{}]", high);
        char low = text.charAt(1);
        log.info("仅打印低位: [{}]", low);
        try {
            byte[] bytes = "中".getBytes("UTF-8");
            String broken = new String(bytes, 0, 2, "UTF-8");
            log.info("UTF-8截断导致的乱码: {}", broken);
        } catch (Exception e) {
            log.error("处理中文字符异常", e);
        }
    }

    /**
     * 验证对中文字串在字节层面进行不正常切分，
     * 破坏 UTF-8 编码字节边界时，导致产生黑色菱形问号（U+FFFD ）的特殊输出。
     */
    @Test
    public void testChineseStringImproperSlicing() {
        // 中文字符串，在UTF-8编码下通常每个中文字符占3个字节
        String originalStr = "测试";
        byte[] utf8Bytes = originalStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // 故意从中间截断一个中文字符。"测"占3个字节，这里只取前2个字节，破坏了完整的UTF-8字符边界
        byte[] brokenBytes = Arrays.copyOfRange(utf8Bytes, 0, 2);

        // 使用UTF-8重新构造字符串时，遇到不完整的字节序列，会用替换字符(Replacement Character, U+FFFD, 即黑色菱形问号 )代替
        String brokenStr = new String(brokenBytes, java.nio.charset.StandardCharsets.UTF_8);

        log.info("原始中文字符串: {}", originalStr);
        log.info("由于不正常切分导致的特殊输出(包含黑色菱形问号): {}", brokenStr);

        // 验证确实生成了带有 U+FFFD 的特殊输出
        assertTrue(brokenStr.contains("\uFFFD"));
    }

    @Test
    public void testUpstreamBug() {
        String mockUpstreamStream = """
                data: {"content": "测"}
                
                data: {"content": "试"}
                
                data: {"content": "\uFFFD"}
                
                data: {"content": "\uFFFD"}
                
                data: {"content": "\uFFFD"}
                
                """;

        byte[] bytesFromNetwork = mockUpstreamStream.getBytes(StandardCharsets.UTF_8);

        int validEnd = findValidUtf8End(bytesFromNetwork);

        log.info("--- 模拟本地按 \\n\\n 拆分并验证字符 ---");
        int start = 0;
        int fffdCount = 0; // 记录发现了多少个脏数据块

        for (int i = 0; i < validEnd - 1; i++) {
            if (bytesFromNetwork[i] == '\n' && bytesFromNetwork[i + 1] == '\n') {
                int eventLength = i - start + 2;
                String event = new String(bytesFromNetwork, start, eventLength, StandardCharsets.UTF_8);

                if (event.contains("\uFFFD")) {
                    fffdCount++;
                    log.info("【抓到脏数据】发现 U+FFFD！完整内容: {}", event);
                } else {
                    log.info("【正常数据】: {}", event);
                }
                // ===============================================

                start = i + 2;
            }
        }

        log.info("----------------------------------------");
        log.info("验证结束，共拦截到 {} 个包含 U+FFFD 的损坏事件。", fffdCount);

    }

    @Test
    public void compare1Test() {
        // 文件比较的后缀大小写
        int c1 = "0001.jpg".compareTo("0001.JPG");
        assertFalse(c1 < 0);
    }

    /**
     * 对比"区间内子串查找"（判断 [from, to) 是否含 needle）的两种实现：
     * 朴素版 {@link #naiveContains}（{@link String#indexOf(String, int)} 无界右扫）vs
     * 正式方法 {@link StringUtil#containsInRange(String, int, int, String)}
     * （首字符 indexOf + 区间边界 + regionMatches）。
     */
    @Test
    public void containsInRangeCompareTest() {
        // 100 行文本：needle 只在最后一行，让"判断第 1 行"时朴素版白扫到文件尾
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("line-").append(String.format("%04d", i)).append('\n');
        }
        String text = sb.toString();
        String needle = "line-0099";
        int line1End = text.indexOf('\n');              // 第 1 行行尾
        int lastLineStart = text.lastIndexOf(needle);   // 最后一行起点

        // 1. 正确性：两实现结果一致（第 1 行不命中、最后一行命中）
        assertFalse(naiveContains(text, 0, line1End, needle));
        assertFalse(StringUtil.containsInRange(text, 0, line1End, needle));
        assertTrue(naiveContains(text, lastLineStart, text.length(), needle));
        assertTrue(StringUtil.containsInRange(text, lastLineStart, text.length(), needle));

        // 2. 边界：needle 起点在区间内但终点越界 → 朴素版误判，正式方法正确
        String one = "line-0099 pad";
        assertTrue(naiveContains(one, 0, 3, needle));    // indexOf 命中 0 < 3 → 误判
        assertFalse(StringUtil.containsInRange(one, 0, 3, needle)); // 0+10 > 3，needle 放不进区间

        // 3. 首字符出现两次：第一个 'l' 位置不是真命中，循环必须跳到第二个 'l' 才算中
        String two = "line-line-0099";
        assertTrue(naiveContains(two, 0, two.length(), needle));
        assertTrue(StringUtil.containsInRange(two, 0, two.length(), needle));

        // 4. 性能对比：needle 在第 100 行，反复判断第 1 行是否命中
        long t0 = System.nanoTime();
        for (int i = 0; i < 200_000; i++) {
            naiveContains(text, 0, line1End, needle);
        }
        long naiveMs = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        for (int i = 0; i < 200_000; i++) {
            StringUtil.containsInRange(text, 0, line1End, needle);
        }
        long boundedMs = (System.nanoTime() - t1) / 1_000_000;

        log.info("朴素 indexOf(String, from) 版: {} ms", naiveMs);
        log.info("StringUtil.containsInRange 版: {} ms", boundedMs);
        assertTrue(boundedMs <= naiveMs,
                "containsInRange 应至少不慢于朴素版: bounded=" + boundedMs + "ms, naive=" + naiveMs + "ms");
    }

    /**
     * 朴素版：indexOf(String, from) 无界右扫，事后用 hit < to 判断是否在区间内。
     */
    private static boolean naiveContains(String text, int from, int to, String needle) {
        int hit = text.indexOf(needle, from);
        return hit >= 0 && hit < to;
    }

    private int findValidUtf8End(byte[] bytes) {
        int length = bytes.length;
        if (length == 0) return 0;
        for (int i = Math.max(0, length - 4); i < length; i++) {
            byte b = bytes[i];
            if ((b & 0x80) == 0) {
                continue;
            } else if ((b & 0xE0) == 0xC0) {
                if (i + 1 >= length) return i;
            } else if ((b & 0xF0) == 0xE0) {
                if (i + 2 >= length) return i;
            } else if ((b & 0xF8) == 0xF0) {
                if (i + 3 >= length) return i;
            }
        }
        return length;
    }

}
