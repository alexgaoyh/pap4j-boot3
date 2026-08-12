package cn.net.pap.common.qlexpress.operator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;

/**
 * <p>日期算子（FORMAT_DATE / DATE_ADD / DATE_DIFF）内部共享的解析与渲染工具。</p>
 * <p>解析输入：LocalDateTime / LocalDate / epoch 毫秒或秒（数字或数字字符串，绝对值 ≥ 1e11 视为毫秒）/ 常见日期字符串格式。</p>
 * <p>渲染保持输入粒度：日期字符串 → ISO 日期；日期时间字符串 → ISO 日期时间；epoch → 原单位数字。避免输出 Java 对象污染 JSON 序列化。</p>
 * <p>时区：epoch 转本地时间使用服务器默认时区（部署内恒定）；本工具不读取当前时间（不引入 NOW()），保证同输入同输出。</p>
 * <p>包内私有实现细节，不构成公共 API。</p>
 */
final class DateSupport {

    /** epoch 值判定为毫秒而非秒的阈值（绝对值 ≥ 1e11 视为毫秒）。 */
    private static final long EPOCH_MILLIS_THRESHOLD = 100_000_000_000L;

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    /** 支持的字符串日期格式（按解析优先级）。 */
    private static final List<FormatSpec> FORMATS = List.of(
            new FormatSpec(DateTimeFormatter.ISO_LOCAL_DATE_TIME, Granularity.DATETIME),
            new FormatSpec(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), Granularity.DATETIME),
            new FormatSpec(DateTimeFormatter.ofPattern("yyyy-MM-dd"), Granularity.DATE),
            new FormatSpec(DateTimeFormatter.ofPattern("yyyy/MM/dd"), Granularity.DATE),
            new FormatSpec(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"), Granularity.DATETIME)
    );

    private DateSupport() {
    }

    /** 日期粒度，决定渲染方式。 */
    enum Granularity {
        DATE, DATETIME, MILLIS, SECONDS
    }

    /** 解析结果：规范化日期时间 + 输入粒度。 */
    record ParsedDate(LocalDateTime dateTime, Granularity granularity) {
    }

    /** 字符串格式与对应粒度。 */
    private record FormatSpec(DateTimeFormatter formatter, Granularity granularity) {
    }

    static ParsedDate parse(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return new ParsedDate(dateTime, Granularity.DATETIME);
        }
        if (value instanceof LocalDate date) {
            return new ParsedDate(date.atStartOfDay(), Granularity.DATE);
        }
        if (value instanceof Number number) {
            return parseEpoch(number.longValue());
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            try {
                return parseEpoch(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 非 epoch 数字字符串，继续按日期格式解析
            }
            for (FormatSpec spec : FORMATS) {
                try {
                    TemporalAccessor parsed = spec.formatter().parseBest(trimmed, LocalDateTime::from, LocalDate::from);
                    LocalDateTime dateTime = parsed instanceof LocalDate date
                            ? date.atStartOfDay()
                            : LocalDateTime.from(parsed);
                    return new ParsedDate(dateTime, spec.granularity());
                } catch (DateTimeParseException e) {
                    // 尝试下一种格式
                }
            }
            throw new IllegalArgumentException("DATE_* cannot parse date: " + str);
        }
        throw new IllegalArgumentException("DATE_* requires a date string or epoch, but got: " + value);
    }

    private static ParsedDate parseEpoch(long epoch) {
        if (Math.abs(epoch) >= EPOCH_MILLIS_THRESHOLD) {
            return new ParsedDate(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), SYSTEM_ZONE), Granularity.MILLIS);
        }
        return new ParsedDate(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), SYSTEM_ZONE), Granularity.SECONDS);
    }

    /** 按输入粒度渲染结果，保证输出类型与输入一致（字符串 / 数字）。 */
    static Object render(ParsedDate parsed) {
        return switch (parsed.granularity()) {
            case DATE -> parsed.dateTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            case DATETIME -> parsed.dateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case MILLIS -> parsed.dateTime().atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
            case SECONDS -> parsed.dateTime().atZone(SYSTEM_ZONE).toInstant().getEpochSecond();
        };
    }

    /** 时间单位字符串 → ChronoUnit；'m' 指分钟，月必须写全 'months'。 */
    static ChronoUnit unitToChrono(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "days", "day", "d" -> ChronoUnit.DAYS;
            case "hours", "hour", "h" -> ChronoUnit.HOURS;
            case "minutes", "minute", "m" -> ChronoUnit.MINUTES;
            case "seconds", "second", "s" -> ChronoUnit.SECONDS;
            case "weeks", "week", "w" -> ChronoUnit.WEEKS;
            case "months", "month" -> ChronoUnit.MONTHS;
            case "years", "year" -> ChronoUnit.YEARS;
            default -> throw new IllegalArgumentException("DATE_* unsupported time unit: " + unit);
        };
    }
}
