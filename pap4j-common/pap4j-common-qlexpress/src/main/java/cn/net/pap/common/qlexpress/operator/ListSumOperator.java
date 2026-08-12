package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.math.BigDecimal;

/**
 * <p>QLExpress 扩展函数：数值列表求和。</p>
 * <p>null 元素跳过、数字字符串（如 {@code "10"}）强转参与计算，非数字元素抛错；空列表返回 {@code 0}。</p>
 * <p>用法：LIST_SUM(json.items) 或 LIST_SUM(JSON_PATH(json, '$.items[*].price'))</p>
 */
public class ListSumOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("LIST_SUM 需要一个参数：数值集合");
        }

        Object input = parameters.get(0).get();
        if (input == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (Object element : ListSupport.iterableOf(input)) {
            BigDecimal value = ListSupport.toBigDecimal(element);
            if (value != null) {
                sum = sum.add(value);
            }
        }
        return sum;
    }
}
