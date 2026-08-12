package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.time.format.DateTimeFormatter;

/**
 * <p>QLExpress 扩展函数：将日期值按指定格式输出为字符串。</p>
 * <p>值为 null 返回 null；无法解析或格式非法时抛错（经审计暴露）。</p>
 * <p>用法：FORMAT_DATE(json.date, 'yyyy/MM/dd')</p>
 */
public class FormatDateOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("FORMAT_DATE 需要两个参数：日期值, 输出格式");
        }

        Object value = parameters.get(0).get();
        Object pattern = parameters.get(1).get();
        if (value == null) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("FORMAT_DATE 输出格式不能为 null");
        }

        return DateSupport.parse(value).dateTime().format(DateTimeFormatter.ofPattern(pattern.toString()));
    }
}
