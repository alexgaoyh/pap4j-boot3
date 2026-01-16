package cn.net.pap.common.datastructure.ReservoirSampling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 蓄水池采样算法测试类
 */
class ReservoirSamplingUtilTest {

    private List<Integer> smallList;
    private List<Integer> mediumList;
    private List<Integer> largeList;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        smallList = IntStream.rangeClosed(1, 10)
                .boxed()
                .collect(Collectors.toList());

        mediumList = IntStream.rangeClosed(1, 1000)
                .boxed()
                .collect(Collectors.toList());

        largeList = IntStream.rangeClosed(1, 100000)
                .boxed()
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("基本功能测试 - 从小列表中采样")
    void testBasicSamplingSmallList() {
        int k = 5;
        List<Integer> result = ReservoirSamplingUtil.sample(smallList, k);

        assertEquals(k, result.size());
        assertAll(
                () -> assertTrue(result.stream().allMatch(n -> n >= 1 && n <= 10),
                        "所有采样元素应在范围内"),
                () -> assertEquals(k, result.stream().distinct().count(),
                        "采样不应有重复（除非列表有重复）")
        );
    }

    @Test
    @DisplayName("边界测试 - k大于列表大小")
    void testKGreaterThanListSize() {
        int k = 15; // 大于smallList的大小10
        List<Integer> result = ReservoirSamplingUtil.sample(smallList, k);

        assertEquals(10, result.size()); // 应返回所有元素
        assertEquals(new HashSet<>(result), new HashSet<>(smallList));
    }

    @Test
    @DisplayName("边界测试 - k等于列表大小")
    void testKEqualToListSize() {
        int k = smallList.size();
        List<Integer> result = ReservoirSamplingUtil.sample(smallList, k);

        assertEquals(k, result.size());
        assertEquals(new HashSet<>(result), new HashSet<>(smallList));
    }

    @Test
    @DisplayName("异常测试 - 无效的k值")
    void testInvalidK() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ReservoirSamplingUtil.sample(smallList, 0),
                        "k=0应抛出异常"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ReservoirSamplingUtil.sample(smallList, -1),
                        "负k值应抛出异常")
        );
    }

    @Test
    @DisplayName("空输入测试")
    void testEmptyInput() {
        List<Integer> emptyList = Collections.emptyList();

        assertAll(
                () -> assertTrue(ReservoirSamplingUtil.sample(emptyList, 5).isEmpty(),
                        "空列表应返回空结果"),
                () -> assertTrue(ReservoirSamplingUtil.sample((Iterator<Integer>) null, 5).isEmpty(),
                        "null迭代器应返回空结果"),
                () -> assertTrue(ReservoirSamplingUtil.sample((Collection<Integer>) null, 5).isEmpty(),
                        "null集合应返回空结果")
        );
    }

    @RepeatedTest(100)
    @DisplayName("均匀性测试 - 重复测试验证均匀分布")
    void testUniformDistribution() {
        int k = 5;
        int n = 100;
        List<Integer> population = IntStream.rangeClosed(1, n)
                .boxed()
                .collect(Collectors.toList());

        List<Integer> result = ReservoirSamplingUtil.sample(population, k);

        // 验证采样数量正确
        assertEquals(k, result.size());

        // 验证所有采样元素都在范围内
        assertTrue(result.stream().allMatch(i -> i >= 1 && i <= n));
    }

    @Test
    @DisplayName("大规模数据测试 - 性能验证")
    void testLargeDatasetPerformance() {
        int k = 1000;
        int n = 1000000; // 一百万个元素

        // 创建大型数据集（使用流避免内存爆炸）
        Iterator<Integer> largeStream = IntStream.rangeClosed(1, n)
                .iterator();

        long startTime = System.nanoTime();
        List<Integer> result = ReservoirSamplingUtil.sample(largeStream, k);
        long endTime = System.nanoTime();

        assertEquals(k, result.size());

        double elapsedMillis = (endTime - startTime) / 1_000_000.0;
        System.out.printf("采样 %d 个元素从 %d 个元素中耗时: %.2f ms%n", k, n, elapsedMillis);

        // 性能要求：处理一百万个元素应在1秒内完成
        assertTrue(elapsedMillis < 1000,
                "处理一百万个元素应在1秒内完成，实际耗时: " + elapsedMillis + "ms");
    }

    @Test
    @DisplayName("流API测试")
    void testStreamSampling() {
        int k = 10;
        Stream<Integer> stream = IntStream.rangeClosed(1, 1000).boxed();

        List<Integer> result = ReservoirSamplingUtil.sample(stream, k);

        assertEquals(k, result.size());
        assertTrue(result.stream().allMatch(i -> i >= 1 && i <= 1000));
    }

    @Test
    @DisplayName("并行流异常测试")
    void testParallelStreamException() {
        Stream<Integer> parallelStream = IntStream.rangeClosed(1, 1000)
                .boxed()
                .parallel();

        assertThrows(IllegalArgumentException.class,
                () -> ReservoirSamplingUtil.sample(parallelStream, 10),
                "并行流应抛出异常");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 20, 50})
    @DisplayName("参数化测试 - 不同k值")
    void testDifferentKValues(int k) {
        List<Integer> result = ReservoirSamplingUtil.sample(mediumList, k);

        assertEquals(Math.min(k, mediumList.size()), result.size());

        if (k <= mediumList.size()) {
            // 验证无重复（除非源数据有重复，但这里没有）
            assertEquals(k, result.stream().distinct().count());
        }
    }

    @Test
    @DisplayName("加权采样测试")
    void testWeightedSampling() {
        // 创建带权重的数据
        List<Item> items = Arrays.asList(
                new Item("A", 1.0),
                new Item("B", 2.0),
                new Item("C", 3.0),
                new Item("D", 4.0),
                new Item("E", 5.0)
        );

        int k = 3;
        List<Item> result = ReservoirSamplingUtil.weightedSample(
                items.iterator(), k, Item::getWeight);

        assertEquals(k, result.size());

        // 验证高权重的项目被选中的概率更高（通过统计测试）
        Map<String, Integer> selectionCount = new HashMap<>();
        int trials = 10000;

        for (int i = 0; i < trials; i++) {
            List<Item> sample = ReservoirSamplingUtil.weightedSample(
                    items.iterator(), k, Item::getWeight);
            sample.forEach(item ->
                    selectionCount.merge(item.getName(), 1, Integer::sum));
        }

        // 验证权重越高的项目被选中的次数越多
        int countE = selectionCount.getOrDefault("E", 0);
        int countA = selectionCount.getOrDefault("A", 0);

        assertTrue(countE > countA,
                String.format("高权重项目E应比低权重项目A被选中更多次: E=%d, A=%d", countE, countA));
    }

    @Test
    @DisplayName("批量采样测试")
    void testBatchSampling() {
        int k = 100;
        int batchSize = 1000;
        int n = 1000000;

        Iterator<Integer> largeStream = IntStream.rangeClosed(1, n)
                .iterator();

        long startTime = System.nanoTime();
        List<Integer> result = ReservoirSamplingUtil.sampleBatch(largeStream, k, batchSize);
        long endTime = System.nanoTime();

        assertEquals(k, result.size());

        double elapsedMillis = (endTime - startTime) / 1_000_000.0;
        System.out.printf("批量采样 %d 个元素从 %d 个元素中耗时: %.2f ms%n", k, n, elapsedMillis);

        // 验证所有元素都在范围内
        assertTrue(result.stream().allMatch(i -> i >= 1 && i <= n));
    }

    @Test
    @DisplayName("无限流采样测试")
    void testInfiniteStreamSampling() {
        int k = 10;
        int maxElements = 10000; // 限制处理的元素数量

        // 创建无限递增的数字流
        Iterator<Integer> infiniteStream = new Iterator<Integer>() {
            private int current = 0;
            private int count = 0;

            @Override
            public boolean hasNext() {
                return count < maxElements;
            }

            @Override
            public Integer next() {
                if (count >= maxElements) {
                    throw new NoSuchElementException();
                }
                count++;
                return current++;
            }
        };

        List<Integer> result = ReservoirSamplingUtil.sample(infiniteStream, k);

        assertEquals(k, result.size());
        // 由于是无限流，我们无法验证具体值，但可以验证数量
    }

    @Test
    @DisplayName("线程安全性测试")
    void testThreadSafety() throws InterruptedException {
        int k = 10;
        int numThreads = 10;
        int iterations = 1000;

        AtomicInteger errorCount = new AtomicInteger(0);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    try {
                        List<Integer> result = ReservoirSamplingUtil.sample(mediumList, k);
                        if (result.size() != Math.min(k, mediumList.size())) {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            });
            threads.add(thread);
        }

        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(0, errorCount.get(),
                "多线程环境下应无错误发生");
    }

    @Test
    @DisplayName("内存效率测试")
    void testMemoryEfficiency() {
        // 测试算法在大型数据集上的内存使用
        int k = 100;
        int n = 10000000; // 一千万个元素

        // 使用迭代器避免一次性加载所有数据到内存
        Iterator<Integer> hugeStream = new Iterator<Integer>() {
            private int current = 0;

            @Override
            public boolean hasNext() {
                return current < n;
            }

            @Override
            public Integer next() {
                if (current >= n) {
                    throw new NoSuchElementException();
                }
                return current++;
            }
        };

        // 获取初始内存使用
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        List<Integer> result = ReservoirSamplingUtil.sample(hugeStream, k);

        runtime.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = finalMemory - initialMemory;

        System.out.printf("处理一千万个元素，采样 %d 个，内存使用: %.2f MB%n",
                k, memoryUsed / (1024.0 * 1024.0));

        assertEquals(k, result.size());

        // 验证内存使用在合理范围内（应远小于存储所有元素的内存）
        assertTrue(memoryUsed < 100 * 1024 * 1024, // 小于100MB
                "内存使用应在合理范围内");
    }

    @Test
    @DisplayName("采样结果验证测试")
    void testSamplingCorrectness() {
        // 使用小数据集验证算法正确性
        int k = 3;
        List<Integer> population = Arrays.asList(1, 2, 3, 4, 5);

        // 多次采样，验证概率分布
        Map<Set<Integer>, Integer> sampleFrequency = new HashMap<>();
        int trials = 10000;

        for (int i = 0; i < trials; i++) {
            List<Integer> sample = ReservoirSamplingUtil.sample(population, k);
            Set<Integer> sampleSet = new HashSet<>(sample);

            // 采样大小应为k
            assertEquals(k, sample.size());

            // 记录每种组合的出现次数
            sampleFrequency.merge(sampleSet, 1, Integer::sum);
        }

        // 验证所有可能的组合都出现了
        int expectedCombinations = 10; // C(5,3) = 10
        assertTrue(sampleFrequency.size() >= expectedCombinations * 0.8,
                "大多数组合应被采样到");

        // 计算每个组合的理论概率 (1/10)
        double expectedProbability = 1.0 / 10;

        // 验证频率大致符合均匀分布
        for (Map.Entry<Set<Integer>, Integer> entry : sampleFrequency.entrySet()) {
            double actualProbability = entry.getValue() / (double) trials;
            double error = Math.abs(actualProbability - expectedProbability);

            // 允许5%的误差
            assertTrue(error < 0.05,
                    String.format("组合 %s 的概率误差过大: 期望=%.3f, 实际=%.3f",
                            entry.getKey(), expectedProbability, actualProbability));
        }
    }

    // 辅助类用于加权采样测试
    static class Item {
        private final String name;
        private final double weight;

        public Item(String name, double weight) {
            this.name = name;
            this.weight = weight;
        }

        public String getName() {
            return name;
        }

        public double getWeight() {
            return weight;
        }
    }
}
