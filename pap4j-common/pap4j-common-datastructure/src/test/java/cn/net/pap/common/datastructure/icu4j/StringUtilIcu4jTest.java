package cn.net.pap.common.datastructure.icu4j;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}

