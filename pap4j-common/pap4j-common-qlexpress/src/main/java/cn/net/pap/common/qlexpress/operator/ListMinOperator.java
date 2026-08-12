package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * <p>QLExpress 扩展函数：返回列表中的最小值。</p>
 * <p>元素须互为 {@code Comparable}（数字或字符串均可）；null 元素跳过；空列表返回 {@code null}。</p>
 * <p>返回值为原元素类型，不做数值归一化。</p>
 * <p>用法：LIST_MIN(json.prices) 或 LIST_MIN(JSON_PATH(json, '$.items[*].price'))</p>
 */
public class ListMinOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("LIST_MIN 需要一个参数：集合");
        }

        Object input = parameters.get(0).get();
        if (input == null) {
            return null;
        }

        Object min = null;
        for (Object element : ListSupport.iterableOf(input)) {
            if (element == null) {
                continue;
            }
            if (min == null || ListSupport.compare(element, min) < 0) {
                min = element;
            }
        }
        return min;
    }
}
