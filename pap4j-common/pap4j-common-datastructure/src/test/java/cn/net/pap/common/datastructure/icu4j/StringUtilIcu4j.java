package cn.net.pap.common.datastructure.icu4j;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.UnicodeSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class StringUtilIcu4j {

    /**
     * ------------------- 基础检查方法 -------------------
     */

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    public static boolean isBlank(String str) {
        if (str == null) return true;
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(str);
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            String grapheme = str.substring(start, end);
            // ICU 的 UnicodeSet 可以精准判断空白
            if (!new UnicodeSet("[\\p{White_Space}]").containsAll(grapheme)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static boolean equalsIgnoreCase(String str1, String str2) {
        if (str1 == null && str2 == null) return true;
        if (str1 == null || str2 == null) return false;
        return UCharacter.foldCase(str1, true).equals(UCharacter.foldCase(str2, true));
    }

    /**
     * ------------------- 基本字符操作 -------------------
     */

    public static int charCount(String input) {
        if (input == null) return 0;
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(input);
        int count = 0;
        while (it.next() != BreakIterator.DONE) count++;
        return count;
    }

    public static String charAt(String input, int charIndex) {
        if (input == null) return null;
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(input);
        int start = it.first();
        int i = 0;
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            if (i == charIndex) {
                return input.substring(start, end);
            }
            i++;
        }
        return null;
    }

    public static String substringByCharacter(String input, int start) {
        if (input == null) return null;
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(input);
        int cpStart = boundaryIndex(it, start);
        return input.substring(cpStart);
    }

    public static String substringByCharacter(String input, int start, int end) {
        if (input == null) return null;
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(input);
        int cpStart = boundaryIndex(it, start);
        int cpEnd = boundaryIndex(it, end);
        return input.substring(cpStart, cpEnd);
    }

    private static int boundaryIndex(BreakIterator it, int index) {
        int start = it.first();
        int i = 0;
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            if (i == index) return start;
            i++;
        }
        return it.last(); // fallback
    }

    public static int indexOfCharacter(String input, String searchChar) {
        if (input == null || searchChar == null || searchChar.isEmpty()) return -1;
        List<String> chars = splitByCharacter(input);
        for (int i = 0; i < chars.size(); i++) {
            if (chars.get(i).equals(searchChar)) return i;
        }
        return -1;
    }

    public static int lastIndexOfCharacter(String input, String searchChar) {
        if (input == null || searchChar == null || searchChar.isEmpty()) return -1;
        List<String> chars = splitByCharacter(input);
        for (int i = chars.size() - 1; i >= 0; i--) {
            if (chars.get(i).equals(searchChar)) return i;
        }
        return -1;
    }

    public static boolean contains(String input, String searchStr) {
        return input != null && searchStr != null && input.contains(searchStr);
    }

    public static boolean containsIgnoreCase(String input, String searchStr) {
        if (input == null || searchStr == null) return false;
        return UCharacter.foldCase(input, true).contains(UCharacter.foldCase(searchStr, true));
    }

    public static int length(String input) {
        return charCount(input);
    }

    /**
     * 根据“可视字符”索引截取字符串（仿照 String.substring(beginIndex, endIndex)）
     */
    public static String substring(String input, int beginIndex, int endIndex) {
        if (input == null) return null;
        if (beginIndex < 0 || endIndex < 0) throw new IndexOutOfBoundsException("Negative index");
        if (beginIndex > endIndex) throw new IndexOutOfBoundsException("beginIndex > endIndex");

        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(input);

        int startOffset = boundaryIndex(it, beginIndex);
        int endOffset = boundaryIndex(it, endIndex);

        return input.substring(startOffset, endOffset);
    }

    /**
     * 重载方法：只传入 beginIndex，截取到末尾
     */
    public static String substring(String input, int beginIndex) {
        if (input == null) return null;
        if (beginIndex < 0) throw new IndexOutOfBoundsException("Negative index");

        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(input);

        int startOffset = boundaryIndex(it, beginIndex);
        return input.substring(startOffset);
    }


    /**
     * ------------------- 字符串分割与连接 -------------------
     */

    public static List<String> splitByCharacter(String input) {
        List<String> result = new ArrayList<>();
        if (input == null) return result;
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(input);
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            result.add(input.substring(start, end));
        }
        return result;
    }

    public static String joinByCharacter(List<String> list) {
        if (list == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * ------------------- 变换方法 -------------------
     */

    public static String reverse(String input) {
        if (input == null) return null;
        List<String> chars = splitByCharacter(input);
        StringBuilder sb = new StringBuilder();
        for (int i = chars.size() - 1; i >= 0; i--) {
            sb.append(chars.get(i));
        }
        return sb.toString();
    }

    public static boolean isPalindrome(String input) {
        if (input == null) return false;
        List<String> chars = splitByCharacter(input);
        int left = 0;
        int right = chars.size() - 1;
        while (left < right) {
            if (!Objects.equals(chars.get(left), chars.get(right))) return false;
            left++;
            right--;
        }
        return true;
    }

    public static String capitalize(String input) {
        if (isEmpty(input)) return input;
        String first = charAt(input, 0);
        return UCharacter.toTitleCase(Locale.ROOT, first, null) + substringByCharacter(input, 1);
    }

    public static String uncapitalize(String input) {
        if (isEmpty(input)) return input;
        String first = charAt(input, 0);
        return UCharacter.toLowerCase(Locale.ROOT, first) + substringByCharacter(input, 1);
    }

    /**
     * ------------------- Unicode 归一化 -------------------
     */
    public static String normalizeNFC(String input) {
        return input == null ? null : Normalizer2.getNFCInstance().normalize(input);
    }

    public static String normalizeNFD(String input) {
        return input == null ? null : Normalizer2.getNFDInstance().normalize(input);
    }

}

