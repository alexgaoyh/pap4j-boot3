package cn.net.pap.common.datastructure.chatset;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <h1>乱码恢复工具类 (Messy Code Recovery Utility)</h1>
 * <p>提供尝试通过不同字符集交叉编码和解码，从而修复和恢复乱码中文字符的功能。</p>
 * <ul>
 *     <li>尝试解码: {@link #tryDecode(String)}</li>
 *     <li>汉字计数及排序: {@link #countChineseCharacters(RecoveryDTO)}, {@link #sortByChineseCount(List)}</li>
 * </ul>
 *
 * @author alexgaoyh
 */
public class MessyCodeRecoveryUtil {

    /**
     * <p>匹配中文字符的正则表达式模式。</p>
     */
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\u4E00-\u9FA5]");

    /**
     * <p>用于交叉匹配的常用字符集数组。</p>
     */
    private static final String[] encodings = {
            "UTF-8", "GBK", "ISO-8859-1", "GB2312", "Big5", "UTF-16",
            "ISO-2022-JP", "Shift_JIS", "EUC-JP", "windows-1252"
    };

    /**
     * <p>尝试对乱码字符串进行所有已知字符集的组合编解码转换，并筛选出包含中文字符的有效结果。</p>
     *
     * @param input 需要进行乱码恢复的原始字符串
     * @return 包含有效中文字符的解码结果列表 {@link RecoveryDTO}
     */
    public static List<RecoveryDTO> tryDecode(String input) {
        List<RecoveryDTO> decodedResults = new ArrayList<>();

        Set<String> availableCharsetsSet = Charset.availableCharsets().keySet();

        List<String> availableCharsets = Arrays.asList(encodings);

        for (String encodingOut : availableCharsets) {
            for (String encodingIn : availableCharsets) {
                try {
                    byte[] bytes = input.getBytes(Charset.forName(encodingOut));

                    String decodedString = new String(bytes, Charset.forName(encodingIn));

                    if (isValid(decodedString)) {
                        decodedResults.add(new RecoveryDTO(encodingOut, encodingIn, decodedString));
                    }
                } catch (Exception e) {
                    // 忽略解码异常，尝试下一种组合
                }
            }
        }
        return decodedResults;
    }

    /**
     * <p>验证字符串中是否包含有效的中文字符。</p>
     *
     * @param str 待验证的字符串
     * @return 如果包含中文字符，则返回 {@code true}，否则返回 {@code false}
     */
    private static boolean isValid(String str) {
        return CHINESE_PATTERN.matcher(str).find();
    }

    /**
     * <p>统计给定恢复结果字符串中包含的中文字符数量。</p>
     *
     * @param str 恢复结果的数据传输对象 {@link RecoveryDTO}
     * @return 字符串中中文字符的总数
     */
    public static int countChineseCharacters(RecoveryDTO str) {
        Matcher matcher = CHINESE_PATTERN.matcher(str.getText());
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * <p>根据恢复结果中包含的中文字符数量进行降序排序。</p>
     *
     * @param results 恢复结果的列表
     * @return 排序后的结果列表，中文字符数量多的排在前面
     */
    public static List<RecoveryDTO> sortByChineseCount(List<RecoveryDTO> results) {
        results.sort((s1, s2) -> countChineseCharacters(s2) - countChineseCharacters(s1));
        return results;
    }

    /**
     * <h2>乱码恢复结果的数据传输对象 (Recovery DTO)</h2>
     * <p>封装了在尝试恢复乱码过程中的原始编码、目标编码及恢复后的文本内容。</p>
     */
    public static class RecoveryDTO implements Serializable {
        
        /**
         * <p>将乱码字符串转换为字节数组时使用的字符集编码。</p>
         */
        private String encode;

        /**
         * <p>将字节数组转换为正常字符串时使用的字符集解码。</p>
         */
        private String decode;

        /**
         * <p>通过编解码恢复后的文本内容。</p>
         */
        private String text;

        /**
         * <p>默认无参构造函数。</p>
         */
        public RecoveryDTO() {
        }

        /**
         * <p>全参数构造函数。</p>
         *
         * @param encode 编码字符集名称
         * @param decode 解码字符集名称
         * @param text   恢复后的文本内容
         */
        public RecoveryDTO(String encode, String decode, String text) {
            this.encode = encode;
            this.decode = decode;
            this.text = text;
        }

        /**
         * <p>获取编码字符集名称。</p>
         *
         * @return 编码字符集名称
         */
        public String getEncode() {
            return encode;
        }

        /**
         * <p>设置编码字符集名称。</p>
         *
         * @param encode 编码字符集名称
         */
        public void setEncode(String encode) {
            this.encode = encode;
        }

        /**
         * <p>获取解码字符集名称。</p>
         *
         * @return 解码字符集名称
         */
        public String getDecode() {
            return decode;
        }

        /**
         * <p>设置解码字符集名称。</p>
         *
         * @param decode 解码字符集名称
         */
        public void setDecode(String decode) {
            this.decode = decode;
        }

        /**
         * <p>获取恢复后的文本内容。</p>
         *
         * @return 文本内容
         */
        public String getText() {
            return text;
        }

        /**
         * <p>设置恢复后的文本内容。</p>
         *
         * @param text 文本内容
         */
        public void setText(String text) {
            this.text = text;
        }

        /**
         * <p>返回恢复结果对象的字符串表示形式。</p>
         *
         * @return 包含编解码信息及文本内容的字符串
         */
        @Override
        public String toString() {
            return "RecoveryDTO{" +
                    "encode='" + encode + '\'' +
                    ", decode='" + decode + '\'' +
                    ", text='" + text + '\'' +
                    '}';
        }
    }

}
