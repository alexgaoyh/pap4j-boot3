package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * <p>QLExpress 扩展函数：将列表或数组元素连接成字符串。</p>
 * <p>用法：LIST_JOIN(json.tags, ',')</p>
 */
public class ListJoinOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() < 1) {
            throw new IllegalArgumentException("LIST_JOIN 需要至少一个参数：集合对象");
        }

        Object collection = parameters.get(0).get();
        Object delimiter = parameters.size() > 1 ? parameters.get(1).get() : ",";

        if (collection == null) {
            return null;
        }

        String sep = delimiter == null ? "" : delimiter.toString();

        if (collection instanceof Collection<?> col) {
            return col.stream()
                    .map(item -> item == null ? "" : item.toString())
                    .collect(Collectors.joining(sep));
        } else if (collection.getClass().isArray()) {
            return IntStream.range(0, Array.getLength(collection))
                    .mapToObj(i -> Array.get(collection, i))
                    .map(item -> item == null ? "" : item.toString())
                    .collect(Collectors.joining(sep));
        } else if (collection instanceof Iterable<?> it) {
            return StreamSupport.stream(it.spliterator(), false)
                    .map(item -> item == null ? "" : item.toString())
                    .collect(Collectors.joining(sep));
        }

        return collection.toString();
    }
}
