package cn.net.pap.common.datastructure.collection;

import org.junit.jupiter.api.Test;

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

}
