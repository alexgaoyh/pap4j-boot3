package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * <p>QLExpress 扩展函数：返回列表中的最大值。</p>
 * <p>元素须互为 {@code Comparable}（数字或字符串均可，ISO 日期字符串可直接取最新）；null 元素跳过；空列表返回 {@code null}。</p>
 * <p>返回值为原元素类型，不做数值归一化。</p>
 * <p>用法：LIST_MAX(json.dates) 或 LIST_MAX(JSON_PATH(json, '$.items[*].price'))</p>
 */
public class ListMaxOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("LIST_MAX 需要一个参数：集合");
        }

        Object input = parameters.get(0).get();
        if (input == null) {
            return null;
        }

        Object max = null;
        for (Object element : ListSupport.iterableOf(input)) {
            if (element == null) {
                continue;
            }
            if (max == null || ListSupport.compare(element, max) > 0) {
                max = element;
            }
        }
        return max;
    }
}
