package cn.net.pap.common.qlexpress.operator;

import java.math.BigDecimal;

/**
 * <p>QLExpress 扩展函数：转换为 BigDecimal（数字字符串按字符串构造，避免 double 精度损失）。</p>
 * <p>用法：TO_DECIMAL(json.amount) 或 TO_DECIMAL(json.badValue, 0)</p>
 */
public class ToDecimalOperator extends AbstractToOperator {

    @Override
    protected String name() {
        return "TO_DECIMAL";
    }

    @Override
    protected Object convert(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String str) {
            return new BigDecimal(str.trim());
        }
        throw new IllegalArgumentException("TO_DECIMAL requires a number or numeric string, but got: " + value);
    }
}
