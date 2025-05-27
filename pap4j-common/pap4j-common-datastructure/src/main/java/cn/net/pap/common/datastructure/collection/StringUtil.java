package cn.net.pap.common.datastructure.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StringUtil {

    public static String replaceFirst(String str, String regex, String replacement) {
        return str.replaceFirst(Pattern.quote(regex), replacement);
    }

    /**
     * print
     *
     * @param str
     */
    public static void print(String str) {
        for (int strIdx = 0; strIdx < str.length(); ) {
            int codePoint = str.codePointAt(strIdx);
            String c = new String(Character.toChars(codePoint));
            System.out.print(c);
            strIdx += Character.charCount(codePoint);
        }
    }

    /**
     * @param input
     * @param specialStrings
     * @return
     */
    public static List<String> groupSpecialStrings(String input, List<String> specialStrings) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < input.length(); ) {
            int codePoint = input.codePointAt(i);
            String c = new String(Character.toChars(codePoint));
            boolean found = false;
            for (String special : specialStrings) {
                if (input.startsWith(special, i)) {
                    result.add(special);
                    i += special.length();
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.add(c);
                i += Character.charCount(codePoint);
            }
        }
        return result;
    }

    /**
     * 找到字符串A中第一次出现字符串B的位置（考虑扩展区字符，返回字符个数的位置）
     *
     * @param A 原始字符串
     * @param B 要查找的子字符串
     * @return 子字符串B在字符串A中的字符位置，如果没有找到返回-1
     */
    public static int indexOf2(String A, String B) {
        if (A == null || B == null) {
            return -1;
        }
        int lenA = A.length();
        int lenB = B.length();
        if (lenB == 0) {
            return 0;
        }
        for (int i = 0; i <= lenA - lenB; i++) {
            int j = 0;
            while (j < lenB && A.codePointAt(i + j) == B.codePointAt(j)) {
                j++;
            }
            if (j == lenB) {
                return getCharacterIndex(A, i);
            }
        }
        return -1;
    }

    /**
     * 根据字符串的索引位置，返回以字符计数为单位的实际位置
     *
     * @param str   原始字符串
     * @param index 字符串的索引位置（按代码点方式）
     * @return 字符的实际位置（以字符为单位的）
     */
    private static int getCharacterIndex(String str, int index) {
        int realIndex = 0;
        for (int i = 0; i < index; i++) {
            if (Character.isHighSurrogate(str.charAt(i)) || Character.isLowSurrogate(str.charAt(i))) {
                i++;
            }
            realIndex++;
        }
        return realIndex;
    }

    /**
     * 使用给定的分隔符字符串中的所有字符作为分隔符来拆分输入字符串
     *
     * @param input      要拆分的字符串
     * @param delimiters 包含所有分隔符的字符串
     * @return 拆分后的字符串数组
     */
    public static String[] split(String input, String delimiters) {
        if (input == null) {
            return new String[0];
        }

        if (delimiters == null || delimiters.isEmpty()) {
            return new String[]{input};
        }

        // 构建正则表达式模式，匹配任意一个分隔符
        String pattern = buildPattern(delimiters);
        return input.split(pattern);
    }

    /**
     * 使用给定的分隔符字符串中的所有字符作为分隔符来拆分输入字符串，并过滤掉空字符串
     *
     * @param input      要拆分的字符串
     * @param delimiters 包含所有分隔符的字符串
     * @return 拆分后的字符串列表（不包含空字符串）
     */
    public static List<String> splitAndFilter(String input, String delimiters) {
        String[] parts = split(input, delimiters);
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    /**
     * 构建正则表达式模式
     *
     * @param delimiters 包含所有分隔符的字符串
     * @return 构建好的正则表达式字符串
     */
    private static String buildPattern(String delimiters) {
        StringBuilder pattern = new StringBuilder();
        pattern.append("[");

        for (int i = 0; i < delimiters.length(); i++) {
            char c = delimiters.charAt(i);
            // 对正则表达式中的特殊字符进行转义
            if (isRegexMetaCharacter(c)) {
                pattern.append("\\");
            }
            pattern.append(c);
        }

        pattern.append("]");
        return pattern.toString();
    }

    /**
     * 检查字符是否是正则表达式的元字符
     *
     * @param c 要检查的字符
     * @return 如果是元字符返回true，否则返回false
     */
    private static boolean isRegexMetaCharacter(char c) {
        return "\\.[]{}()*+?^$|".indexOf(c) != -1;
    }

}
