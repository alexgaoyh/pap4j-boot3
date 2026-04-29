package cn.net.pap.common.datastructure.cron;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * <h1>简易 Cron 表达式解析器 (Simple Cron Parser)</h1>
 * <p>该类用于解析标准的或带有秒/年字段的简易 Cron 表达式，并能计算出基于当前时间的下一次执行时间。</p>
 * <p>支持解析 5 到 7 个字段的 Cron 表达式：</p>
 * <ul>
 *     <li>5 个字段: {@code [分] [时] [日] [月] [周]}</li>
 *     <li>6 个字段: {@code [秒] [分] [时] [日] [月] [周]}</li>
 *     <li>7 个字段: {@code [秒] [分] [时] [日] [月] [周] [年]}</li>
 * </ul>
 * <p>支持的特殊字符包括 {@code *}, {@code ?}, {@code ,}, {@code -}, {@code /}, {@code L} 等。</p>
 *
 * @author alexgaoyh
 */
public class SimpleCronParser {

    // Cron 字段常量索引
    private static final int SECOND = 0;
    private static final int MINUTE = 1;
    private static final int HOUR = 2;
    private static final int DAY_OF_MONTH = 3;
    private static final int MONTH = 4;
    private static final int DAY_OF_WEEK = 5;
    private static final int YEAR = 6;

    /** <p>解析后的 Cron 各字段数组</p> */
    private String[] cronParts;
    /** <p>当前参考时间</p> */
    private LocalDateTime currentTime;
    /** <p>是否包含秒字段标志</p> */
    private boolean hasSecondField;
    /** <p>是否包含年字段标志</p> */
    private boolean hasYearField;

    /**
     * <p>根据传入的 Cron 表达式字符串构造解析器。</p>
     *
     * @param cronExpression Cron 表达式字符串
     * @throws IllegalArgumentException 如果 Cron 表达式格式不合法（字段数不是 5, 6 或 7）
     */
    public SimpleCronParser(String cronExpression) {
        this.cronParts = cronExpression.trim().split("\\s+");

        if (cronParts.length == 5) {
            // 标准 cron 表达式（无秒、无年），补齐秒和年
            this.cronParts = new String[]{"0", cronParts[0], cronParts[1], cronParts[2], cronParts[3], cronParts[4]};
            this.hasSecondField = true;
            this.hasYearField = false;
        } else if (cronParts.length == 6) {
            // 有秒但无年
            this.hasSecondField = true;
            this.hasYearField = false;
        } else if (cronParts.length == 7) {
            // 有秒且有年
            this.hasSecondField = true;
            this.hasYearField = true;
        } else {
            throw new IllegalArgumentException("Invalid cron expression format. Expected 5, 6 or 7 fields.");
        }

        this.currentTime = LocalDateTime.now();
    }


    /**
     * <p>解析并计算当前时间之后的下一次触发时间。</p>
     * <p>该方法会截断到秒级精度，并不断自增时间直至满足 Cron 表达式。</p>
     *
     * @return 匹配 Cron 表达式的下一个 {@link LocalDateTime}
     */
    public LocalDateTime getNextExecutionTime() {
        LocalDateTime nextTime = currentTime.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);

        // 设置一个安全阈值：比如最多查找未来的 10 年
        LocalDateTime limitTime = nextTime.plusYears(10);

        while (true) {
            if (nextTime.isAfter(limitTime)) {
                // 超过阈值仍未找到，说明该 Cron 表达式可能在未来永远不会执行
                throw new IllegalStateException("无法找到下一次执行时间，Cron 表达式可能已过期或逻辑不可达");
            }

            if (!matches(nextTime)) {
                nextTime = increment(nextTime);
                continue;
            }
            break;
        }

        return nextTime;
    }

    /**
     * <p>检查给定的时间是否完全匹配该 Cron 表达式的所有字段。</p>
     *
     * @param time 待检查的时间
     * @return 如果完全匹配返回 {@code true}，否则返回 {@code false}
     */
    private boolean matches(LocalDateTime time) {
        // 处理秒字段（如果有）
        if (hasSecondField && !matchesField(time.getSecond(), cronParts[SECOND])) {
            return false;
        }

        // 分钟字段索引取决于是否有秒字段
        int minuteIndex = hasSecondField ? MINUTE : SECOND;
        if (!matchesField(time.getMinute(), cronParts[minuteIndex])) {
            return false;
        }

        // 小时字段索引
        int hourIndex = hasSecondField ? HOUR : MINUTE;
        if (!matchesField(time.getHour(), cronParts[hourIndex])) {
            return false;
        }

        // 日期字段索引
        int dayIndex = hasSecondField ? DAY_OF_MONTH : MONTH - 1;
        if (!matchesDayOfMonth(time, dayIndex)) {
            return false;
        }

        // 月份字段索引
        int monthIndex = hasSecondField ? MONTH : MONTH;
        if (!matchesField(time.getMonthValue(), cronParts[monthIndex])) {
            return false;
        }

        // 星期字段索引
        int weekIndex = hasSecondField ? DAY_OF_WEEK : DAY_OF_WEEK - 1;
        if (!matchesDayOfWeek(time, weekIndex)) {
            return false;
        }

        // 年份字段（如果有）
        if (hasYearField && !matchesField(time.getYear(), cronParts[YEAR])) {
            return false;
        }

        return true;
    }

    /**
     * <p>特殊处理“月份中的天”字段，支持 {@code L} 字符。</p>
     *
     * @param time 待检查时间
     * @param dayFieldIndex 日期字段在数组中的索引
     * @return 是否匹配该日期字段
     */
    private boolean matchesDayOfMonth(LocalDateTime time, int dayFieldIndex) {
        String dayField = cronParts[dayFieldIndex];
        if (dayField.equals("?") || dayField.equals("*")) {
            return true;
        }
        if (dayField.equals("L")) {
            return time.getDayOfMonth() == time.toLocalDate().lengthOfMonth();
        }
        return matchesField(time.getDayOfMonth(), dayField);
    }

    /**
     * <p>特殊处理“星期几”字段，支持 {@code L} 后缀表示最后一周的星期几。</p>
     *
     * @param time 待检查时间
     * @param weekFieldIndex 星期字段在数组中的索引
     * @return 是否匹配星期字段
     */
    private boolean matchesDayOfWeek(LocalDateTime time, int weekFieldIndex) {
        String dayField = cronParts[weekFieldIndex];
        if (dayField.equals("?") || dayField.equals("*")) {
            return true;
        }

        // 转换Java的DayOfWeek(1=Monday,7=Sunday)到Cron的(0=Sunday,6=Saturday)
        int cronDayOfWeek = (time.getDayOfWeek().getValue() % 7);

        // 处理"L"表示最后一天的情况
        if (dayField.equals("L")) {
            return time.getDayOfMonth() == time.toLocalDate().lengthOfMonth();
        }

        // 处理"6L"表示最后一个星期五的情况
        if (dayField.endsWith("L")) {
            try {
                int dayNum = Integer.parseInt(dayField.substring(0, dayField.length() - 1));
                // 检查是否是当月的最后一个指定的星期几
                return isLastWeekdayOfMonth(time, dayNum) && (cronDayOfWeek == dayNum);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid day of week field: " + dayField);
            }
        }

        return matchesField(cronDayOfWeek, dayField);
    }

    /**
     * <p>匹配单个字段的通用方法，处理包含逗号 {@code ,} 的列表表达式。</p>
     *
     * @param value 实际时间提取的数值
     * @param field 表达式字段字符串
     * @return 是否匹配
     */
    private boolean matchesField(int value, String field) {
        if (field.equals("*") || field.equals("?")) {
            return true;
        }

        // 处理逗号分隔的列表
        if (field.contains(",")) {
            for (String part : field.split(",")) {
                if (matchesSingleField(value, part)) {
                    return true;
                }
            }
            return false;
        }

        return matchesSingleField(value, field);
    }

    /**
     * <p>匹配单个没有逗号分隔的基本字段表达式。</p>
     * <p>支持范围 {@code -}、步长 {@code /} 及具体数字匹配。</p>
     *
     * @param value 实际时间提取的数值
     * @param field 基本字段字符串
     * @return 是否匹配
     */
    private boolean matchesSingleField(int value, String field) {
        // 如果字段包含字母（如"L"），不尝试解析为数字
        if (!field.matches("^[0-9*/,-]+$")) {
            return false;
        }

        // 处理步长表达式 */5
        if (field.startsWith("*/")) {
            int step = Integer.parseInt(field.substring(2));
            return value % step == 0;
        }

        // 处理范围表达式 1-5
        if (field.contains("-")) {
            String[] range = field.split("-");
            int min = Integer.parseInt(range[0]);
            int max = Integer.parseInt(range[1]);
            return value >= min && value <= max;
        }

        // 直接匹配数字
        try {
            return value == Integer.parseInt(field);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * <p>检查给定的日期是否是当月中最后一个符合指定的星期几。</p>
     *
     * @param time 待检查的时间
     * @param targetDayOfWeek 目标的星期数值（0-6 对应周日到周六）
     * @return 是否为该月最后一个目标星期几
     */
    private boolean isLastWeekdayOfMonth(LocalDateTime time, int targetDayOfWeek) {
        LocalDate date = time.toLocalDate();
        LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());

        // 找到最后一个指定的星期几
        LocalDate lastTargetDay = lastDay;
        while (lastTargetDay.getDayOfWeek().getValue() % 7 != targetDayOfWeek) {
            lastTargetDay = lastTargetDay.minusDays(1);
        }

        return date.getDayOfMonth() == lastTargetDay.getDayOfMonth();
    }

    /**
     * <p>每次递增一秒，进行下一轮的时间匹配。</p>
     *
     * @param time 当前尝试匹配的时间
     * @return 递增 1 秒后的时间
     */
    private LocalDateTime increment(LocalDateTime time) {
        return time.plusSeconds(1);
    }

    /**
     * <p>在控制台打印解析好的 Cron 表达式的各部分详细信息。</p>
     */
    public void printCronDetails() {
        System.out.println("Cron表达式解析结果:");

        int index = 0;
        if (hasSecondField) {
            System.out.println("秒: " + cronParts[index++]);
        }

        System.out.println("分钟: " + cronParts[index++]);
        System.out.println("小时: " + cronParts[index++]);
        System.out.println("日: " + cronParts[index++]);
        System.out.println("月: " + cronParts[index++]);
        System.out.println("周几: " + cronParts[index++]);

        if (hasYearField) {
            System.out.println("年: " + cronParts[index]);
        }
    }

    /**
     * <p>设置计算下一次执行时间的初始参照时间。</p>
     *
     * @param currentTime 参考的当前时间
     */
    public void setCurrentTime(LocalDateTime currentTime) {
        this.currentTime = currentTime;
    }
}