package cn.net.pap.common.qlexpress.operator;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * <p>QLExpress 扩展函数：转换为布尔值。</p>
 * <p>字符串按词表识别：true/false、yes/no、y/n、t/f、是/否（ASCII 忽略大小写）；数字按非零为 true；其他字符串若可解析为数字则按非零判定。</p>
 * <p>用法：TO_BOOLEAN(json.flag) 或 TO_BOOLEAN(json.flag, false)</p>
 */
public class ToBooleanOperator extends AbstractToOperator {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ToBooleanOperator.class);

    private static final Set<String> TRUE_WORDS = Set.of("true", "yes", "y", "t", "是");
    private static final Set<String> FALSE_WORDS = Set.of("false", "no", "n", "f", "否");

    @Override
    protected String name() {
        return "TO_BOOLEAN";
    }

    @Override
    protected Object convert(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (TRUE_WORDS.contains(lower)) {
                return true;
            }
            if (FALSE_WORDS.contains(lower)) {
                return false;
            }
            try {
                return new BigDecimal(trimmed).signum() != 0;
            } catch (NumberFormatException e) {
                log.error("TO_BOOLEAN cannot parse value: {}", str, e);
                throw new IllegalArgumentException("TO_BOOLEAN cannot parse: " + str, e);
            }
        }
        throw new IllegalArgumentException("TO_BOOLEAN requires boolean/number/string, but got: " + value);
    }
}
