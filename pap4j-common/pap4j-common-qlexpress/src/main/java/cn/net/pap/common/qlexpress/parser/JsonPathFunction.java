package cn.net.pap.common.qlexpress.parser;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import com.jayway.jsonpath.JsonPath;

/**
 * <p>QLExpress 扩展函数：使用 JsonPath 提取数据。</p>
 * <p>用法：JSON_PATH(json, '$.path.to.field')</p>
 */
public class JsonPathFunction implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("JSON_PATH 需要两个参数：JSON对象和路径字符串");
        }

        Object json = parameters.get(0).get();
        Object path = parameters.get(1).get();

        if (json == null || path == null) {
            return null;
        }

        return JsonPath.read(json, path.toString());
    }
}
