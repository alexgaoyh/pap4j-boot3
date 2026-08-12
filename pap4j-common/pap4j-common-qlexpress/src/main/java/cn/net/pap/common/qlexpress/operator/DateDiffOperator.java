package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.time.LocalDateTime;

/**
 * <p>QLExpress 扩展函数：计算两个日期的差值（结束 - 开始），单位 days/hours/minutes/seconds/weeks/months/years（默认 days），返回整数。</p>
 * <p>参数顺序与 SQL DATEDIFF 一致；任一值为 null 返回 null。</p>
 * <p>用法：DATE_DIFF(json.endDate, json.startDate, 'days')</p>
 */
public class DateDiffOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() < 2 || parameters.size() > 3) {
            throw new IllegalArgumentException("DATE_DIFF 需要 2~3 个参数：结束日期, 开始日期[, 单位]");
        }

        Object end = parameters.get(0).get();
        Object start = parameters.get(1).get();
        if (end == null || start == null) {
            return null;
        }
        String unit = parameters.size() == 3 && parameters.get(2).get() != null
                ? parameters.get(2).get().toString() : "days";

        LocalDateTime endDateTime = DateSupport.parse(end).dateTime();
        LocalDateTime startDateTime = DateSupport.parse(start).dateTime();
        return DateSupport.unitToChrono(unit).between(startDateTime, endDateTime);
    }
}
