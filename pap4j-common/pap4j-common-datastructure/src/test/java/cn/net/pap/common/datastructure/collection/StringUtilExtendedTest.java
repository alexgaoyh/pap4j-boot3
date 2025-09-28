package cn.net.pap.common.datastructure.collection;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StringUtilExtendedTest {

    @Test
    public void testIsEmptyAndIsNotEmpty() {
        assertTrue(StringUtilExtended.isEmpty(null));
        assertTrue(StringUtilExtended.isEmpty(""));
        assertFalse(StringUtilExtended.isEmpty("hello"));
        assertTrue(StringUtilExtended.isNotEmpty("abc"));
        assertFalse(StringUtilExtended.isNotEmpty(""));
    }

    @Test
    public void testIsBlankAndIsNotBlank() {
        assertTrue(StringUtilExtended.isBlank(null));
        assertTrue(StringUtilExtended.isBlank(""));
        assertTrue(StringUtilExtended.isBlank(" \t\n"));
        assertFalse(StringUtilExtended.isBlank("abc"));
        assertTrue(StringUtilExtended.isNotBlank("abc"));
        assertFalse(StringUtilExtended.isNotBlank("   "));
    }

    @Test
    public void testEqualsIgnoreCase() {
        assertTrue(StringUtilExtended.equalsIgnoreCase("abc", "ABC"));
        assertFalse(StringUtilExtended.equalsIgnoreCase("abc", "abcd"));
        assertTrue(StringUtilExtended.equalsIgnoreCase(null, null));
        assertFalse(StringUtilExtended.equalsIgnoreCase(null, "abc"));
    }

    @Test
    public void testCharCount() {
        assertEquals(5, StringUtilExtended.charCount("hello"));
        assertEquals(3, StringUtilExtended.charCount("a𠀀b")); // '𠀀' 是扩展区字符
        assertEquals(4, StringUtilExtended.charCount("A😊B𠀀"));
    }

    @Test
    public void testCharAt() {
        assertEquals("h", StringUtilExtended.charAt("hello", 0));
        assertEquals("𠀀", StringUtilExtended.charAt("a𠀀b", 1));
        assertEquals("b", StringUtilExtended.charAt("a𠀀b", 2));
        assertEquals("😊", StringUtilExtended.charAt("A😊B𠀀", 1));
    }

    @Test
    public void testSubstringByCharacter() {
        assertEquals("llo", StringUtilExtended.substringByCharacter("hello", 2));
        assertEquals("𠀀b", StringUtilExtended.substringByCharacter("a𠀀b", 1));
        assertEquals("B𠀀", StringUtilExtended.substringByCharacter("A😊B𠀀", 2));
        assertEquals("😊B𠀀", StringUtilExtended.substringByCharacter("A😊B𠀀", 1));
    }

    @Test
    public void testSubstringByCharacterWithEnd() {
        assertEquals("ll", StringUtilExtended.substringByCharacter("hello", 2, 4));
        assertEquals("𠀀", StringUtilExtended.substringByCharacter("a𠀀b", 1, 2));
        assertEquals("😊B", StringUtilExtended.substringByCharacter("A😊B𠀀", 1, 3));
    }

    @Test
    public void testIndexOfCharacter() {
        assertEquals(1, StringUtilExtended.indexOfCharacter("a𠀀b", "𠀀"));
        assertEquals(0, StringUtilExtended.indexOfCharacter("hello", "h"));
        assertEquals(-1, StringUtilExtended.indexOfCharacter("hello", "x"));
    }

    @Test
    public void testLastIndexOfCharacter() {
        assertEquals(3, StringUtilExtended.lastIndexOfCharacter("a𠀀b𠀀", "𠀀"));
        assertEquals(0, StringUtilExtended.lastIndexOfCharacter("hello", "h"));
        assertEquals(-1, StringUtilExtended.lastIndexOfCharacter("hello", "x"));
        assertEquals(4, StringUtilExtended.lastIndexOfCharacter("a𠀀b𠀀h", "h"));
    }

    @Test
    public void testContains() {
        assertTrue(StringUtilExtended.contains("hello", "ell"));
        assertFalse(StringUtilExtended.contains("hello", "xyz"));
    }

    @Test
    public void testContainsIgnoreCase() {
        assertTrue(StringUtilExtended.containsIgnoreCase("Hello", "HEL"));
        assertFalse(StringUtilExtended.containsIgnoreCase("hello", "XYZ"));
    }

    @Test
    public void testSplitByCharacter() {
        List<String> list = StringUtilExtended.splitByCharacter("a𠀀b😊");
        assertArrayEquals(new String[]{"a", "𠀀", "b", "😊"}, list.toArray(new String[0]));
    }

    @Test
    public void testJoinByCharacter() {
        List<String> list = Arrays.asList("a", "𠀀", "b", "😊");
        assertEquals("a𠀀b😊", StringUtilExtended.joinByCharacter(list));
    }

    @Test
    public void testReverse() {
        assertEquals("olleh", StringUtilExtended.reverse("hello"));
        assertEquals("b𠀀a", StringUtilExtended.reverse("a𠀀b"));
        assertEquals("𠀀B😊A", StringUtilExtended.reverse("A😊B𠀀"));
    }

    @Test
    public void testIsPalindrome() {
        assertTrue(StringUtilExtended.isPalindrome("aba"));
        assertTrue(StringUtilExtended.isPalindrome("a𠀀a"));
        assertTrue(StringUtilExtended.isPalindrome("😊a😊"));
        assertFalse(StringUtilExtended.isPalindrome("abc"));
    }

    @Test
    public void testCapitalize() {
        assertEquals("Hello", StringUtilExtended.capitalize("hello"));
        assertEquals("𠀀abc", StringUtilExtended.capitalize("𠀀abc"));
        assertEquals("😊abc", StringUtilExtended.capitalize("😊abc"));
    }

    @Test
    public void testUncapitalize() {
        assertEquals("hello", StringUtilExtended.uncapitalize("Hello"));
        assertEquals("𠀀abc", StringUtilExtended.uncapitalize("𠀀abc"));
        assertEquals("😊abc", StringUtilExtended.uncapitalize("😊abc"));
    }
}
