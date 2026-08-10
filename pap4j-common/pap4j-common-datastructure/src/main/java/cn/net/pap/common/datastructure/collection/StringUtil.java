package cn.net.pap.common.datastructure.collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <h1>字符串处理工具类 (String Utility)</h1>
 * <p>提供了一系列操作字符串的高级实用方法，支持处理特殊字符和扩展区字符（如 Emoji 表情）。</p>
 * <ul>
 *     <li>安全替换: {@link #replaceFirst(String, String, String)}</li>
 *     <li>字符点阵打印: {@link #print(String)}</li>
 *     <li>特定字符分组: {@link #groupSpecialStrings(String, List)}</li>
 *     <li>基于代码点的索引查找: {@link #indexOf2(String, String)}</li>
 *     <li>多分隔符拆分: {@link #split(String, String)}, {@link #splitAndFilter(String, String)}</li>
 * </ul>
 *
 * @author alexgaoyh
 */
public class StringUtil {

    private static final Logger log = LoggerFactory.getLogger(StringUtil.class);

    /**
     * <p>替换字符串中第一个匹配指定字面量子字符串的部分。</p>
     * <p>该方法会自动转义正则表达式的保留字符，以纯文本形式进行匹配。</p>
     *
     * @param str         原始字符串
     * @param regex       需要查找的纯文本字符串（内部会通过 {@link Pattern#quote(String)} 转换）
     * @param replacement 要替换成的新字符串
     * @return 替换后的新字符串
     */
    public static String replaceFirst(String str, String regex, String replacement) {
        return str.replaceFirst(Pattern.quote(regex), replacement);
    }

    /**
     * <p>逐字符（基于代码点）打印字符串内容。</p>
     * <p>能正确处理占用多个 {@code char} 的扩展区字符（如 Emoji 表情）。</p>
     *
     * @param str 需要打印的字符串
     */
    public static void print(String str) {
        StringBuilder sb = new StringBuilder();
        str.codePoints().forEach(cp -> sb.append(new String(Character.toChars(cp))));
        log.info("{}", sb.toString());
    }

    /**
     * <p>从输入字符串中提取和分组特殊指定的字符串，并保持原始文本的字符切分顺序。</p>
     * <p>遇到特定字符串列表中的子串，则将其作为一个整体存入结果列表，否则将每个字符单独存入。</p>
     *
     * @param input          原始输入字符串
     * @param specialStrings 特殊字符串列表，例如标记位或高亮词汇
     * @return 包含分组拆分后的字符串列表
     */
    public static List<String> groupSpecialStrings(String input, List<String> specialStrings) {
        List<String> result = new ArrayList<>();
        if (input == null || input.isEmpty()) return result;
        int[] codePoints = input.codePoints().toArray();
        int len = codePoints.length;
        for (int i = 0; i < len; ) {
            boolean found = false;
            for (String special : specialStrings) {
                int[] specialCps = special.codePoints().toArray();
                if (i + specialCps.length <= len) {
                    boolean match = true;
                    for (int j = 0; j < specialCps.length; j++) {
                        if (codePoints[i + j] != specialCps[j]) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        result.add(special);
                        i += specialCps.length;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                result.add(new String(Character.toChars(codePoints[i])));
                i++;
            }
        }
        return result;
    }

    /**
     * <p>找到字符串 A 中第一次出现字符串 B 的位置，支持处理扩展区字符。</p>
     * <p>该方法基于代码点（Code Point）进行匹配，因此能正确计算包含 Emoji 等占多个字符位时真实的字符偏移位置。</p>
     *
     * @param A 原始字符串
     * @param B 要查找的子字符串
     * @return 子字符串 B 在字符串 A 中的字符位置，如果没有找到则返回 -1
     */
    public static int indexOf2(String A, String B) {
        if (A == null || B == null) {
            return -1;
        }
        int[] cpsA = A.codePoints().toArray();
        int[] cpsB = B.codePoints().toArray();
        int lenA = cpsA.length;
        int lenB = cpsB.length;
        if (lenB == 0) {
            return 0;
        }
        for (int i = 0; i <= lenA - lenB; i++) {
            int j = 0;
            while (j < lenB && cpsA[i + j] == cpsB[j]) {
                j++;
            }
            if (j == lenB) {
                return i;
            }
        }
        return -1;
    }

    /**
     * <p>判断 {@code needle} 是否作为子串完整出现在 {@code text} 的 {@code [from, to)} 字符区间内。</p>
     * <p>与 {@link String#indexOf(String, int)} 的区别：indexOf 只能指定起始位置、会一路搜到字符串
     * 末尾；本方法多了右边界 {@code to}，搜索被限定在区间内，不会外溢到区间之后。</p>
     * <p>实现：首字符 {@code indexOf} 找候选起点 + {@code i + needle.length() &lt;= to} 确认放得进区间 +
     * {@link String#regionMatches(int, String, int, int)} 定长比较，零子串分配。典型场景：逐行/逐段
     * 扫描大文本时判断"当前行/段是否含关键字"，避免搜索串到下一行。</p>
     *
     * @param text   被搜索的字符串；{@code null} 返回 {@code false}
     * @param from   区间起点（含），会钳制到 {@code [0, text.length()]}
     * @param to     区间终点（不含），会钳制到 {@code [0, text.length()]}
     * @param needle 要查找的子串；{@code null} 返回 {@code false}，空串在非空区间内视为存在
     * @return 区间内完整出现 {@code needle} 返回 {@code true}，否则 {@code false}
     */
    public static boolean containsInRange(String text, int from, int to, String needle) {
        if (text == null || needle == null) {
            return false;
        }
        from = Math.max(0, from);
        to = Math.min(text.length(), to);
        if (from >= to) {
            return false;
        }
        if (needle.isEmpty()) {
            return true; // 空串在任意非空区间起点处都存在
        }
        char first = needle.charAt(0);
        int i = text.indexOf(first, from);
        while (i >= 0 && i < to) {                 // 首字符必须在区间内，越过 to 立即停
            if (i + needle.length() <= to          // 整个 needle 必须放得进区间
                    && text.regionMatches(i, needle, 0, needle.length())) {
                return true;
            }
            i = text.indexOf(first, i + 1);        // 首字符对但整词不对 → 找下一个首字符
        }
        return false;
    }

    /**
     * <p>使用给定的分隔符字符串中的所有字符作为分隔符来拆分输入字符串。</p>
     * <strong>示例:</strong>
     * <pre>{@code
     * String[] result = StringUtil.split("a,b;c|d", ",;|");
     * // 结果为 ["a", "b", "c", "d"]
     * }</pre>
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
     * <p>使用给定的分隔符字符串中的所有字符作为分隔符来拆分输入字符串，并过滤掉空字符串。</p>
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
     * <p>构建匹配任意指定分隔符字符的正则表达式模式。</p>
     *
     * @param delimiters 包含所有分隔符的字符串
     * @return 构建好的正则表达式字符串（自动转义特殊元字符）
     */
    private static String buildPattern(String delimiters) {
        StringBuilder pattern = new StringBuilder();
        pattern.append("[");

        delimiters.codePoints().forEach(cp -> {
            String c = new String(Character.toChars(cp));
            if (isRegexMetaCharacter(cp)) {
                pattern.append("\\");
            }
            pattern.append(c);
        });

        pattern.append("]");
        return pattern.toString();
    }

    /**
     * <p>检查指定字符是否是正则表达式的元字符。</p>
     *
     * @param cp 要检查的字符Code Point
     * @return 如果是元字符返回 {@code true}，否则返回 {@code false}
     */
    private static boolean isRegexMetaCharacter(int cp) {
        return "\\.[]{}()*+?^$|-".codePoints().anyMatch(meta -> meta == cp);
    }

}
