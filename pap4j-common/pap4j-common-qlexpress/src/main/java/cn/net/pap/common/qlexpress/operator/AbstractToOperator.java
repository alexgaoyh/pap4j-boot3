package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * <p>类型强转算子（TO_*）共用的执行骨架：值 + 可选默认值。</p>
 * <p>语义：值为 null 时返回默认值（未给默认值则返回 null）；转换失败时返回默认值（未给默认值则抛 {@link IllegalArgumentException}，经审计暴露）。</p>
 * <p>包内私有实现细节，不构成公共 API。</p>
 */
abstract class AbstractToOperator implements CustomFunction {

    @Override
    public final Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() < 1 || parameters.size() > 2) {
            throw new IllegalArgumentException(name() + " 需要 1~2 个参数：值[, 默认值]");
        }

        Object value = parameters.get(0).get();
        boolean hasDefault = parameters.size() == 2;
        Object defaultValue = hasDefault ? parameters.get(1).get() : null;

        if (value == null) {
            return hasDefault ? defaultValue : null;
        }
        try {
            return convert(value);
        } catch (RuntimeException e) {
            if (hasDefault) {
                return defaultValue;
            }
            throw new IllegalArgumentException(name() + " failed to convert value: " + value, e);
        }
    }

    /** 算子名称，用于错误信息。 */
    protected abstract String name();

    /** 将非 null 值转换为目标类型；失败抛 {@link RuntimeException}。 */
    protected abstract Object convert(Object value);
}
