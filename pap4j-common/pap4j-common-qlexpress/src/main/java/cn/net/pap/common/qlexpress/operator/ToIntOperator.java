package cn.net.pap.common.qlexpress.operator;

import java.math.BigDecimal;

/**
 * <p>QLExpress 扩展函数：转换为 int（Number 取 intValue，数字字符串按 BigDecimal 截断取整）。</p>
 * <p>用法：TO_INT(json.numStr) 或 TO_INT(json.badValue, 0)</p>
 */
public class ToIntOperator extends AbstractToOperator {

    @Override
    protected String name() {
        return "TO_INT";
    }

    @Override
    protected Object convert(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            return new BigDecimal(str.trim()).intValue();
        }
        throw new IllegalArgumentException("TO_INT requires a number or numeric string, but got: " + value);
    }
}
