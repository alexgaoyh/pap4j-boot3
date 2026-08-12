package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.util.Map;

/**
 * <p>QLExpress 扩展函数：码值/字典翻译，按映射表查找翻译值。</p>
 * <p>键匹配优先精确 {@code get}；值为数字时回退到字符串键（JSON 数字 1 可命中字典键 {@code "1"}）。</p>
 * <p>未命中或值为 null 时返回 null（可用 COALESCE / TERNARY 兜底）。</p>
 * <p>用法：DICT_MAP(json.status, {'1':'已下单','2':'已支付'})</p>
 */
public class DictMapOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("DICT_MAP 需要两个参数：值, 映射Map");
        }

        Object value = parameters.get(0).get();
        Object mapping = parameters.get(1).get();
        if (value == null || mapping == null) {
            return null;
        }
        if (!(mapping instanceof Map<?, ?> dict)) {
            throw new IllegalArgumentException("DICT_MAP 第二个参数必须是 Map，如 {'1':'已下单','2':'已支付'}");
        }

        Object direct = dict.get(value);
        if (direct != null || dict.containsKey(value)) {
            return direct;
        }
        if (value instanceof Number number) {
            return dict.get(number.toString());
        }
        return null;
    }
}
