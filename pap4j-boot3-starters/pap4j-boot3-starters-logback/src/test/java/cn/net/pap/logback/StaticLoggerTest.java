package cn.net.pap.logback;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class StaticLoggerTest {

    private static final Logger log = LoggerFactory.getLogger(StaticLoggerTest.class);

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
            log.error("Thread sleep interrupted", e);
            Thread.currentThread().interrupt();
        }

        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        List<T> controllers = new ArrayList<>();
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            try {
                controllers.add(controllerClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                log.error("Failed to create instance", e);
            }
        }

        System.gc();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            log.error("Thread sleep interrupted", e);
            Thread.currentThread().interrupt();
        }

        long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long memoryIncrement = finalMemory - initialMemory;

        log.info("Memory increment for {}: {} bytes", controllerClass.getSimpleName(), memoryIncrement);
    }

}
