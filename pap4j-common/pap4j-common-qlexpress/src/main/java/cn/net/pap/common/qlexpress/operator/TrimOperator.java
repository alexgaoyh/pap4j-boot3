package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 字符串去空格
 */
public class TrimOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("TRIM 只接受一个参数");
        }

        Object value = parameters.get(0).get();
        if (value == null) {
            return null;
        }

        return value.toString().trim();
    }
}
