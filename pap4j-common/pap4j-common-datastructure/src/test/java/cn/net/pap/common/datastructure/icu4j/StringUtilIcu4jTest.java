package cn.net.pap.common.datastructure.icu4j;

import com.ibm.icu.text.*;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringUtilIcu4jTest {

    @Test
    public void testIsEmptyAndIsNotEmpty() {
        assertTrue(StringUtilIcu4j.isEmpty(null));
        assertTrue(StringUtilIcu4j.isEmpty(""));
        assertFalse(StringUtilIcu4j.isEmpty("hello"));
        assertTrue(StringUtilIcu4j.isNotEmpty("abc"));
        assertFalse(StringUtilIcu4j.isNotEmpty(""));
    }

    @Test
    public void testIsBlankAndIsNotBlank() {
        assertTrue(StringUtilIcu4j.isBlank(null));
        assertTrue(StringUtilIcu4j.isBlank(""));
        assertTrue(StringUtilIcu4j.isBlank(" \t\n"));
        assertFalse(StringUtilIcu4j.isBlank("abc"));
        assertTrue(StringUtilIcu4j.isNotBlank("abc"));
        assertFalse(StringUtilIcu4j.isNotBlank("   "));
    }

    @Test
    public void testEqualsIgnoreCase() {
        assertTrue(StringUtilIcu4j.equalsIgnoreCase("abc", "ABC"));
        assertFalse(StringUtilIcu4j.equalsIgnoreCase("abc", "abcd"));
        assertTrue(StringUtilIcu4j.equalsIgnoreCase(null, null));
        assertFalse(StringUtilIcu4j.equalsIgnoreCase(null, "abc"));
    }

    @Test
    public void testCharCount() {
        assertEquals(5, StringUtilIcu4j.charCount("hello"));
        assertEquals(3, StringUtilIcu4j.charCount("a𠀀b")); // '𠀀' 是扩展区字符
        assertEquals(4, StringUtilIcu4j.charCount("A😊B𠀀"));
    }

    @Test
    public void testCharAt() {
        assertEquals("h", StringUtilIcu4j.charAt("hello", 0));
        assertEquals("𠀀", StringUtilIcu4j.charAt("a𠀀b", 1));
        assertEquals("b", StringUtilIcu4j.charAt("a𠀀b", 2));
        assertEquals("😊", StringUtilIcu4j.charAt("A😊B𠀀", 1));
    }

    @Test
    public void testSubstringByCharacter() {
        assertEquals("llo", StringUtilIcu4j.substringByCharacter("hello", 2));
        assertEquals("𠀀b", StringUtilIcu4j.substringByCharacter("a𠀀b", 1));
        assertEquals("B𠀀", StringUtilIcu4j.substringByCharacter("A😊B𠀀", 2));
        assertEquals("😊B𠀀", StringUtilIcu4j.substringByCharacter("A😊B𠀀", 1));
    }

    @Test
    public void testSubstringByCharacterWithEnd() {
        assertEquals("ll", StringUtilIcu4j.substringByCharacter("hello", 2, 4));
        assertEquals("𠀀", StringUtilIcu4j.substringByCharacter("a𠀀b", 1, 2));
        assertEquals("😊B", StringUtilIcu4j.substringByCharacter("A😊B𠀀", 1, 3));
    }

    @Test
    public void testIndexOfCharacter() {
        assertEquals(1, StringUtilIcu4j.indexOfCharacter("a𠀀b", "𠀀"));
        assertEquals(0, StringUtilIcu4j.indexOfCharacter("hello", "h"));
        assertEquals(-1, StringUtilIcu4j.indexOfCharacter("hello", "x"));
    }

    @Test
    public void testLastIndexOfCharacter() {
        assertEquals(3, StringUtilIcu4j.lastIndexOfCharacter("a𠀀b𠀀", "𠀀"));
        assertEquals(0, StringUtilIcu4j.lastIndexOfCharacter("hello", "h"));
        assertEquals(-1, StringUtilIcu4j.lastIndexOfCharacter("hello", "x"));
        assertEquals(4, StringUtilIcu4j.lastIndexOfCharacter("a𠀀b𠀀h", "h"));
    }

    @Test
    public void testContains() {
        assertTrue(StringUtilIcu4j.contains("hello", "ell"));
        assertFalse(StringUtilIcu4j.contains("hello", "xyz"));
    }

    @Test
    public void testContainsIgnoreCase() {
        assertTrue(StringUtilIcu4j.containsIgnoreCase("Hello", "HEL"));
        assertFalse(StringUtilIcu4j.containsIgnoreCase("hello", "XYZ"));
    }

    @Test
    public void testSplitByCharacter() {
        List<String> list = StringUtilIcu4j.splitByCharacter("a𠀀b😊");
        assertArrayEquals(new String[]{"a", "𠀀", "b", "😊"}, list.toArray(new String[0]));

        // 效果其实不太好，可以全部切换为 icu4j 的实现。
        List<String> list2 = StringUtilIcu4j.splitByCharacter("👨‍👩‍👧‍👦𠀀He");
        assertArrayEquals(new String[]{"👨‍👩‍👧‍👦", "𠀀", "H", "e"}, list2.toArray(new String[0]));
    }

    @Test
    public void testJoinByCharacter() {
        List<String> list = Arrays.asList("a", "𠀀", "b", "😊");
        assertEquals("a𠀀b😊", StringUtilIcu4j.joinByCharacter(list));
    }

    @Test
    public void testReverse() {
        assertEquals("olleh", StringUtilIcu4j.reverse("hello"));
        assertEquals("b𠀀a", StringUtilIcu4j.reverse("a𠀀b"));
        assertEquals("𠀀B😊A", StringUtilIcu4j.reverse("A😊B𠀀"));
    }

    @Test
    public void testIsPalindrome() {
        assertTrue(StringUtilIcu4j.isPalindrome("aba"));
        assertTrue(StringUtilIcu4j.isPalindrome("a𠀀a"));
        assertTrue(StringUtilIcu4j.isPalindrome("😊a😊"));
        assertFalse(StringUtilIcu4j.isPalindrome("abc"));
    }

    @Test
    public void testCapitalize() {
        assertEquals("Hello", StringUtilIcu4j.capitalize("hello"));
        assertEquals("𠀀abc", StringUtilIcu4j.capitalize("𠀀abc"));
        assertEquals("😊abc", StringUtilIcu4j.capitalize("😊abc"));
    }

    @Test
    public void testUncapitalize() {
        assertEquals("hello", StringUtilIcu4j.uncapitalize("Hello"));
        assertEquals("𠀀abc", StringUtilIcu4j.uncapitalize("𠀀abc"));
        assertEquals("😊abc", StringUtilIcu4j.uncapitalize("😊abc"));
    }

    @Test
    public void test1() throws Exception {
        // 使用 MessageFormat 处理复杂消息
        MessageFormat fmt = new MessageFormat("{0,choice,0#No files|1#One file|1<{0,number} files} found", Locale.US);
        Object[] args = {5};
        String result = fmt.format(args);
        assertTrue(result.equals("5 files found"));

        // 创建自定义排序规则
        RuleBasedCollator collator = new RuleBasedCollator("& a < b < c < 'ch' < d");
        String[] words = {"apple", "dog", "cat", "chair"};
        Arrays.sort(words, collator);
        assertTrue("[apple, cat, chair, dog]".equals(Arrays.toString(words)));

        // 使用 RuleBasedNumberFormat 进行规则基础的数字格式化
        RuleBasedNumberFormat rbnf = new RuleBasedNumberFormat(Locale.US, RuleBasedNumberFormat.SPELLOUT);
        assertTrue("twelve thousand three hundred forty-five".equals(rbnf.format(12345)));

        // 货币
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new ULocale("en_US@currency=JPY"));
        assertTrue("¥1,000".equals(currencyFormat.format(1000)));

    }

    @Test
    public void testDiffSegment() {
        // 这些字符串能明显展示差异
        String[] testTexts = {
                "测试𠮷字",                    // CJK扩展B字符
                "Hello👋世界",                  // 简单emoji
                "家庭👨‍👩‍👧‍👦幸福",              // 零宽连接符emoji
                "符号∀∃∈",                     // 数学符号
                "𠀀𠀁𠀂",                      // CJK扩展A字符
                "emoji🎉测试🔥",                // 多个emoji
                "古字𪚥𪚦𪚧",                  // CJK扩展C字符
                "混合𠮷emoji🎉文字"             // 混合所有类型
        };

        for (String text : testTexts) {
            System.out.println();

            List<String> icuSegments = segmentWithICU(text);
            List<String> stdSegments = segmentWithStandard(text);

            // 检查差异
            if (!icuSegments.equals(stdSegments)) {
                System.out.println("\n测试文本: " + text + "❌ 发现差异！");
                System.out.println(icuSegments);
                System.out.println(stdSegments);
            }
        }

    }

    private List<String> segmentWithICU(String text) {
        com.ibm.icu.text.BreakIterator iterator = com.ibm.icu.text.BreakIterator.getCharacterInstance();
        iterator.setText(text);

        List<String> segments = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next();
             end != BreakIterator.DONE;
             start = end, end = iterator.next()) {
            String segment = text.substring(start, end).trim();
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private List<String> segmentWithStandard(String text) {
        java.text.BreakIterator iterator = java.text.BreakIterator.getCharacterInstance();
        iterator.setText(text);

        List<String> segments = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next();
             end != BreakIterator.DONE;
             start = end, end = iterator.next()) {
            String segment = text.substring(start, end).trim();
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

}

