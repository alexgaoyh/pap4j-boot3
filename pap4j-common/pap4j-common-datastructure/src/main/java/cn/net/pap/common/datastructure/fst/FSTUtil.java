package cn.net.pap.common.datastructure.fst;

import java.util.ArrayList;
import java.util.List;

/**
 * <p><strong>FSTUtil</strong> 是用于有限状态机（FST）操作的工具类。</p>
 *
 * <p>此类提供了一些实用方法，用于使用 {@link FST} 数据结构执行基于字典的字符串匹配。</p>
 *
 * <ul>
 *     <li>提供最大正向匹配算法。</li>
 *     <li>支持返回匹配的字符串或其确切位置。</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * FST dict = new FST();
 * // ... 初始化 dict ...
 * List<ValueLocationDTO> matches = FSTUtil.maxMatchLocation("some text", dict);
 * }</pre>
 */
public class FSTUtil {

    /**
     * <p>使用提供的字典对给定文本执行最大正向匹配。</p>
     * 
     * <p>此方法从输入文本中查找字典里所有可能的最长匹配词。如果某个字符没有找到匹配项，则将其视为单字符词。</p>
     *
     * @param text 要匹配的输入字符串。
     * @param dict 用于匹配的 {@link FST} 字典。
     * @return 匹配字符串的 {@link List}。
     * @deprecated 请改用 {@link #maxMatchLocation(String, FST)} 以获得更好的准确性和位置详细信息。
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
     * <p>执行最大正向匹配并返回每个匹配项的确切位置。</p>
     *
     * <p>此方法遍历文本并尝试查找字典中存在的最长子串。它能正确处理补充的 Unicode 字符。</p>
     *
     * <ul>
     *     <li>提取匹配的词汇。</li>
     *     <li>记录匹配项的起始和结束索引。</li>
     * </ul>
     *
     * @param text 要匹配的输入字符串。
     * @param dict 用于匹配的 {@link FST} 字典。
     * @return 包含匹配文本及其位置的 {@link ValueLocationDTO} 对象的 {@link List}。
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
