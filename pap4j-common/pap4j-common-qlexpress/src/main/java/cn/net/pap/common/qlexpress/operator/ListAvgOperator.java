package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * <p>QLExpress 扩展函数：数值列表求算术平均。</p>
 * <p>null 元素跳过、数字字符串强转参与计算，非数字元素抛错；空列表返回 {@code null}。</p>
 * <p>结果固定保留 {@value #AVG_SCALE} 位小数（HALF_UP）。</p>
 * <p>用法：LIST_AVG(json.items) 或 LIST_AVG(JSON_PATH(json, '$.items[*].price'))</p>
 */
public class ListAvgOperator implements CustomFunction {

    /** 均值结果保留的小数位数。 */
    private static final int AVG_SCALE = 4;

    /** 均值结果的舍入模式。 */
    private static final RoundingMode AVG_ROUNDING = RoundingMode.HALF_UP;

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("LIST_AVG 需要一个参数：数值集合");
        }

        Object input = parameters.get(0).get();
        if (input == null) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (Object element : ListSupport.iterableOf(input)) {
            BigDecimal value = ListSupport.toBigDecimal(element);
            if (value != null) {
                sum = sum.add(value);
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), AVG_SCALE, AVG_ROUNDING);
    }
}
