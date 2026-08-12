package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.util.Objects;

/**
 * <p>QLExpress 扩展函数：判断列表中是否包含目标值（null 安全比对）。</p>
 * <p>用法：LIST_CONTAINS(json.tags, 'web')</p>
 */
public class ListContainsOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("LIST_CONTAINS 需要两个参数：集合, 目标值");
        }

        Object input = parameters.get(0).get();
        Object target = parameters.get(1).get();
        if (input == null) {
            return false;
        }

        for (Object element : ListSupport.iterableOf(input)) {
            if (Objects.equals(element, target)) {
                return true;
            }
        }
        return false;
    }
}
