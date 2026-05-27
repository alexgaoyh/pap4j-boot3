package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 字符串截取
 */
public class SubstringOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() < 2 || parameters.size() > 3) {
            throw new IllegalArgumentException("SUBSTRING 需要 2 或 3 个参数：字符串, 起始位置, [结束位置]");
        }

        Object strObj = parameters.get(0).get();
        Object startObj = parameters.get(1).get();

        if (strObj == null) {
            return null;
        }
        String str = strObj.toString();
        int start = ((Number) startObj).intValue();

        if (parameters.size() == 3) {
            int end = ((Number) parameters.get(2).get()).intValue();
            return str.substring(start, end);
        } else {
            return str.substring(start);
        }
    }
}
