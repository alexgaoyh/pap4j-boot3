package cn.net.pap.common.datastructure.lock;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p><strong>ReentrantLockConditionUtil</strong> 提供了一个使用 {@link ReentrantLock} 和 {@link Condition} 进行线程同步的简单演示。</p>
 *
 * <ul>
 *     <li>演示了一个等待线程暂停执行，直到满足某个条件。</li>
 *     <li>演示了一个通知线程发出信号，以唤醒等待线程恢复执行。</li>
 * </ul>
 * 
 * <p>示例场景：</p>
 * <pre>{@code
 * ReentrantLockConditionUtil util = new ReentrantLockConditionUtil();
 * // 线程 1 调用 util.waiter()
 * // 线程 2 调用 util.notifier()
 * }</pre>
 */
public class ReentrantLockConditionUtil {

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    private boolean isReady = false;

    /**
     * <p>充当等待线程。</p>
     * 
     * <p>它获取锁并在 {@link Condition} 上等待，直到标志 <strong>isReady</strong> 变为 true。在等待期间，锁会被隐式释放。</p>
     *
     * @throws InterruptedException 如果线程在等待时被中断。
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
     * <p>充当通知线程。</p>
     * 
     * <p>它获取锁，更改状态标志，并使用 {@link Condition#signal()} 方法向等待线程发送信号。</p>
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
