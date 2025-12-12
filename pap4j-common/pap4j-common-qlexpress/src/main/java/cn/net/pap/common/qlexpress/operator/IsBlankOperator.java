package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 是否为空
 */
public class IsBlankOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("ISBLANK 只接受一个参数");
        }

        Object value = parameters.get(0).get();

        return value == null || (value instanceof String && ((String) value).trim().isEmpty());
    }

}

