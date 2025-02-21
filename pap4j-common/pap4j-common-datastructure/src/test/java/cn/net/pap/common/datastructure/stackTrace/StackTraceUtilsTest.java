package cn.net.pap.common.datastructure.stackTrace;

import org.junit.jupiter.api.Test;

public class StackTraceUtilsTest {

    @Test
    public void printStackTraceTest() {
        try {
            int i = 1/0;
        } catch (Exception e) {
            // 默认规则过滤
            System.out.println(StackTraceUtils.getCoreStackTrace(e));

            // 完全自定义过滤逻辑
            System.out.println(StackTraceUtils.getCoreStackTrace(e, element ->
                    element.getClassName().contains("Test")
            ));

            // 默认规则过滤
            System.out.println(StackTraceUtils.getCoreStackTrace(StackTraceUtils.getFullStackTrace(e)));

            // 完全自定义过滤逻辑
            System.out.println(StackTraceUtils.getCoreStackTrace(StackTraceUtils.getFullStackTrace(e), element ->
                    element.getClassName().contains("Test")
            ));

            System.out.println(StackTraceUtils.printFilteredStackTraceStackWalker(e));
        }
    }

}
