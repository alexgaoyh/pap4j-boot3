package cn.net.pap.example.ftp.server.ftplet;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 限制 FTP 服务器的连接数
 */
public class RateLimitFtplet extends DefaultFtplet {

    private final BlockingQueue<ConnectRequest> queue;
    private final ScheduledExecutorService scheduler;
    private final long intervalMs;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final int maxQueueSize;

    /**
     * @param ratePerSecond 每秒允许连接数
     * @param maxQueueSize  队列最大长度
     */
    public RateLimitFtplet(int ratePerSecond, int maxQueueSize) {
        if (ratePerSecond <= 0) throw new IllegalArgumentException("ratePerSecond must be positive");
        this.intervalMs = 1000L / ratePerSecond;
        this.maxQueueSize = maxQueueSize;
        this.queue = new ArrayBlockingQueue<>(maxQueueSize);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ftp-leaky-bucket");
            t.setDaemon(true);
            return t;
        });
        startWorker();
    }

    public RateLimitFtplet(int ratePerSecond) {
        this(ratePerSecond, 200);
    }

    /**
     * 启动漏桶调度线程
     */
    private void startWorker() {
        if (started.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    ConnectRequest req = queue.poll();
                    if (req != null) {
                        req.allow();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public FtpletResult onConnect(FtpSession session) throws FtpException {
        ConnectRequest request = new ConnectRequest(session);

        try {
            // 队列阻塞入队，避免拒绝客户端
            queue.put(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FtpletResult.DISCONNECT;
        }

        // 阻塞等待漏桶调度器释放
        return request.await();
    }

    /**
     * 封装连接请求
     */
    private static class ConnectRequest {
        private final FtpSession session;
        private final CountDownLatch latch = new CountDownLatch(1);

        ConnectRequest(FtpSession session) {
            this.session = session;
        }

        void allow() {
            latch.countDown();
        }

        FtpletResult await() {
            try {
                latch.await(); // 阻塞等待调度器
                return FtpletResult.DEFAULT;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FtpletResult.DISCONNECT;
            }
        }
    }

    /**
     * 优雅关闭
     */
    public void shutdown() {
        scheduler.shutdownNow();
    }

}