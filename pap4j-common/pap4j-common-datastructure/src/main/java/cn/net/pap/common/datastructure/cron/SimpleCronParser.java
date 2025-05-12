package cn.net.pap.common.datastructure.cron;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class SimpleCronParser {

    // Cron字段常量
    private static final int SECOND = 0;
    private static final int MINUTE = 1;
    private static final int HOUR = 2;
    private static final int DAY_OF_MONTH = 3;
    private static final int MONTH = 4;
    private static final int DAY_OF_WEEK = 5;
    private static final int YEAR = 6;

    private String[] cronParts;
    private LocalDateTime currentTime;
    private boolean hasSecondField;
    private boolean hasYearField;

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
            this.hasSecondField = true;
            this.hasYearField = true;
        } else {
            throw new IllegalArgumentException("Invalid cron expression format. Expected 5, 6 or 7 fields.");
        }

        this.currentTime = LocalDateTime.now();
    }


    // 解析并获取下一次执行时间
    public LocalDateTime getNextExecutionTime() {
        LocalDateTime nextTime = currentTime.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);

        while (true) {
            if (!matches(nextTime)) {
                nextTime = increment(nextTime);
                continue;
            }
            break;
        }

        return nextTime;
    }

    // 检查给定时间是否匹配cron表达式
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

    // 特殊处理月份中的天
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

    // 特殊处理星期几
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

    // 匹配单个字段的通用方法
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

    // 匹配单个字段的具体实现
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

    // 检查是否是当月的最后一个指定的星期几
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

    // 增加时间到下一个可能的时间点
    private LocalDateTime increment(LocalDateTime time) {
        return time.plusSeconds(1);
    }

    // 打印cron表达式的解析结果
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

    public void setCurrentTime(LocalDateTime currentTime) {
        this.currentTime = currentTime;
    }
}