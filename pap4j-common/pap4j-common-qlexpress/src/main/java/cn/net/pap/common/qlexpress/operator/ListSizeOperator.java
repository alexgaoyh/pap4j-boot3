package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.lang.reflect.Array;
import java.util.Collection;

/**
 * <p>QLExpress 扩展函数：获取列表或数组的大小。</p>
 * <p>用法：LIST_SIZE(json.items)</p>
 */
public class ListSizeOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("LIST_SIZE 需要一个参数：集合对象");
        }

        Object obj = parameters.get(0).get();
        if (obj == null) {
            return 0;
        }

        if (obj instanceof Collection<?> col) {
            return col.size();
        } else if (obj.getClass().isArray()) {
            return Array.getLength(obj);
        }

        return 1;
    }
}
