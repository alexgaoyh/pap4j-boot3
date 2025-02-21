package cn.net.pap.logback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StaticLoggerTest {

    private static final int INSTANCE_COUNT = 9999999; // 创建大量实例以放大差异

    @Test
    public void testStaticLoggerMemoryUsage() {
        measureMemoryUsage(LoggerDefineStatic.class);
    }

    @Test
    public void testNonStaticLoggerMemoryUsage() {
        measureMemoryUsage(LoggerDefineNonStatic.class);
    }

    private <T> void measureMemoryUsage(Class<T> controllerClass) {
        System.gc();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        List<T> controllers = new ArrayList<>();
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            try {
                controllers.add(controllerClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.gc();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long memoryIncrement = finalMemory - initialMemory;

        System.out.println("Memory increment for " + controllerClass.getSimpleName() + ": " + memoryIncrement + " bytes");
    }

}
