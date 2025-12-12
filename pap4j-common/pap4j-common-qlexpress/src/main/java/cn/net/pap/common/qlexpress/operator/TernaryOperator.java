package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 三元表达式
 */
public class TernaryOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 3) {
            throw new IllegalArgumentException("TERNARY 需要三个参数：条件, 真值, 假值");
        }

        Object condition = parameters.get(0).get();
        Object trueValue = parameters.get(1).get();
        Object falseValue = parameters.get(2).get();

        // Excel 风格的逻辑：只要 condition 为 Boolean 且为 true，返回 trueValue，否则返回 falseValue
        boolean cond = false;
        if (condition instanceof Boolean) {
            cond = (Boolean) condition;
        } else if (condition instanceof Number) {
            cond = ((Number) condition).doubleValue() != 0;
        } else if (condition != null) {
            cond = true; // 非 null 的其他对象都算 true
        }

        return cond ? trueValue : falseValue;
    }
}