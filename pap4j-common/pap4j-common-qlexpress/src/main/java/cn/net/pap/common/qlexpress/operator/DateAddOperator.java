package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.time.LocalDateTime;

/**
 * <p>QLExpress 扩展函数：日期加减指定数量，单位 days/hours/minutes/seconds/weeks/months/years（默认 days）。</p>
 * <p>输出保持输入粒度：日期字符串 → ISO 日期；日期时间字符串 → ISO 日期时间；epoch → 原单位数字。</p>
 * <p>不读取当前时间（确定性）。值为 null 返回 null。</p>
 * <p>用法：DATE_ADD(json.date, 30, 'days')</p>
 */
public class DateAddOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() < 2 || parameters.size() > 3) {
            throw new IllegalArgumentException("DATE_ADD 需要 2~3 个参数：日期值, 数量[, 单位]");
        }

        Object value = parameters.get(0).get();
        Object amount = parameters.get(1).get();
        if (value == null) {
            return null;
        }
        if (!(amount instanceof Number)) {
            throw new IllegalArgumentException("DATE_ADD 数量必须是数字");
        }
        String unit = parameters.size() == 3 && parameters.get(2).get() != null
                ? parameters.get(2).get().toString() : "days";

        DateSupport.ParsedDate parsed = DateSupport.parse(value);
        LocalDateTime result = parsed.dateTime().plus(((Number) amount).longValue(), DateSupport.unitToChrono(unit));
        return DateSupport.render(new DateSupport.ParsedDate(result, parsed.granularity()));
    }
}
