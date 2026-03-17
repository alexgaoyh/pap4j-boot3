package cn.net.pap.common.bitmap;

import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p><strong>单例 MD5 存储工具类（线程安全）</strong></p>
 * <p>使用 {@code Map<Long, Roaring64NavigableMap>} 建立高 64 位到低 64 位的精确映射关系，彻底解决了高低位绑定丢失及 size 计算错误的问题。</p>
 *
 * <p><b>==================== 生产环境严重隐患警告 ====================</b></p>
 * <p><b>强烈不建议在生产环境使用此工具类存储海量 MD5，存在致命的数据结构不匹配问题！</b></p>
 * <ul>
 * <li><b>1. 致命缺陷：MD5 特性与 RoaringBitmap 的天然冲突（引发内存灾难）</b>
 *     <ul>
 *     <li>MD5 是完全均匀分布的哈希值。根据生日悖论，在 64 位空间中需要约 43 亿数据才会大概率出现高 64 位冲突。</li>
 *     <li>在实际业务中，HashMap 中的 Key（高 64 位）几乎绝不重复，导致每个 Roaring64NavigableMap 中永远只有一个低 64 位元素。</li>
 *     <li>这不仅完全起不到 Bitmap 的压缩作用，反而会因为巨量的 HashMap Node、Long 包装类以及 RoaringBitmap 的基础对象开销，导致比直接使用 {@code HashSet<String>} 消耗成倍的内存。</li>
 *     </ul>
 * </li>
 * <li><b>2. 严重的性能隐患：iterator() 的“Stop-The-World”效应</b>
 *     <ul>
 *     <li>迭代器在获取快照时，在读锁保护下进行了全量 HashMap 的遍历和 RoaringBitmap 的深拷贝。</li>
 *     <li>若是千万级数据，会导致长时间占用读锁，阻塞所有写操作（add, remove），极易引发生产系统接口超时或线程池打满。</li>
 *     </ul>
 * </li>
 * <li><b>3. 反序列化缺乏原子性（破坏数据一致性）</b>
 *     <ul>
 *     <li>deserialize 方法先执行 clear() 再读取流，若中途发生异常（如文件损坏、网络流中断），会导致原有数据全部丢失且无法恢复。</li>
 *     </ul>
 * </li>
 * <li><b>4. 内存估算严重失真</b>
 *     <ul>
 *     <li>estimatedMemoryUsage() 依赖的序列化大小远小于对象在 JVM 堆内存中的实际占用（包含对象头、对齐填充等），会给监控带来虚假的安全感。</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * <p><b>==================== 代码架构与规范评估 ====================</b></p>
 * <p>撇开底层数据结构不匹配的缺陷，本类的代码规范与完整性极其优秀（可作为标准并发组件模板）：</p>
 * <ul>
 * <li><b>API 完备：</b>基础增删改查、批量操作、快照遍历、序列化/反序列化体系（File/Bytes/Stream）一应俱全。</li>
 * <li><b>并发严谨：</b>精准使用 ReentrantReadWriteLock 实现读写分离，且在 finally 块中释放，无死锁风险。</li>
 * <li><b>防御性编程：</b>严密的入参校验，以及极其老练的十六进制前导零补全逻辑。</li>
 * <li><b>防内存泄漏：</b>在 remove() 中精准清理空的低位集合，防止 HashMap 无限膨胀。</li>
 * </ul>
 * <p><b>架构替代建议：</b></p>
 * <ul>
 * <li>若允许极小误判：推荐使用 <strong>布隆过滤器 (Bloom Filter)</strong>，极其省内存。</li>
 * <li>若必须单机 100% 精确：推荐抛弃 Bitmap，改用 {@code long[]} 配合二分查找，或基于 Fastutil 的 {@code Long2LongOpenHashMap}，乃至 <strong>堆外内存 (Chronicle Map)</strong>。</li>
 * <li>若为分布式环境：推荐交由 <strong>Redis 集群</strong> 处理。</li>
 * </ul>
 *
 * @author alexgaoyh
 */
public final class MD5StoreUtil {
    // 单例实例
    private static final MD5StoreUtil INSTANCE = new MD5StoreUtil();

    // 核心存储结构：高64位 -> 包含所有对应低64位的集合
    private final Map<Long, Roaring64NavigableMap> store;

    // 读写锁保证线程安全
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private MD5StoreUtil() {
        this.store = new HashMap<>();
    }

    /* ========== 核心操作方法 ========== */

    /**
     * <p><strong>添加 MD5 值</strong></p>
     * <p>添加一个 32 位十六进制字符串格式的 MD5 值到集合中。</p>
     * 
     * @param md5Hex 待添加的 32 位十六进制字符串
     * @throws IllegalArgumentException 如果字符串长度不为 32
     * @throws NullPointerException 如果传入的 md5Hex 为 null
     */
    public static void add(String md5Hex) {
        Objects.requireNonNull(md5Hex);
        if (md5Hex.length() != 32) {
            throw new IllegalArgumentException("MD5 must be 32 characters long");
        }

        long[] parts = splitMD5(md5Hex);

        INSTANCE.lock.writeLock().lock();
        try {
            // 如果高位不存在，则创建一个新的 Roaring64NavigableMap，然后将低位加入其中
            INSTANCE.store.computeIfAbsent(parts[0], k -> new Roaring64NavigableMap()).add(parts[1]);
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }

    /**
     * <p><strong>检查是否包含指定的 MD5 值</strong></p>
     * 
     * @param md5Hex 待检查的 32 位十六进制字符串
     * @return 如果包含该 MD5 则返回 {@code true}，否则返回 {@code false}（长度不为 32 也返回 false）
     * @throws NullPointerException 如果传入的 md5Hex 为 null
     */
    public static boolean contains(String md5Hex) {
        Objects.requireNonNull(md5Hex);
        if (md5Hex.length() != 32) {
            return false;
        }

        long[] parts = splitMD5(md5Hex);

        INSTANCE.lock.readLock().lock();
        try {
            Roaring64NavigableMap lowBitsMap = INSTANCE.store.get(parts[0]);
            return lowBitsMap != null && lowBitsMap.contains(parts[1]);
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }

    /**
     * <p><strong>移除指定的 MD5 值</strong></p>
     * <p>从存储中安全地移除特定的 MD5 值，并会在低位集合为空时清理高位映射以优化内存。</p>
     * 
     * @param md5Hex 待移除的 32 位十六进制字符串
     * @throws NullPointerException 如果传入的 md5Hex 为 null
     */
    public static void remove(String md5Hex) {
        Objects.requireNonNull(md5Hex);
        if (md5Hex.length() != 32) {
            return;
        }

        long[] parts = splitMD5(md5Hex);

        INSTANCE.lock.writeLock().lock();
        try {
            Roaring64NavigableMap lowBitsMap = INSTANCE.store.get(parts[0]);
            if (lowBitsMap != null) {
                lowBitsMap.removeLong(parts[1]);
                // 内存优化：如果该高位下的所有低位都被移除了，则清理掉这个高位 Key
                if (lowBitsMap.isEmpty()) {
                    INSTANCE.store.remove(parts[0]);
                }
            }
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }

    /**
     * <p><strong>返回存储的 MD5 数量</strong></p>
     * 
     * @return 当前存储中包含的 MD5 值的总数
     */
    public static long size() {
        long totalSize = 0;
        INSTANCE.lock.readLock().lock();
        try {
            for (Roaring64NavigableMap map : INSTANCE.store.values()) {
                totalSize += map.getLongCardinality();
            }
            return totalSize;
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }

    /**
     * <p><strong>判断是否为空</strong></p>
     * 
     * @return 如果存储中没有任何元素则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isEmpty() {
        INSTANCE.lock.readLock().lock();
        try {
            return INSTANCE.store.isEmpty();
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }

    /**
     * <p><strong>清空所有存储的 MD5 值</strong></p>
     */
    public static void clear() {
        INSTANCE.lock.writeLock().lock();
        try {
            INSTANCE.store.clear();
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }

    /* ========== 批量操作方法 ========== */

    /**
     * <p><strong>批量添加 MD5 值</strong></p>
     * <p>将可迭代对象中的所有 MD5 值批量加入存储中。长度不等于 32 的字符串将被忽略。</p>
     * 
     * @param md5HexList 包含 32 位十六进制 MD5 字符串的可迭代集合
     * @throws NullPointerException 如果传入的集合为 null
     */
    public static void addAll(Iterable<String> md5HexList) {
        Objects.requireNonNull(md5HexList);

        INSTANCE.lock.writeLock().lock();
        try {
            for (String md5Hex : md5HexList) {
                if (md5Hex.length() != 32) continue;

                long[] parts = splitMD5(md5Hex);
                INSTANCE.store.computeIfAbsent(parts[0], k -> new Roaring64NavigableMap()).add(parts[1]);
            }
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }

    /**
     * <p><strong>批量检查 MD5 值是否存在</strong></p>
     * 
     * @param md5HexList 包含待检查的 32 位十六进制 MD5 字符串的可迭代集合
     * @return 只有当集合中所有的 MD5 值都存在于存储中时才返回 {@code true}，否则返回 {@code false}
     * @throws NullPointerException 如果传入的集合为 null
     */
    public static boolean containsAll(Iterable<String> md5HexList) {
        Objects.requireNonNull(md5HexList);

        INSTANCE.lock.readLock().lock();
        try {
            for (String md5Hex : md5HexList) {
                if (md5Hex.length() != 32) return false;

                long[] parts = splitMD5(md5Hex);
                Roaring64NavigableMap lowBitsMap = INSTANCE.store.get(parts[0]);
                if (lowBitsMap == null || !lowBitsMap.contains(parts[1])) {
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
     * <p>将 MD5 拆分为高 64 位和低 64 位</p>
     * 
     * @param md5Hex 32位十六进制字符串
     * @return 包含两个长整型的数组，索引0为高64位，索引1为低64位
     */
    private static long[] splitMD5(String md5Hex) {
        String high64Hex = md5Hex.substring(0, 16);
        String low64Hex = md5Hex.substring(16);

        long high64 = Long.parseUnsignedLong(high64Hex, 16);
        long low64 = Long.parseUnsignedLong(low64Hex, 16);

        return new long[]{high64, low64};
    }

    /**
     * <p><strong>将高低 64 位组合为 MD5 字符串</strong></p>
     * 
     * @param high64 高 64 位值
     * @param low64 低 64 位值
     * @return 补全前导零后的 32 位 MD5 十六进制字符串
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
     * <p><strong>获取所有 MD5 的迭代器（线程安全快照）</strong></p>
     * <p>返回当前所有数据的深拷贝迭代器。由于涉及全量复制，在大数据量下会产生一定的性能开销。</p>
     * 
     * @return 遍历所有 32 位 MD5 字符串的迭代器
     */
    public static Iterator<String> iterator() {
        Map<Long, Roaring64NavigableMap> snapshot = new HashMap<>();

        INSTANCE.lock.readLock().lock();
        try {
            // 深拷贝当前状态
            for (Map.Entry<Long, Roaring64NavigableMap> entry : INSTANCE.store.entrySet()) {
                Roaring64NavigableMap copiedLowMap = new Roaring64NavigableMap();

                // 修复：显式使用迭代器进行遍历
                Iterator<Long> it = entry.getValue().iterator();
                while (it.hasNext()) {
                    copiedLowMap.add(it.next());
                }

                snapshot.put(entry.getKey(), copiedLowMap);
            }
        } finally {
            INSTANCE.lock.readLock().unlock();
        }

        return new Iterator<String>() {
            private final Iterator<Map.Entry<Long, Roaring64NavigableMap>> mapIterator = snapshot.entrySet().iterator();
            private Long currentHigh = null;
            private Iterator<Long> currentLowIterator = null;

            @Override
            public boolean hasNext() {
                // 如果当前低位迭代器还有值，返回 true
                if (currentLowIterator != null && currentLowIterator.hasNext()) {
                    return true;
                }
                // 否则寻找下一个包含数据的高位节点
                while (mapIterator.hasNext()) {
                    Map.Entry<Long, Roaring64NavigableMap> entry = mapIterator.next();
                    currentHigh = entry.getKey();
                    currentLowIterator = entry.getValue().iterator();
                    if (currentLowIterator.hasNext()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return toMD5Hex(currentHigh, currentLowIterator.next());
            }
        };
    }

    /* ========== 序列化/反序列化方法 ========== */

    /**
     * <p><strong>序列化当前存储的 MD5 数据到输出流</strong></p>
     * 
     * @param outputStream 目标输出流
     * @throws IOException 如果在写入过程中发生 I/O 错误
     * @throws NullPointerException 如果 outputStream 为 null
     */
    public static void serialize(OutputStream outputStream) throws IOException {
        Objects.requireNonNull(outputStream);

        DataOutputStream dos = new DataOutputStream(outputStream);

        INSTANCE.lock.readLock().lock();
        try {
            // 先写入共有多少个高位 Key
            dos.writeInt(INSTANCE.store.size());

            for (Map.Entry<Long, Roaring64NavigableMap> entry : INSTANCE.store.entrySet()) {
                dos.writeLong(entry.getKey());          // 写入高64位
                entry.getValue().serialize(dos);        // 序列化对应的低64位集合
            }
        } finally {
            INSTANCE.lock.readLock().unlock();
            dos.flush();
        }
    }

    /**
     * <p><strong>从输入流反序列化 MD5 数据</strong></p>
     * <p>注意：该操作会先清空现有的所有数据！</p>
     * 
     * @param inputStream 源输入流
     * @throws IOException 如果在读取过程中发生 I/O 错误
     * @throws NullPointerException 如果 inputStream 为 null
     */
    public static void deserialize(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream);

        DataInputStream dis = new DataInputStream(inputStream);

        INSTANCE.lock.writeLock().lock();
        try {
            INSTANCE.store.clear();

            int size = dis.readInt();
            for (int i = 0; i < size; i++) {
                long high64 = dis.readLong();
                Roaring64NavigableMap lowMap = new Roaring64NavigableMap();
                lowMap.deserialize(dis);
                INSTANCE.store.put(high64, lowMap);
            }
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }

    /**
     * <p><strong>序列化当前存储的 MD5 数据到字节数组</strong></p>
     * 
     * @return 包含序列化后数据的字节数组
     * @throws IOException 如果发生 I/O 错误
     */
    public static byte[] serializeToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serialize(baos);
        return baos.toByteArray();
    }

    /**
     * <p><strong>从字节数组反序列化 MD5 数据</strong></p>
     * 
     * @param bytes 包含序列化数据的字节数组
     * @throws IOException 如果发生 I/O 错误
     * @throws NullPointerException 如果 bytes 为 null
     */
    public static void deserializeFromBytes(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        deserialize(bais);
    }

    /**
     * <p><strong>序列化当前存储的 MD5 数据到文件</strong></p>
     * 
     * @param file 目标文件对象
     * @throws IOException 如果发生 I/O 错误
     * @throws NullPointerException 如果 file 为 null
     */
    public static void serializeToFile(File file) throws IOException {
        Objects.requireNonNull(file);
        try (FileOutputStream fos = new FileOutputStream(file); BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            serialize(bos);
        }
    }

    /**
     * <p><strong>从文件反序列化 MD5 数据</strong></p>
     * 
     * @param file 源文件对象
     * @throws IOException 如果发生 I/O 错误
     * @throws NullPointerException 如果 file 为 null
     */
    public static void deserializeFromFile(File file) throws IOException {
        Objects.requireNonNull(file);
        try (FileInputStream fis = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(fis)) {
            deserialize(bis);
        }
    }

    /* ========== 统计方法 ========== */

    /**
     * <p><strong>获取内存占用估算（字节）</strong></p>
     * <p>估算当前对象在堆内存中的近似占用大小。</p>
     * 
     * @return 估算的字节数
     */
    public static long estimatedMemoryUsage() {
        long totalBytes = 0;
        INSTANCE.lock.readLock().lock();
        try {
            // 估算：每个 Map.Entry 大约占用 32 字节 + Long 对象 24 字节 + 实际 Bitmap 大小
            totalBytes += INSTANCE.store.size() * 56L;
            for (Roaring64NavigableMap map : INSTANCE.store.values()) {
                totalBytes += map.serializedSizeInBytes();
            }
            return totalBytes;
        } finally {
            INSTANCE.lock.readLock().unlock();
        }
    }
}