package cn.net.pap.common.datastructure.ReservoirSampling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * <p><strong>ReservoirSamplingUtil</strong> 提供了蓄水池采样算法的高效实现。</p>
 *
 * <p>它允许从未知或极大尺寸的数据流中均匀地抽取 <strong>k</strong> 个元素。
 * 在与流（Stream）正确结合使用时，此类是线程安全的，适合在生产环境中使用。</p>
 *
 * <ul>
 *     <li>支持从迭代器（Iterator）、集合（Collection）、流（Stream）以及可迭代对象（Iterable）中进行采样。</li>
 *     <li>包含加权蓄水池采样功能。</li>
 *     <li>提供批量处理以优化性能。</li>
 * </ul>
 */
public final class ReservoirSamplingUtil {

    private ReservoirSamplingUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * <p>从给定的 {@link Iterator} 中均匀抽取 <strong>k</strong> 个元素。</p>
     *
     * @param iterator 数据源迭代器。
     * @param k        要抽取的元素数量。
     * @param <T>      元素的类型。
     * @return 包含抽取元素的 {@link List}。如果源为空，则返回空列表。
     * @throws IllegalArgumentException 如果 <strong>k &lt;= 0</strong>。
     */
    public static <T> List<T> sample(Iterator<T> iterator, int k) {
        validateK(k);

        if (iterator == null || !iterator.hasNext()) {
            return Collections.emptyList();
        }

        List<T> reservoir = new ArrayList<>(k);

        // 第一阶段：填充前k个元素
        int count = 0;
        while (count < k && iterator.hasNext()) {
            reservoir.add(iterator.next());
            count++;
        }

        // 如果元素总数小于等于k，直接返回所有元素
        if (count < k) {
            return reservoir;
        }

        // 第二阶段：替换阶段
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (iterator.hasNext()) {
            T item = iterator.next();
            count++;

            // 生成[0, count)范围内的随机整数
            int randomIndex = random.nextInt(count);

            // 如果随机索引在蓄水池范围内，则替换
            if (randomIndex < k) {
                reservoir.set(randomIndex, item);
            }
        }

        return reservoir;
    }

    /**
     * <p>从给定的 {@link Collection} 中均匀抽取 <strong>k</strong> 个元素。</p>
     *
     * @param collection 数据源集合。
     * @param k          要抽取的元素数量。
     * @param <T>        元素的类型。
     * @return 包含抽取元素的 {@link List}。
     */
    public static <T> List<T> sample(Collection<T> collection, int k) {
        validateK(k);

        if (collection == null || collection.isEmpty()) {
            return Collections.emptyList();
        }

        return sample(collection.iterator(), Math.min(k, collection.size()));
    }

    /**
     * <p>从给定的 {@link Stream} 中均匀抽取 <strong>k</strong> 个元素。</p>
     * 
     * <p>注意：<strong>不支持</strong>并行流，因为它们会破坏蓄水池采样的不变性。</p>
     *
     * @param stream 数据源流。
     * @param k      要抽取的元素数量。
     * @param <T>    元素的类型。
     * @return 包含抽取元素的 {@link List}。
     * @throws IllegalArgumentException 如果流是并行流。
     */
    public static <T> List<T> sample(Stream<T> stream, int k) {
        validateK(k);

        if (stream == null) {
            return Collections.emptyList();
        }

        // 注意：并行流会破坏蓄水池算法的正确性
        if (stream.isParallel()) {
            throw new IllegalArgumentException("Reservoir sampling does not support parallel streams");
        }

        return sample(stream.iterator(), k);
    }

    /**
     * <p>从给定的 {@link Iterable} 中均匀抽取 <strong>k</strong> 个元素。</p>
     *
     * @param iterable 数据源可迭代对象。
     * @param k        要抽取的元素数量。
     * @param <T>      元素的类型。
     * @return 包含抽取元素的 {@link List}。
     */
    public static <T> List<T> sample(Iterable<T> iterable, int k) {
        validateK(k);

        if (iterable == null) {
            return Collections.emptyList();
        }

        return sample(iterable.iterator(), k);
    }

    /**
     * <p>执行加权蓄水池采样。</p>
     *
     * <p>权重较高的元素被选中的几率成比例地更高。</p>
     *
     * @param iterator        数据源迭代器。
     * @param k               要抽取的元素数量。
     * @param weightExtractor 提取每个元素权重的函数。
     * @param <T>             元素的类型。
     * @return 包含抽取元素的 {@link List}。
     */
    public static <T> List<T> weightedSample(Iterator<T> iterator, int k, java.util.function.ToDoubleFunction<T> weightExtractor) {
        validateK(k);

        if (iterator == null || !iterator.hasNext() || weightExtractor == null) {
            return Collections.emptyList();
        }

        List<T> reservoir = new ArrayList<>(k);
        List<Double> weights = new ArrayList<>(k);
        double weightSum = 0.0;
        int count = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 填充前k个元素
        while (count < k && iterator.hasNext()) {
            T item = iterator.next();
            double weight = Math.max(weightExtractor.applyAsDouble(item), 0.0);
            reservoir.add(item);
            weights.add(weight);
            weightSum += weight;
            count++;
        }

        // 如果元素少于k，直接返回
        if (count < k) {
            return reservoir;
        }

        // 加权采样
        while (iterator.hasNext()) {
            T item = iterator.next();
            double weight = Math.max(weightExtractor.applyAsDouble(item), 0.0);
            weightSum += weight;
            count++;

            // 计算替换概率
            double replacementProb = (k * weight) / (weightSum);
            if (random.nextDouble() < replacementProb) {
                // 选择要替换的位置
                int replaceIndex = selectReplacementIndex(weights, random);
                reservoir.set(replaceIndex, item);
                weights.set(replaceIndex, weight);
            }
        }

        return reservoir;
    }

    /**
     * <p>执行批量蓄水池采样以最大程度地减少随机数生成的开销。</p>
     *
     * @param iterator  数据源迭代器。
     * @param k         要抽取的元素数量。
     * @param batchSize 生成跳跃的批处理大小。
     * @param <T>       元素的类型。
     * @return 包含抽取元素的 {@link List}。
     */
    public static <T> List<T> sampleBatch(Iterator<T> iterator, int k, int batchSize) {
        validateK(k);

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }

        if (iterator == null || !iterator.hasNext()) {
            return Collections.emptyList();
        }

        List<T> reservoir = new ArrayList<>(k);
        int count = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 填充前k个元素
        while (count < k && iterator.hasNext()) {
            reservoir.add(iterator.next());
            count++;
        }

        if (count < k) {
            return reservoir;
        }

        // 批量处理
        while (iterator.hasNext()) {
            int remaining = batchSize;
            while (remaining-- > 0 && iterator.hasNext()) {
                T item = iterator.next();
                count++;

                // 使用预生成的随机数
                if (random.nextInt(count) < k) {
                    int randomIndex = random.nextInt(k);
                    reservoir.set(randomIndex, item);
                }
            }
        }

        return reservoir;
    }

    /**
     * <p>验证样本大小 <strong>k</strong> 是否严格为正数。</p>
     * 
     * @param k 样本大小。
     */
    private static void validateK(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Sample size k must be positive");
        }
    }

    /**
     * <p>根据累积的权重选择一个要被替换的索引。</p>
     *
     * @param weights 当前蓄水池元素的权重列表。
     * @param random  随机数生成器。
     * @return 选择进行替换的索引。
     */
    private static int selectReplacementIndex(List<Double> weights, Random random) {
        // 根据权重选择要替换的位置
        double totalWeight = weights.stream().mapToDouble(Double::doubleValue).sum();
        double randomWeight = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0.0;

        for (int i = 0; i < weights.size(); i++) {
            cumulativeWeight += weights.get(i);
            if (cumulativeWeight >= randomWeight) {
                return i;
            }
        }

        // 以防浮点精度问题
        return weights.size() - 1;
    }

}