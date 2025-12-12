package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.Value;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 除法
 */
public class DivideOperator implements CustomFunction {

    private final int scale; // 保留的小数位数

    public DivideOperator(int scale) {
        this.scale = scale;
    }

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        // 检查参数数量
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("DivideOperator 需要两个参数");
        }

        Object[] params = new Object[parameters.size()];

        for (int i = 0; i < parameters.size(); i++) {
            Value v = parameters.get(i);
            Object value = v.get();
            // 判断是否是数字
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("DivideOperator 参数必须是数字");
            }
            params[i] = value;
        }

        BigDecimal dividend = new BigDecimal(params[0].toString());
        BigDecimal divisor = new BigDecimal(params[1].toString());

        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("除数不能为0");
        }

        // 执行除法，保留 scale 位小数，四舍五入
        BigDecimal result = dividend.divide(divisor, scale, RoundingMode.HALF_UP);
        return result;
    }

}