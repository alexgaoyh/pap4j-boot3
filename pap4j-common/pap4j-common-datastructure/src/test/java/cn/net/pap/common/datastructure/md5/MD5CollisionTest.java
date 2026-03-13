package cn.net.pap.common.datastructure.md5;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MD5 高 64 位冲突概率验证测试类。
 *
 * <p>本测试旨在通过实际运行大规模数据，验证在常规业务量级（如千万级）下，
 * MD5 哈希值的高 64 位（或任意连续的 64 位）几乎不存在哈希碰撞的理论结论。
 *
 * <h3>理论背景 (生日悖论)</h3>
 * <p>MD5 算法会生成 128 位（16 字节）的强均匀分布散列值。当我们仅截取其前 8 字节（高 64 位）作为 Key 时，
 * 目标样本空间大小为 $2^{64}$。根据生日悖论（Birthday Paradox），在均匀分布的散列空间中，
 * 若要达到 50% 的碰撞概率，所需的独立数据量大约为其样本空间的平方根，即 $\sqrt{2^{64}} = 2^{32}$（约 42.9 亿）。
 * <p>对于本测试中 1000 万（$10^7$）的数据量级，发生碰撞的概率近似公式为：
 * $$P \approx 1 - e^{-\frac{n^2}{2m}}$$
 * 其中 $n = 10^7$，$m = 2^{64}$。计算得出的碰撞概率极低（约 $2.7 \times 10^{-6}$），
 * 在单次千万级的本地测试中，出现碰撞属于极小概率事件。
 *
 * <h3>业务指导意义与排坑指南</h3>
 * <p>在实际业务中，如果试图利用 MD5 将数据分组存入 Bitmap（如 {@code Roaring64NavigableMap}）：
 * <ul>
 * <li><b>反直觉的陷阱</b>：开发者可能希望以高 64 位作为 Key，将低 64 位作为 Value 存入 Bitmap 以实现数据压缩。</li>
 * <li><b>实际的灾难</b>：由于高 64 位“几乎绝不重复”，导致分配的每一个 Key 对应的数据桶中，
 * 永远只会挂载唯一一个低 64 位的孤立元素。</li>
 * <li><b>后果</b>：这不仅完全丧失了 Bitmap 利用位偏移进行连续数据压缩的核心优势，
 * 还会因为创建了海量的容器对象和空闲空间，导致严重的内存浪费和性能下降。</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 * <li><b>规避 OOM</b>：测试采用了千万级循环。为了避免 JDK 自带 {@code HashMap<Long, Object>}
 * 产生海量的 {@code Long} 装箱开销从而撑爆堆内存，本测试引入了 {@code fastutil} 库中的
 * {@link Long2ObjectOpenHashMap} 和 {@link LongArrayList}。</li>
 * <li><b>快速位运算</b>：利用 {@link ByteBuffer} 将 16 字节的 MD5 字节数组快速解析为两个原生 {@code long} 类型。</li>
 * </ul>
 */
public class MD5CollisionTest {

    @Test
    public void testHigh64BitCollisions() throws Exception {
        // 使用 MD5 算法
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        // FastUtil 的 Long2ObjectOpenHashMap，比 JDK HashMap<Long, Object> 占用内存少很多，且无装箱开销
        // 这里的 Value 用 LongArrayList 来模拟存储低 64 位的容器 (类似你的 Roaring64NavigableMap 的效果)
        Long2ObjectOpenHashMap<LongArrayList> map = new Long2ObjectOpenHashMap<>();

        // 设置测试数据量：100万。
        // （你可以尝试加大到 500 万或 1000 万，但要注意 JVM 堆内存限制。即使是千万级，冲突概率依然极低）
        int testSize = 10_000_000;
        System.out.println("开始生成并计算 " + testSize + " 个 MD5 哈希...");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < testSize; i++) {
            // 使用 UUID 模拟业务中唯一不重复的原始字符串
            byte[] data = UUID.randomUUID().toString().getBytes();
            byte[] hash = md5.digest(data);

            // MD5 结果是 16 字节 (128 bits)
            // 使用 ByteBuffer 快速提取前 8 字节作为高 64 位，后 8 字节作为低 64 位
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            long high64 = buffer.getLong(); // 0 ~ 7 bytes
            long low64 = buffer.getLong();  // 8 ~ 15 bytes

            // 存入 Fastutil Map 中
            // 如果高 64 位不存在，则创建一个新的列表；然后将低 64 位加入其中
            map.computeIfAbsent(high64, k -> new LongArrayList()).add(low64);
        }

        long endTime = System.currentTimeMillis();
        System.out.println("数据处理完成，耗时: " + (endTime - startTime) + " ms");

        // 验证阶段：统计冲突情况
        int maxElementsInBucket = 0;
        int bucketsWithCollisions = 0;

        for (LongArrayList list : map.values()) {
            int size = list.size();
            if (size > 1) {
                bucketsWithCollisions++;
            }
            maxElementsInBucket = Math.max(maxElementsInBucket, size);
        }

        System.out.println("========== 测试结果 ==========");
        System.out.println("总测试数据量: " + testSize);
        System.out.println("去重后的高 64 位数量 (Map Size): " + map.size());
        System.out.println("发生冲突的 Bucket 数量: " + bucketsWithCollisions);
        System.out.println("单个 Bucket 中的最大元素数量 (预期为 1): " + maxElementsInBucket);
        System.out.println("=============================");

        // 断言：证明在 100 万量级下，任何高 64 位对应的列表里永远只有 1 个低 64 位元素
        assertEquals(1, maxElementsInBucket, "在当前业务量级下，高 64 位应当不发生碰撞！");
        assertEquals(testSize, map.size(), "由于没有碰撞，Map 的大小应当与测试数据量完全一致！");
    }

}
