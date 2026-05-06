package cn.net.pap.example.admin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessPoolUtilExample {

    private static final Logger log = LoggerFactory.getLogger(ProcessPoolUtilExample.class);

    /**
     * main 方法，可以通过 args 接收参数
     */
    public static void main(String[] args) {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        int result = run(args);
        log.info("TaskExample result: {}", result);
        // main 方法默认没有返回值，只能通过输出或异常返回
    }


    /**
     * 真正逻辑方法，返回 int
     */
    public static int run(String[] args) {
        // 简单示例：把参数长度作为返回值
        if (args == null) return 0;
        int sum = 0;
        for (String arg : args) {
            try {
                sum += Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                // 非数字忽略
            }
        }
        return sum;
    }

}
