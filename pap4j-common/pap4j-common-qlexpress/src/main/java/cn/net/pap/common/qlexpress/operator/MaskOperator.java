package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * <p>QLExpress 扩展函数：声明式脱敏，保留前 N 位与后 M 位，中间用掩码字符填充。</p>
 * <p>当 前缀保留 + 后缀保留 ≥ 字符串长度 时返回原值（无法安全掩码）；值为 null 时返回 null。</p>
 * <p>用法：MASK(json.phone, 3, 4) 或 MASK(json.idCard, 6, 4, '#')</p>
 */
public class MaskOperator implements CustomFunction {

    private static final char DEFAULT_MASK_CHAR = '*';

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() < 3 || parameters.size() > 4) {
            throw new IllegalArgumentException("MASK 需要 3~4 个参数：值, 前缀保留位数, 后缀保留位数[, 掩码字符]");
        }

        Object value = parameters.get(0).get();
        int prefixKeep = ((Number) parameters.get(1).get()).intValue();
        int suffixKeep = ((Number) parameters.get(2).get()).intValue();
        if (prefixKeep < 0 || suffixKeep < 0) {
            throw new IllegalArgumentException("MASK 的前后缀保留位数不能为负");
        }

        char maskChar = DEFAULT_MASK_CHAR;
        if (parameters.size() == 4 && parameters.get(3).get() != null) {
            String custom = parameters.get(3).get().toString();
            if (!custom.isEmpty()) {
                maskChar = custom.charAt(0);
            }
        }

        if (value == null) {
            return null;
        }

        String source = value.toString();
        if (prefixKeep + suffixKeep >= source.length()) {
            return source;
        }
        int maskLen = source.length() - prefixKeep - suffixKeep;
        return source.substring(0, prefixKeep)
                + String.valueOf(maskChar).repeat(maskLen)
                + source.substring(source.length() - suffixKeep);
    }
}
