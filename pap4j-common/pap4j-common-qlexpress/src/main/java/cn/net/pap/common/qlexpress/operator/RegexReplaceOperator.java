package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * <p>QLExpress 扩展函数：正则替换，返回替换后的字符串。</p>
 * <p>replacement 中 {@code $1} 表示第 1 个捕获组（Java {@code replaceAll} 语义）；replacement 为 null 视为删除匹配。</p>
 * <p>值为 null 时返回 {@code null}。</p>
 * <p>用法：REGEX_REPLACE(json.text, '\s+', ' ')</p>
 */
public class RegexReplaceOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 3) {
            throw new IllegalArgumentException("REGEX_REPLACE 需要三个参数：值, 正则, 替换串");
        }

        Object value = parameters.get(0).get();
        Object pattern = parameters.get(1).get();
        if (value == null) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("REGEX_REPLACE 正则不能为 null");
        }
        Object replacement = parameters.get(2).get();
        String replacementStr = replacement == null ? "" : replacement.toString();

        return value.toString().replaceAll(pattern.toString(), replacementStr);
    }
}
