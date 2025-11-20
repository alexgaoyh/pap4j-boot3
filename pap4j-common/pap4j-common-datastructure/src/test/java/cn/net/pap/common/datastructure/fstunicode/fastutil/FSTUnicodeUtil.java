package cn.net.pap.common.datastructure.fstunicode.fastutil;

import cn.net.pap.common.datastructure.fst.ValueLocationDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * FST 工具类（基于新 FSTUnicode 实现）
 */
public class FSTUnicodeUtil {

    /**
     * 最大匹配（保留原接口，兼容旧版本）
     *
     * @param text 输入文本
     * @param dict FST字典
     * @return 匹配结果列表
     */
    @Deprecated
    public static List<String> maxMatch(String text, FSTUnicode dict) {
        List<String> result = new ArrayList<>();
        int index = 0;
        int length = text.length();

        while (index < length) {
            int maxMatchEnd = index;
            // 从当前字符开始尝试最长匹配
            for (int end = length; end > index; ) {
                int codePoint = text.codePointAt(end - 1);
                int cpCount = Character.charCount(codePoint);
                int start = end - cpCount;
                if (dict.isWord(text.subSequence(index, end))) {
                    maxMatchEnd = end;
                    break;
                }
                end = start;
            }

            if (maxMatchEnd > index) {
                result.add(text.substring(index, maxMatchEnd));
                index = maxMatchEnd;
            } else {
                int codePoint = text.codePointAt(index);
                int cpCount = Character.charCount(codePoint);
                result.add(text.substring(index, index + cpCount));
                index += cpCount;
            }
        }
        return result;
    }

    /**
     * 最大匹配（带位置信息）
     */
    public static List<ValueLocationDTO> maxMatchLocation(String text, FSTUnicode dict) {
        List<ValueLocationDTO> result = new ArrayList<>();
        int index = 0;
        int length = text.length();

        while (index < length) {
            int maxMatchEnd = index;

            // 尝试最长匹配
            for (int end = length; end > index; ) {
                if (dict.isWord(text.subSequence(index, end))) {
                    maxMatchEnd = end;
                    break;
                }
                int codePoint = text.codePointAt(end - 1);
                end -= Character.charCount(codePoint);
            }

            if (maxMatchEnd > index) {
                String matched = text.substring(index, maxMatchEnd);
                result.add(new ValueLocationDTO(matched, index, maxMatchEnd));
                index = maxMatchEnd;
            } else {
                int codePoint = text.codePointAt(index);
                int cpCount = Character.charCount(codePoint);
                String singleChar = text.substring(index, index + cpCount);
                // result.add(new ValueLocationDTO(singleChar, index, index + cpCount, index + cpCount));
                index += cpCount;
            }
        }
        return result;
    }

}
