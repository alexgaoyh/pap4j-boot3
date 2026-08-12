package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.util.regex.Pattern;

/**
 * <p>QLExpress 扩展函数：正则格式校验，返回是否匹配。</p>
 * <p>采用 {@code find()}（子串匹配）语义；需要整串校验时，请在正则首尾加 {@code ^} / {@code $} 锚点。</p>
 * <p>值为 null 时返回 {@code false}。</p>
 * <p>用法：REGEX_TEST(json.phone, '^1[3-9]\d{9}$')</p>
 */
public class RegexTestOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("REGEX_TEST 需要两个参数：值, 正则");
        }

        Object value = parameters.get(0).get();
        Object pattern = parameters.get(1).get();
        if (value == null) {
            return false;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("REGEX_TEST 正则不能为 null");
        }

        return Pattern.compile(pattern.toString()).matcher(value.toString()).find();
    }
}
