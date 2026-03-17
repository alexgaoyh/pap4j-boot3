package cn.net.pap.common.datastructure.number;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * <p><strong>NumberUtil</strong> 提供了用于数值操作的实用方法。</p>
 *
 * <p>它包含用于格式化数字范围以及查找最大子数组和的函数。</p>
 *
 * <ul>
 *     <li>字符串到范围的展开。</li>
 *     <li>列表到紧凑字符串范围的转换。</li>
 *     <li>最大连续子数组和计算。</li>
 * </ul>
 */
public class NumberUtil {

    /**
     * <p>解析并将格式化的数字范围字符串展开为数字列表。</p>
     *
     * <p>示例：输入 <strong>"1-5,7-9,12"</strong> 输出 <strong>[1,2,3,4,5,7,8,9,12]</strong></p>
     *
     * @param numbers 表示范围的格式化字符串。
     * @return 长整型数字的 {@link List}。
     */
    public static List<Long> formatRanges(String numbers) {
        // 解析范围字符串
        List<String> ranges = Arrays.asList(numbers.split(","));
        List<Long> longRanges = ranges.stream()
                .flatMap(range -> {
                    if (range.contains("-")) {
                        String[] parts = range.split("-");
                        Long start = Long.parseLong(parts[0]);
                        Long end = Long.parseLong(parts[1]);
                        return LongStream.rangeClosed(start, end).boxed();
                    } else {
                        return Stream.of(Long.parseLong(range));
                    }
                })
                .collect(Collectors.toList());
        return longRanges;
    }

    /**
     * <p>将数字列表压缩为表示范围的格式化字符串。</p>
     *
     * <p>示例：输入 <strong>[1,2,3,4,5,7,8,9,12]</strong> 输出 <strong>"1-5,7-9,12"</strong></p>
     *
     * @param numbers 要格式化的数字列表。
     * @return 包含分组后连续范围的字符串。
     */
    public static String formatRanges(List<Long> numbers) {
        // 对入参先排序一下.
        Collections.sort(numbers);

        if (numbers == null || numbers.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        List<Long> range = new ArrayList<>();

        for (int i = 0; i < numbers.size(); i++) {
            range.add(numbers.get(i));

            // 是否连续
            if (i + 1 < numbers.size() && numbers.get(i) + 1 != numbers.get(i + 1)) {
                result.append(formatRange(range)).append(",");
                range.clear();
            }
        }

        // 处理结尾
        if (!range.isEmpty()) {
            result.append(formatRange(range));
        }

        return result.toString();
    }

    /**
     * <p>计算最大连续子串（子数组）和。</p>
     *
     * <p>此方法使用 Kadane 算法迭代地查找连续元素的最大可能和。</p>
     *
     * @param inputList 一个整数数组。
     * @return 找到的最大和。
     */
    public static Integer maxSubstringSum(Integer[] inputList) {
        Integer[] dp = new Integer[inputList.length];
        dp[0] = inputList[0];
        int max = Integer.MIN_VALUE;
        for (int i = 1; i < inputList.length; i++) {
            dp[i] = Math.max(inputList[i], dp[i - 1] + inputList[i]);
            if(dp[i] > max) {
                max = dp[i];
            }
        }
        return max;
    }

    /**
     * <p>将较小的连续数字范围格式化为其字符串表示形式。</p>
     *
     * @param range 连续的数字块。
     * @return 字符串形式，例如 "1" 或 "1-5"。
     */
    private static String formatRange(List<Long> range) {
        if (range.size() == 1) {
            return range.get(0).toString();
        } else {
            return range.get(0) + "-" + range.get(range.size() - 1);
        }
    }

}