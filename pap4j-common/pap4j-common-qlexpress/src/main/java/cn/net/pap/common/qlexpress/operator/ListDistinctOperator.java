package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * <p>QLExpress 扩展函数：列表去重（保留首次出现顺序，null 元素最多保留一个）。</p>
 * <p>用法：LIST_DISTINCT(json.tags)</p>
 */
public class ListDistinctOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("LIST_DISTINCT 需要一个参数：集合");
        }

        Object input = parameters.get(0).get();
        if (input == null) {
            return Collections.emptyList();
        }

        LinkedHashSet<Object> seen = new LinkedHashSet<>();
        for (Object element : ListSupport.iterableOf(input)) {
            seen.add(element);
        }
        return new ArrayList<>(seen);
    }
}
