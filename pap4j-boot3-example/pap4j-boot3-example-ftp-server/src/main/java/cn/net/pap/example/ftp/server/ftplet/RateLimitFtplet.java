package cn.net.pap.example.ftp.server.ftplet;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限制 FTP 服务器的每秒连接数，防止过多客户端同时连接导致服务器资源耗尽。
 */
public class RateLimitFtplet extends DefaultFtplet {

    private final int maxPerSecond;
    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile long lastTimestamp = System.currentTimeMillis();

    public RateLimitFtplet(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
    }

    @Override
    public FtpletResult onConnect(FtpSession session) throws FtpException {
        synchronized (this) {
            long now = System.currentTimeMillis();

            // 每秒重置计数
            if (now - lastTimestamp >= 1000) {
                counter.set(0);
                lastTimestamp = now;
            }

            if (counter.get() >= maxPerSecond) {
                try {
                    // 超过速率时等待
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            counter.incrementAndGet();
        }
        return FtpletResult.DEFAULT;
    }
}
