package cn.net.pap.common.datastructure.fst;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具类与测试方法
 */
public class FSTUtil {

    /**
     * 最大匹配
     *
     * @param text
     * @param dict
     * @return
     */
    @Deprecated
    public static List<String> maxMatch(String text, FST dict) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = text.length();
            while (end > start) {
                String substr = text.substring(start, end);
                if (dict.isWord(substr)) {
                    result.add(substr);
                    start = end;
                    break;
                }
                end--;
            }
            if (end == start) {
                String substring = text.substring(start, end == text.length() ? end : end + 1);
                if (substring != null && !"".equals(substring)) {
                    result.add(substring);
                }
                start++;
            }
        }
        return result;
    }

    /**
     * 最大匹配，并且额外返回了字典在文本中所处的位置。
     *
     * @param text
     * @param dict
     * @return
     */
    public static List<ValueLocationDTO> maxMatchLocation(String text, FST dict) {
        List<ValueLocationDTO> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            boolean continueFlag = false;
            int end = text.length();
            while (end > start) {
                String substr = text.substring(start, end);
                if (dict.isWord(substr)) {
                    result.add(new ValueLocationDTO(substr, start, end));
                    start = end;
                    continueFlag = true;
                    break;
                }
                if(Character.isSupplementaryCodePoint(substr.codePointBefore(substr.length()))) {
                    end = end - 2;
                } else {
                    end = end - 1;
                }
            }
            if (continueFlag) {
                continue;
            }
            if (end == start) {
                char[] chars = text.toCharArray();
                if (Character.isHighSurrogate(chars[start])) {
                    start = start + 2;
                } else {
                    start = start + 1;
                }
            }
        }
        return result;
    }

}
