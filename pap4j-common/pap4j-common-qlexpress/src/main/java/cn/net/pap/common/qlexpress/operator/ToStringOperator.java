package cn.net.pap.common.qlexpress.operator;

/**
 * <p>QLExpress 扩展函数：转换为字符串（调用 {@code toString}，不做 trim）。</p>
 * <p>用法：TO_STRING(json.amount)</p>
 */
public class ToStringOperator extends AbstractToOperator {

    @Override
    protected String name() {
        return "TO_STRING";
    }

    @Override
    protected Object convert(Object value) {
        return value.toString();
    }
}
