package cn.net.pap.common.datastructure.collection;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringUtilTest {

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
        System.out.println();
        str.chars().mapToObj(c -> (char) c).forEach(System.out::println);
        System.out.println();

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
            System.out.println(match);
        }
    }

    @Test
    public void replaceFirstTest() {
        String s = StringUtil.replaceFirst("一二三(四五六)七八九十", ")", "");
        System.out.println(s);

        s = StringUtil.replaceFirst("一二三(四五六)七八九十", ".*", "");
        System.out.println(s);

        s = "一二三(四五六)七八九十".replaceFirst(".*", "");
        System.out.println(s);
    }

    @Test
    public void splitTest() {
        String input = "苹果、香蕉#西瓜 葡萄,橙子";
        String delimiters = "、# ,";

        System.out.println("原始字符串: " + input);
        System.out.println("分隔符: " + delimiters);

        String[] result = StringUtil.split(input, delimiters);
        System.out.println("拆分结果: " + Arrays.toString(result));

        List<String> filteredResult = StringUtil.splitAndFilter(input, delimiters);
        System.out.println("拆分并过滤空字符串结果: " + filteredResult);
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
            System.out.println(seg);
        }
    }

    /**
     * 黑色菱形问号
     */
    @Test
    public void chineseErrorTest() {
        String replacementChar = "\uFFFD";
        System.out.println("直接打印: " + replacementChar);
    }

    /**
     * 验证错误解析。
     */
    @Test
    public void chineseCharTest() {
        String text = "𠮷";
        System.out.println("原始字符串: " + text);
        System.out.println("字符串长度 (char count): " + text.length());
        char high = text.charAt(0);
        System.out.println("仅打印高位: [" + high + "]");
        char low = text.charAt(1);
        System.out.println("仅打印低位: [" + low + "]");
        try {
            byte[] bytes = "中".getBytes("UTF-8");
            String broken = new String(bytes, 0, 2, "UTF-8");
            System.out.println("UTF-8截断导致的乱码: " + broken);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 验证对中文字符串在字节层面进行不正常切分，
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

        System.out.println("原始中文字符串: " + originalStr);
        System.out.println("由于不正常切分导致的特殊输出(包含黑色菱形问号): " + brokenStr);

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
                
                data: {"content": "\uFFFD\"}
                
                """;

        byte[] bytesFromNetwork = mockUpstreamStream.getBytes(StandardCharsets.UTF_8);

        int validEnd = findValidUtf8End(bytesFromNetwork);

        System.out.println("--- 模拟本地按 \\n\\n 拆分并验证字符 ---");
        int start = 0;
        int fffdCount = 0; // 记录发现了多少个脏数据块

        for (int i = 0; i < validEnd - 1; i++) {
            if (bytesFromNetwork[i] == '\n' && bytesFromNetwork[i + 1] == '\n') {
                int eventLength = i - start + 2;
                String event = new String(bytesFromNetwork, start, eventLength, StandardCharsets.UTF_8);

                if (event.contains("\uFFFD")) {
                    fffdCount++;
                    System.out.print("【抓到脏数据】发现 U+FFFD！完整内容: " + event);
                } else {
                    System.out.print("【正常数据】: " + event);
                }
                // ===============================================

                start = i + 2;
            }
        }

        System.out.println("----------------------------------------");
        System.out.println("验证结束，共拦截到 " + fffdCount + " 个包含 U+FFFD 的损坏事件。");

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
