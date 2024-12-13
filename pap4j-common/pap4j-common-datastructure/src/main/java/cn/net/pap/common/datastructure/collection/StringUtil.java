package cn.net.pap.common.datastructure.collection;

public class StringUtil {

    /**
     * 找到字符串A中第一次出现字符串B的位置（考虑扩展区字符，返回字符个数的位置）
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
     * @param str 原始字符串
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

}
