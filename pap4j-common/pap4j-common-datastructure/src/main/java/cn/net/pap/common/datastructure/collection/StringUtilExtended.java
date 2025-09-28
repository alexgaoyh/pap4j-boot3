package cn.net.pap.common.datastructure.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StringUtilExtended {

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
        int cpCount = charCount(str);
        for (int i = 0; i < cpCount; i++) {
            int cp = str.codePointAt(str.offsetByCodePoints(0, i));
            if (!Character.isWhitespace(cp)) return false;
        }
        return true;
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static boolean equalsIgnoreCase(String str1, String str2) {
        if (str1 == null && str2 == null) return true;
        if (str1 == null || str2 == null) return false;
        return str1.equalsIgnoreCase(str2);
    }

    /**
     * ------------------- 基本字符操作 -------------------
     */

    public static int charCount(String input) {
        if (input == null) return 0;
        return input.codePointCount(0, input.length());
    }

    public static String charAt(String input, int charIndex) {
        if (input == null) return null;
        int index = input.offsetByCodePoints(0, charIndex);
        int codePoint = input.codePointAt(index);
        return new String(Character.toChars(codePoint));
    }

    public static String substringByCharacter(String input, int start) {
        if (input == null) return null;
        int beginIndex = input.offsetByCodePoints(0, start);
        return input.substring(beginIndex);
    }

    public static String substringByCharacter(String input, int start, int end) {
        if (input == null) return null;
        int beginIndex = input.offsetByCodePoints(0, start);
        int endIndex = input.offsetByCodePoints(0, end);
        return input.substring(beginIndex, endIndex);
    }

    public static int indexOfCharacter(String input, String searchChar) {
        if (input == null || searchChar == null || searchChar.isEmpty()) return -1;
        int searchCp = searchChar.codePointAt(0);
        int cpCount = charCount(input);
        for (int i = 0; i < cpCount; i++) {
            if (input.codePointAt(input.offsetByCodePoints(0, i)) == searchCp) return i;
        }
        return -1;
    }

    public static int lastIndexOfCharacter(String input, String searchChar) {
        if (input == null || searchChar == null || searchChar.isEmpty()) return -1;
        int searchCp = searchChar.codePointAt(0);
        int cpCount = charCount(input);
        for (int i = cpCount - 1; i >= 0; i--) {
            if (input.codePointAt(input.offsetByCodePoints(0, i)) == searchCp) return i;
        }
        return -1;
    }

    public static boolean contains(String input, String searchStr) {
        return input != null && searchStr != null && input.contains(searchStr);
    }

    public static boolean containsIgnoreCase(String input, String searchStr) {
        return input != null && searchStr != null &&
                input.toLowerCase().contains(searchStr.toLowerCase());
    }

    /**
     * ------------------- 字符串分割与连接 -------------------
     */

    public static List<String> splitByCharacter(String input) {
        List<String> result = new ArrayList<>();
        if (input == null) return result;
        int i = 0;
        while (i < input.length()) {
            int cp = input.codePointAt(i);
            result.add(new String(Character.toChars(cp)));
            i += Character.charCount(cp);
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
        StringBuilder sb = new StringBuilder();
        for (int i = charCount(input) - 1; i >= 0; i--) {
            sb.append(charAt(input, i));
        }
        return sb.toString();
    }

    public static boolean isPalindrome(String input) {
        if (input == null) return false;
        int left = 0;
        int right = charCount(input) - 1;
        while (left < right) {
            if (!Objects.equals(charAt(input, left), charAt(input, right))) return false;
            left++;
            right--;
        }
        return true;
    }

    public static String capitalize(String input) {
        if (isEmpty(input)) return input;
        return charAt(input, 0).toUpperCase() + substringByCharacter(input, 1);
    }

    public static String uncapitalize(String input) {
        if (isEmpty(input)) return input;
        return charAt(input, 0).toLowerCase() + substringByCharacter(input, 1);
    }

}

