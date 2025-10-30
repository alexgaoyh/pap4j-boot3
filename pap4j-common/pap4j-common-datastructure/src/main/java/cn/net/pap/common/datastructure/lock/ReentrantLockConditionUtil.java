package cn.net.pap.common.datastructure.lock;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockConditionUtil {

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    private boolean isReady = false;

    /**
     * 等待的线程
     *
     * @throws InterruptedException
     */
    public void waiter() throws InterruptedException {
        lock.lock();
        try {
            System.out.println("等待线程：开始等待");

            // 当条件不满足时，调用await()等待
            while (!isReady) {
                condition.await();  // 重点：这里会释放锁并等待
            }

            System.out.println("等待线程：条件满足，继续执行");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 通知的线程
     */
    public void notifier() {
        lock.lock();
        try {
            System.out.println("通知线程：改变条件");
            isReady = true;  // 改变条件

            condition.signal();  // 重点：唤醒一个等待的线程
            System.out.println("通知线程：已发送通知");
        } finally {
            lock.unlock();
        }
    }

}
