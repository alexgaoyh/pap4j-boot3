package cn.net.pap.common.datastructure.lock;

import org.junit.jupiter.api.Test;

public class ReentrantLockConditionUtilTest {

    @Test
    public void test1() throws InterruptedException {
        ReentrantLockConditionUtil example = new ReentrantLockConditionUtil();

        // 线程1：等待
        new Thread(() -> {
            try {
                example.waiter();
            } catch (InterruptedException e) {
                e.printStackTrace();
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
