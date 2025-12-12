package cn.net.pap.common.qlexpress;

import cn.net.pap.common.qlexpress.operator.DivideOperator;
import cn.net.pap.common.qlexpress.operator.IsBlankOperator;
import cn.net.pap.common.qlexpress.operator.TernaryOperator;
import cn.net.pap.common.qlexpress.operator.UpperOperator;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;

public class Express4RunnerUtil {

    public static final Express4Runner runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);

    static {
        runner.addFunction("DIV2", new DivideOperator(2));
        runner.addFunction("ISBLANK", new IsBlankOperator());
        runner.addFunction("UPPER", new UpperOperator());
        runner.addFunction("TERNARY", new TernaryOperator());
    }

}
