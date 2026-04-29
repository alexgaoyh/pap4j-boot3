package cn.net.pap.common.datastructure.chroniclemap;

import cn.net.pap.common.datastructure.chroniclemap.dto.ValueDataDTO;
import cn.net.pap.common.datastructure.chroniclemap.dto.ValueDataNeighborsDTO;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.WireType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 添加 JVM
 * --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
 * --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
 * --add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
 * --add-exports jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
 * --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
 * --add-opens java.base/java.lang=ALL-UNNAMED
 * --add-opens java.base/java.lang.reflect=ALL-UNNAMED
 * --add-opens java.base/java.io=ALL-UNNAMED
 * --add-opens java.base/java.util=ALL-UNNAMED
 * --add-opens java.base/java.nio=ALL-UNNAMED
 * --add-opens java.base/sun.nio.ch=ALL-UNNAMED
 */
public class ChronicleMapTest {

    private ChronicleMap<Long, long[]> graphMap;
    private File persistenceFile;

    @TempDir
    Path tempDir;

    // @BeforeEach
    void setUp() throws IOException {
        // 创建一个持久化文件，模拟磁盘存储
        persistenceFile = tempDir.resolve("graph_data.dat").toFile();

        // 核心配置
        graphMap = ChronicleMap.of(Long.class, long[].class).name("test-graph-map").entries(1000)               // 预估最大节点数
                .averageValueSize(8 * 10)    // 预估每个节点平均有 10 个邻居 (long=8字节)
                .createPersistedTo(persistenceFile);
    }

    // @AfterEach
    void tearDown() {
        if (graphMap != null) {
            graphMap.close();
        }
    }

    // @Test
    void testGraphEdgeStorageAndPersistence() throws IOException {
        // 1. 模拟存入一个图的边信息
        // 节点 1 指向节点 2 和 3
        long nodeId = 1L;
        long[] neighbors = {2L, 3L};
        graphMap.put(nodeId, neighbors);

        // 2. 立即读取验证
        assertTrue(graphMap.containsKey(1L));
        assertArrayEquals(new long[]{2L, 3L}, graphMap.get(1L));

        // 3. 模拟“关闭并重新加载”，验证磁盘持久化特性
        graphMap.close();

        // 重新打开同一个文件
        try (ChronicleMap<Long, long[]> reopenedMap = ChronicleMap.of(Long.class, long[].class).createPersistedTo(persistenceFile)) {

            assertNotNull(reopenedMap.get(1L));
            assertArrayEquals(new long[]{2L, 3L}, reopenedMap.get(1L));
            System.out.println("持久化验证成功：即使关闭后，节点 1 的邻居依然存在。");
        }
    }

    // @Test
    void testUpdateNeighbors() {
        // 模拟给已有节点添加新的邻居（需覆盖旧数组）
        long nodeId = 100L;
        graphMap.put(nodeId, new long[]{101L});

        // 更新：添加 102L
        long[] existing = graphMap.get(nodeId);
        long[] updated = new long[existing.length + 1];
        System.arraycopy(existing, 0, updated, 0, existing.length);
        updated[updated.length - 1] = 102L;

        graphMap.put(nodeId, updated);

        assertEquals(2, graphMap.get(100L).length);
        assertArrayEquals(new long[]{101L, 102L}, graphMap.get(100L));
    }

    // @Test
    @org.junit.jupiter.api.Disabled("Requires local environment/dataset")
    void testTenMillionNodes() throws IOException {
        long entries = 10_000_000L; // 一千万个节点
        int avgNeighbors = 10;      // 每个节点平均 10 个邻居

        // 1. 定义存储文件（确保磁盘有 2GB+ 空间）
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("large_graph_test", ".dat");
        File file = tempFile.toFile();
        if (file.exists()) {
            file.delete();
        }

        System.out.println("正在初始化海量存储容器...");
        long startTime = System.currentTimeMillis();

        try (ChronicleMap<Long, long[]> graph = ChronicleMap.of(Long.class, long[].class).name("ultra-graph").entries(entries).averageValueSize(avgNeighbors * 8) // 预估 Value 大小
                .createPersistedTo(file)) {

            System.out.println("初始化完成，耗时: " + (System.currentTimeMillis() - startTime) + "ms");

            // 2. 压力测试：高速写入千万条数据
            System.out.println("开始批量写入 10,000,000 条边...");
            long writeStart = System.currentTimeMillis();

            for (long i = 0; i < entries; i++) {
                long[] neighbors = new long[avgNeighbors];
                for (int j = 0; j < avgNeighbors; j++) {
                    neighbors[j] = i + j; // 模拟连接
                }
                graph.put(i, neighbors);

                if (i % 1_000_000 == 0 && i > 0) {
                    System.out.println("已处理: " + i + " 个节点...");
                }
            }

            System.out.println("写入千万条记录完成，耗时: " + (System.currentTimeMillis() - writeStart) + "ms");

            // 3. 读取验证：随机抽样
            System.out.println("随机抽样读取验证...");
            long testId = 5_555_555L;
            long[] result = graph.get(testId);
            System.out.println("节点 " + testId + " 的邻居: " + Arrays.toString(result));

            // 4. 验证磁盘占用
            System.out.println("磁盘映射文件大小: " + (file.length() / 1024 / 1024) + " MB");
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    // @Test
    @org.junit.jupiter.api.Disabled("Requires local environment/dataset")
    void customerValueBatchTest() throws IOException {
        // 定义要写入的数据条目数
        final int ENTRY_COUNT = 5;
        // 存储写入时的邻居索引，用于稍后的批量验证
        final Map<Long, Long> expectedNeighborsIndices = new HashMap<>();

        java.nio.file.Path mapTempFile = java.nio.file.Files.createTempFile("chromicle_map_customer_batch", ".dat");
        java.nio.file.Path queueTempDir = java.nio.file.Files.createTempDirectory("chromicle_queue_customer_batch");

        try {
            // 1. 初始化 ChronicleMap (存储 ValueDataDTO)
            try (ChronicleMap<Long, ValueDataDTO> map = ChronicleMap
                    .of(Long.class, ValueDataDTO.class)
                    .name("value-data-map-batch")
                    .averageValue(new ValueDataDTO())
                    .entries(ENTRY_COUNT * 2) // 预期条目数
                    .createPersistedTo(mapTempFile.toFile())) {
    
                // 2. 初始化 ChronicleQueue (存储 ValueDataNeighborsDTO)
                try (ChronicleQueue queue = SingleChronicleQueueBuilder.builder(queueTempDir.toFile(), WireType.BINARY).blockSize(1024*64).build()) {
    
                    // --- 3. 批量写入数据到 Queue 和 Map ---
                    System.out.println("--- 3. 开始批量写入数据 ---");
    
                    for (long i = 1; i <= ENTRY_COUNT; i++) {
                        // 3.1 准备邻居数据 (Neighbors Data)
                        ValueDataNeighborsDTO neighborsData = new ValueDataNeighborsDTO();
                        // 邻居 ID 根据主键 i 偏移
                        neighborsData.neighbors = new long[]{1000L + i, 2000L + i, 3000L + i};
    
                        // 3.2 写入邻居数据到 Queue
                        ExcerptAppender appender = queue.createAppender();
                        // 记录写入时的索引
                        appender.writeBytes(out -> neighborsData.writeMarshallable(out));
                        long neighborsIndex = appender.lastIndexAppended();
    
                        // 存储 Key 和 索引，用于后续验证
                        expectedNeighborsIndices.put(i, neighborsIndex);
    
                        // 3.3 准备主数据 (Main Data)
                        Long primaryKey = i;
                        ValueDataDTO mainData = new ValueDataDTO();
                        mainData.label = (int) (i * 10); // 标签根据 i 变化
                        mainData.weight = i * 0.1;      // 权重根据 i 变化
                        mainData.timestamp = System.currentTimeMillis();
                        // 设置 Queue 索引
                    mainData.neighborsIndex = neighborsIndex;

                    // 3.4 写入主数据到 Map
                    map.put(primaryKey, mainData);

                    System.out.printf("写入数据: Key=%d, Label=%d, QueueIndex=%d%n", primaryKey, mainData.label, neighborsIndex);
                }

                // --- 4. 批量读取数据并验证 ---
                System.out.println("\n--- 4. 开始批量读取和验证数据 ---");

                ExcerptTailer tailer = queue.createTailer();

                for (long i = 1; i <= ENTRY_COUNT; i++) {
                    Long primaryKey = i;

                    // 4.1 从 Map 读取主数据 (Main Data)
                    ValueDataDTO retrievedMainData = map.get(primaryKey);

                    // 验证 Map 中的主数据
                    assertNotNull(retrievedMainData, "Map中应能读取到 Key=" + primaryKey + " 的主数据");
                    assertEquals((int) (i * 10), retrievedMainData.label, "Label 字段值应匹配");
                    assertEquals(i * 0.1, retrievedMainData.weight, 0.001, "Weight 字段值应匹配");

                    // 获取并验证索引
                    long expectedIndex = expectedNeighborsIndices.get(primaryKey);
                    assertEquals(expectedIndex, retrievedMainData.neighborsIndex, "NeighborsIndex 索引值应匹配");

                    // 4.2 使用索引从 Queue 读取关联的邻居数据 (Neighbors Data)
                    long retrievedNeighborsIndex = retrievedMainData.neighborsIndex;

                    // 移动到指定的索引位置
                    boolean success = tailer.moveToIndex(retrievedNeighborsIndex);
                    assertEquals(true, success, "应能成功移动到 neighborsIndex=" + retrievedNeighborsIndex + " 的索引位置");

                    ValueDataNeighborsDTO retrievedNeighborsData = new ValueDataNeighborsDTO();
                    // 读取数据
                    tailer.readBytes(in -> retrievedNeighborsData.readMarshallable(in));

                    // 验证邻居数据
                    long[] expectedNeighbors = new long[]{1000L + i, 2000L + i, 3000L + i};
                    assertArrayEquals(expectedNeighbors, retrievedNeighborsData.neighbors, "Neighbors 数组内容应匹配");

                    System.out.printf("成功验证 Key=%d, Label=%d, Neighbors=%s%n",
                            primaryKey,
                            retrievedMainData.label,
                            java.util.Arrays.toString(retrievedNeighborsData.neighbors));
                }
                System.out.println("--- 所有数据验证成功！ ---");
                }
            }
        } finally {
            java.nio.file.Files.deleteIfExists(mapTempFile);
            if (queueTempDir != null) {
                java.nio.file.Files.walkFileTree(queueTempDir, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        java.nio.file.Files.delete(file);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                    @Override
                    public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc) throws IOException {
                        java.nio.file.Files.delete(dir);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

}
