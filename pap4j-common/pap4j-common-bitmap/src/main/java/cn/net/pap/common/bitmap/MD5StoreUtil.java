package cn.net.pap.common.bitmap;

import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 单例 MD5 存储工具类（线程安全）
 * 使用两个 Roaring64NavigableMap 分别存储高64位和低64位
 * 所有方法均为静态方法
 */
public final class MD5StoreUtil {
    // 单例实例
    private static final MD5StoreUtil INSTANCE = new MD5StoreUtil();

    // 存储结构
    private final Roaring64NavigableMap highBitsMap;
    private final Roaring64NavigableMap lowBitsMap;

    // 读写锁保证线程安全
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private MD5StoreUtil() {
        this.highBitsMap = new Roaring64NavigableMap();
        this.lowBitsMap = new Roaring64NavigableMap();
    }

    /* ========== 核心操作方法 ========== */

    /**
     * 添加 MD5 值（32位十六进制字符串）
     */
    public static void add(String md5Hex) {
        Objects.requireNonNull(md5Hex);
        if (md5Hex.length() != 32) {
            throw new IllegalArgumentException("MD5 must be 32 characters long");
        }

        long[] parts = splitMD5(md5Hex);

        INSTANCE.lock.writeLock().lock();
        try {
            INSTANCE.highBitsMap.add(parts[0]);
            INSTANCE.lowBitsMap.add(parts[1]);
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }

    /**
     * 检查是否包含指定的 MD5 值
     */
    public static boolean contains(String md5Hex) {
        Objects.requireNonNull(md5Hex);
        if (md5Hex.length() != 32) {
            return false;
        }

        long[] parts = splitMD5(md5Hex);

        INSTANCE.lock.readLock().lock();
        try {
            return INSTANCE.highBitsMap.contains(parts[0]) && INSTANCE.lowBitsMap.contains(parts[1]);
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }

    /**
     * 移除指定的 MD5 值
     */
    public static void remove(String md5Hex) {
        Objects.requireNonNull(md5Hex);
        if (md5Hex.length() != 32) {
            return;
        }

        long[] parts = splitMD5(md5Hex);

        INSTANCE.lock.writeLock().lock();
        try {
            INSTANCE.highBitsMap.removeLong(parts[0]);
            INSTANCE.lowBitsMap.removeLong(parts[1]);
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }

    /**
     * 返回存储的 MD5 数量
     */
    public static long size() {
        INSTANCE.lock.readLock().lock();
        try {
            return INSTANCE.highBitsMap.getLongCardinality();
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }

    /**
     * 判断是否为空
     */
    public static boolean isEmpty() {
        INSTANCE.lock.readLock().lock();
        try {
            return INSTANCE.highBitsMap.isEmpty();
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }

    /**
     * 清空所有存储的 MD5 值
     */
    public static void clear() {
        INSTANCE.lock.writeLock().lock();
        try {
            INSTANCE.highBitsMap.clear();
            INSTANCE.lowBitsMap.clear();
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }

    /* ========== 批量操作方法 ========== */

    /**
     * 批量添加 MD5 值
     */
    public static void addAll(Iterable<String> md5HexList) {
        Objects.requireNonNull(md5HexList);

        INSTANCE.lock.writeLock().lock();
        try {
            for (String md5Hex : md5HexList) {
                if (md5Hex.length() != 32) continue;

                long[] parts = splitMD5(md5Hex);
                INSTANCE.highBitsMap.add(parts[0]);
                INSTANCE.lowBitsMap.add(parts[1]);
            }
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }

    /**
     * 批量检查 MD5 值是否存在
     */
    public static boolean containsAll(Iterable<String> md5HexList) {
        Objects.requireNonNull(md5HexList);

        INSTANCE.lock.readLock().lock();
        try {
            for (String md5Hex : md5HexList) {
                if (md5Hex.length() != 32) return false;

                long[] parts = splitMD5(md5Hex);
                if (!INSTANCE.highBitsMap.contains(parts[0]) || !INSTANCE.lowBitsMap.contains(parts[1])) {
                    return false;
                }
            }
            return true;
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }

    /* ========== 实用方法 ========== */

    /**
     * 将 MD5 拆分为高64位和低64位
     */
    private static long[] splitMD5(String md5Hex) {
        String high64Hex = md5Hex.substring(0, 16);
        String low64Hex = md5Hex.substring(16);

        long high64 = Long.parseUnsignedLong(high64Hex, 16);
        long low64 = Long.parseUnsignedLong(low64Hex, 16);

        return new long[]{high64, low64};
    }

    /**
     * 将高低64位组合为MD5字符串
     */
    public static String toMD5Hex(long high64, long low64) {
        String high64Hex = Long.toUnsignedString(high64, 16);
        String low64Hex = Long.toUnsignedString(low64, 16);

        // 补全前导零
        high64Hex = String.format("%16s", high64Hex).replace(' ', '0');
        low64Hex = String.format("%16s", low64Hex).replace(' ', '0');

        return high64Hex + low64Hex;
    }

    /**
     * 获取所有 MD5 的迭代器（线程安全快照）
     */
    public static Iterator<String> iterator() {
        INSTANCE.lock.readLock().lock();
        try {
            // 创建快照
            Roaring64NavigableMap highBitsSnapshot = new Roaring64NavigableMap();
            Roaring64NavigableMap lowBitsSnapshot = new Roaring64NavigableMap();

            // 手动复制高低64位的所有数据
            for (Iterator<Long> it = INSTANCE.highBitsMap.iterator(); it.hasNext(); ) {
                highBitsSnapshot.add(it.next());
            }
            for (Iterator<Long> it = INSTANCE.lowBitsMap.iterator(); it.hasNext(); ) {
                lowBitsSnapshot.add(it.next());
            }

            return new Iterator<String>() {
                private final Iterator<Long> highBitsIterator = highBitsSnapshot.iterator();
                private final Iterator<Long> lowBitsIterator = lowBitsSnapshot.iterator();

                @Override
                public boolean hasNext() {
                    return highBitsIterator.hasNext() && lowBitsIterator.hasNext();
                }

                @Override
                public String next() {
                    return toMD5Hex(highBitsIterator.next(), lowBitsIterator.next());
                }
            };
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }

    /* ========== 统计方法 ========== */

    /**
     * 获取内存占用估算（字节）
     */
    public static long estimatedMemoryUsage() {
        INSTANCE.lock.readLock().lock();
        try {
            return INSTANCE.highBitsMap.serializedSizeInBytes() + INSTANCE.lowBitsMap.serializedSizeInBytes();
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }
}
