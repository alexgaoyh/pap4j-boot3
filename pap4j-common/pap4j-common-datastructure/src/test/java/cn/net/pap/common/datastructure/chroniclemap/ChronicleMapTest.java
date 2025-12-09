package cn.net.pap.common.datastructure.chroniclemap;

import net.openhft.chronicle.map.ChronicleMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

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
    void testTenMillionNodes() throws IOException {
        long entries = 10_000_000L; // 一千万个节点
        int avgNeighbors = 10;      // 每个节点平均 10 个邻居

        // 1. 定义存储文件（确保磁盘有 2GB+ 空间）
        File file = new File("D:/large_graph_test.dat");
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
        }
    }

}
