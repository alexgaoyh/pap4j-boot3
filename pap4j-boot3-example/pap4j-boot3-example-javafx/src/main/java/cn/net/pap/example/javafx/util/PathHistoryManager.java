package cn.net.pap.example.javafx.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

public class PathHistoryManager {

    private static final Logger log = LoggerFactory.getLogger(PathHistoryManager.class);

    /**
     * 核心数据结构
     * key   : directory
     * value : 有序历史（尾部 = 最近一次）
     */
    private static final ConcurrentHashMap<String, Deque<String>> historyRegistry = new ConcurrentHashMap<>();

    private static final ReadWriteLock registryLock = new ReentrantReadWriteLock();

    /**
     * 禁止实例化
     */
    private PathHistoryManager() {
    }

    /**
     * 添加历史文件路径（线程安全）
     */
    public static void registerHistoricalFile(String directory, String historicalFile) {
        registryLock.writeLock().lock();
        try {
            Deque<String> historyDeque = historyRegistry.computeIfAbsent(directory, k -> new ConcurrentLinkedDeque<>());
            historyDeque.addLast(historicalFile);
        } finally {
            registryLock.writeLock().unlock();
        }
    }

    /**
     * 获取并删除最近一次历史记录（LIFO）
     *
     * @return 最近一次历史文件路径；如果不存在返回 null
     */
    public static String popLatestHistoricalFile(String directory) {
        registryLock.writeLock().lock();
        try {
            Deque<String> historyDeque = historyRegistry.get(directory);
            if (historyDeque == null || historyDeque.isEmpty()) {
                return null;
            }

            String latest = historyDeque.pollLast();
            if (historyDeque.isEmpty()) {
                historyRegistry.remove(directory);
            }
            return latest;
        } finally {
            registryLock.writeLock().unlock();
        }
    }

    public static void cleanupExpiredHistory(Duration maxAge) {
        long now = System.currentTimeMillis();
        long expireMillis = maxAge.toMillis();

        registryLock.writeLock().lock();
        try {
            Iterator<Map.Entry<String, Deque<String>>> mapIterator = historyRegistry.entrySet().iterator();

            while (mapIterator.hasNext()) {
                Map.Entry<String, Deque<String>> entry = mapIterator.next();
                Deque<String> deque = entry.getValue();

                Iterator<String> fileIterator = deque.iterator();
                while (fileIterator.hasNext()) {
                    String filePath = fileIterator.next();

                    Long timestamp = extractTimestampFromFileName(filePath);
                    if (timestamp == null) {
                        continue; // 无法解析时间戳，跳过
                    }

                    if (now - timestamp > expireMillis) {
                        // 1. 删除文件（即使失败也继续清理历史）
                        try {
                            Files.deleteIfExists(Path.of(filePath));
                        } catch (IOException e) {
                            log.error("cleanupExpiredHistory", e);
                        }
                        // 2. 删除历史记录
                        fileIterator.remove();
                    }
                }
                // 3. 如果该目录下已经没有历史，移除 key
                if (deque.isEmpty()) {
                    mapIterator.remove();
                }
            }
        } finally {
            registryLock.writeLock().unlock();
        }
    }

    public static void deleteFilesBefore(Path directory, Duration duration) {
        if (directory == null || duration == null) {
            return;
        }
        if (!Files.isDirectory(directory)) {
            return;
        }

        long cutoffMillis = System.currentTimeMillis() - duration.toMillis();

        try (Stream<Path> stream = Files.list(directory)) {
            stream
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        Long timestamp = extractTimestampFromFileName(path.toAbsolutePath().toString());
                        if (timestamp == null) {
                            return;
                        }
                        if (timestamp < cutoffMillis) {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.error("deleteFilesBefore", e);
                            }
                        }
                    });
        } catch (IOException e) {
            log.error("deleteFilesBefore", e);
        }
    }

    private static Long extractTimestampFromFileName(String filePath) {
        try {
            String fileName = Path.of(filePath).getFileName().toString();
            int underscoreIndex = fileName.indexOf('_');
            if (underscoreIndex <= 0) {
                return null;
            }

            String timestampPart = fileName.substring(0, underscoreIndex);
            return Long.parseLong(timestampPart);
        } catch (Exception e) {
            log.error("extractTimestampFromFileName", e);
            return null;
        }
    }

    /**
     * 清空所有历史记录
     */
    public static void clearAll() {
        registryLock.writeLock().lock();
        try {
            historyRegistry.clear();
        } finally {
            registryLock.writeLock().unlock();
        }
    }

}
