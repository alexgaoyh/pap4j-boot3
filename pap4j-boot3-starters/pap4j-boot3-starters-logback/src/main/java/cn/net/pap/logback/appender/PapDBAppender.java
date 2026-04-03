package cn.net.pap.logback.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <h3>项目专用数据库日志 Appender</h3>
 * <p>
 * 该类具备以下核心特性：
 * <ul>
 *   <li><b>无锁设计 (Lock-Free):</b> 采用 ConcurrentLinkedQueue 业务线程零阻塞。</li>
 *   <li><b>规范化线程池:</b> 显式使用 ScheduledThreadPoolExecutor。</li>
 *   <li><b>高性能批量写入:</b> 默认积攒 50 条日志触发批量插入。</li>
 *   <li><b>定时刷新:</b> 5秒强制执行 flush()。</li>
 * </ul>
 * </p>
 */
public class PapDBAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private final DataSource dataSource;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    private static final int BATCH_SIZE = 50;

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1,
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                private final String namePrefix = "logback-db-worker-";

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }, new ThreadPoolExecutor.CallerRunsPolicy());

    public PapDBAppender(DataSource dataSource) {
        this.dataSource = dataSource;
        this.scheduler.scheduleAtFixedRate(this::flush, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        if (dataSource != null) {
            // [无锁入队] offer 操作是线程安全的且极速返回
            queue.offer(iLoggingEvent.getLevel().levelStr);
            // [原子计数] 只有当累计达到 BATCH_SIZE 时才尝试触发刷新
            if (count.incrementAndGet() >= BATCH_SIZE) {
                flush();
            }
        }
    }

    /**
     * 核心刷新逻辑：将内存队列中的日志批量持久化到数据库
     * <p>
     * 设计要点：
     * 1. 抢占锁保护：通过 CAS 防止多个后台线程（或业务线程）同时争抢数据库连接。
     * 2. 贪婪消费：进入刷新状态后，会循环抓取队列中的所有日志，直到队列清空。
     * 3. 异步吞吐：最耗时的 IO 操作在锁外异步进行（借由 Logback 异步机制或定时任务）。
     * </p>
     */
    private void flush() {
        // [CAS 抢占标志位] 只有一个线程能成功将 false 设为 true，从而进入刷新核心区
        if (!isFlushing.compareAndSet(false, true)) {
            return;
        }
        try {
            // [外层循环] 持续处理，直到队列彻底掏空
            while (true) {
                List<String> batch = new ArrayList<>(BATCH_SIZE);
                String level;
                
                // [内层循环] 构建当前批次的包，上限为 BATCH_SIZE
                while (batch.size() < BATCH_SIZE && (level = queue.poll()) != null) {
                    batch.add(level);
                    count.decrementAndGet(); // 对应入队时的计数增加
                }

                // 如果本轮没抓到任何数据，说明队列已空，可以退出整个刷新动作
                if (batch.isEmpty()) {
                    break;
                }
                
                // [批量执行 SQL] 只有在抓取到数据后才建立数据库连接执行 IO
                executeBatchInsert(batch);

                // 如果本轮抓到的数据不足一页，说明暂无积压，提前退出循环以节省资源
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
        } finally {
            // [标志位重置] 无论操作是否成功，必须释放标志位，允许下一波刷新
            isFlushing.set(false);
        }
    }

    private void executeBatchInsert(List<String> batch) {
        String sql = "INSERT INTO log(level) VALUES (?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (String level : batch) {
                ps.setString(1, level);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            addError("Failed to insert log batch into DB", e);
        }
    }

    @Override
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        flush(); 
        super.stop();
    }

}