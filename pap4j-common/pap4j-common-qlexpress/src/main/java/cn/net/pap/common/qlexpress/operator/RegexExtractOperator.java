package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>QLExpress 扩展函数：正则提取首次匹配（默认返回整个匹配，可选捕获组号）。</p>
 * <p>无匹配时返回 {@code null}。</p>
 * <p>用法：REGEX_EXTRACT(json.email, '(\w+)@(\w+\.\w+)', 1)</p>
 */
public class RegexExtractOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() < 2 || parameters.size() > 3) {
            throw new IllegalArgumentException("REGEX_EXTRACT 需要 2~3 个参数：值, 正则[, 分组号]");
        }

        Object value = parameters.get(0).get();
        Object pattern = parameters.get(1).get();
        int group = parameters.size() == 3 ? ((Number) parameters.get(2).get()).intValue() : 0;

        if (value == null || pattern == null) {
            return null;
        }

        Matcher matcher = Pattern.compile(pattern.toString()).matcher(value.toString());
        if (group < 0 || group > matcher.groupCount()) {
            throw new IllegalArgumentException(
                    "REGEX_EXTRACT 分组号越界: " + group + ", 最大分组号: " + matcher.groupCount());
        }
        return matcher.find() ? matcher.group(group) : null;
    }
}
