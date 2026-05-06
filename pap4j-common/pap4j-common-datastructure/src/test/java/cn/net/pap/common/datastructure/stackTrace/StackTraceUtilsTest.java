package cn.net.pap.common.datastructure.stackTrace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StackTraceUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(StackTraceUtilsTest.class);

    // @Test
    @EnabledIfEnvironmentVariable(named = "RUN_TESTS", matches = "true")
    public void printStackTraceTest() {
        try {
            int i = 1/0;
        } catch (Exception e) {
            // 默认规则过滤
            log.info("{}", StackTraceUtils.getCoreStackTrace(e));

            // 完全自定义过滤逻辑
            log.info("{}", StackTraceUtils.getCoreStackTrace(e, element ->
                    element.getClassName().contains("Test")
            ));

            // 默认规则过滤
            log.info("{}", StackTraceUtils.getCoreStackTrace(StackTraceUtils.getFullStackTrace(e)));

            // 完全自定义过滤逻辑
            log.info("{}", StackTraceUtils.getCoreStackTrace(StackTraceUtils.getFullStackTrace(e), element ->
                    element.getClassName().contains("Test")
            ));

            log.info("{}", StackTraceUtils.printFilteredStackTraceStackWalker(e));
        }
    }

}
