package cn.net.pap.common.datastructure.number;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * 数字 工具类
 */
public class NumberUtil {

    /**
     * 数据集合范围格式化
     * 输入 1-5,7-9,12  输出  [1,2,3,4,5,7,8,9,12]
     * @param numbers
     * @return
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
     * 数据集合范围格式化
     * 输入 [1,2,3,4,5,7,8,9,12] 输出 1-5,7-9,12
     *
     * @param numbers
     * @return
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
     * 最大子串和
     * @param inputList
     * @return
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

    private static String formatRange(List<Long> range) {
        if (range.size() == 1) {
            return range.get(0).toString();
        } else {
            return range.get(0) + "-" + range.get(range.size() - 1);
        }
    }

}
