package cn.net.pap.common.datastructure.lock;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReentrantLockConditionUtilTest {

    private static final Logger log = LoggerFactory.getLogger(ReentrantLockConditionUtilTest.class);

    @Test
    public void test1() throws InterruptedException {
        ReentrantLockConditionUtil example = new ReentrantLockConditionUtil();

        // 线程1：等待
        new Thread(() -> {
            try {
                example.waiter();
            } catch (InterruptedException e) {
                log.error("Waiter interrupted", e);
                Thread.currentThread().interrupt();
            }
        }).start();

        // 主线程等1秒后通知
        Thread.sleep(1000);

        // 线程2：通知
        new Thread(() -> {
            example.notifier();
        }).start();

    }


}
