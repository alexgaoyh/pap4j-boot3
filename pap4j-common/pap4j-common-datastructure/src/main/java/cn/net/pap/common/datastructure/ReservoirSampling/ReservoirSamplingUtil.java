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
 * 高效蓄水池采样算法工具类
 * 支持从不确定大小的数据流中均匀采样k个元素
 * 线程安全，适用于生产环境
 */
public final class ReservoirSamplingUtil {

    private ReservoirSamplingUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 从迭代器中采样k个元素
     *
     * @param iterator 数据源迭代器，可以非常大或无限
     * @param k        采样数量
     * @param <T>      元素类型
     * @return 采样结果列表，如果数据源为空则返回空列表
     * @throws IllegalArgumentException 如果k <= 0
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
     * 从集合中采样k个元素
     *
     * @param collection 数据源集合
     * @param k          采样数量
     * @param <T>        元素类型
     * @return 采样结果列表
     */
    public static <T> List<T> sample(Collection<T> collection, int k) {
        validateK(k);

        if (collection == null || collection.isEmpty()) {
            return Collections.emptyList();
        }

        return sample(collection.iterator(), Math.min(k, collection.size()));
    }

    /**
     * 从Stream中采样k个元素（非并行流）
     *
     * @param stream 数据流
     * @param k      采样数量
     * @param <T>    元素类型
     * @return 采样结果列表
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
     * 从Iterable中采样k个元素
     *
     * @param iterable 可迭代对象
     * @param k        采样数量
     * @param <T>      元素类型
     * @return 采样结果列表
     */
    public static <T> List<T> sample(Iterable<T> iterable, int k) {
        validateK(k);

        if (iterable == null) {
            return Collections.emptyList();
        }

        return sample(iterable.iterator(), k);
    }

    /**
     * 带权重的蓄水池采样（加权采样）
     *
     * @param iterator        数据源迭代器
     * @param k               采样数量
     * @param weightExtractor 权重提取函数
     * @param <T>             元素类型
     * @return 采样结果列表
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
     * 批量采样 - 对多个流进行采样，减少随机数生成开销
     *
     * @param iterator  数据源迭代器
     * @param k         采样数量
     * @param batchSize 批量大小
     * @param <T>       元素类型
     * @return 采样结果列表
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
     * 验证k值有效性
     */
    private static void validateK(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Sample size k must be positive");
        }
    }

    /**
     * 选择替换位置的辅助方法（用于加权采样）
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
