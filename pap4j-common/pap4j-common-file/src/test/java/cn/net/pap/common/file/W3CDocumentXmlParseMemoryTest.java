package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.input.BOMInputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * org.w3c.dom.Document 大文件解析内存溢出（OOM）隐患排查与流式优化验证
 * 1. 问题背景 (Background)
 * 在 Review XmlParseUtil 的底层文件解析逻辑（getDocumentByPath / getDocumentByContent）时，发现原代码存在严重的**“内存双重冗余拷贝”**问题。
 * 处理大文件时，系统会先将文件全量加载为 Java String（UTF-16 编码导致内存翻倍），随后又强制调用 getBytes() 生成完整的 byte[] 数组。
 * 这种非流式的处理方式在解析 50MB 以上的大型 XML 时，会在喂给 DOM 解析器之前就额外吃掉近 200MB 内存。
 * 在高并发场景下极易触发频繁的 Full GC 甚至引发 OutOfMemoryError。
 * <p>
 * 2. 优化与测试方案设计 (Solution & Test Design)
 * 代码重构：将文件读取和字符串读取全面重构为纯流式处理（Zero-copy 零拷贝）。
 * 使用 InputStream 配合 BOMInputStream（处理 BOM 头），以及 StringReader 配合 InputSource，让解析器直接消费底层流，彻底消除中间无用的缓冲变量。
 * <p>
 * 基准测试验证 (XmlParseMemoryTest)：
 * <p>
 * 造数逻辑：使用 @TempDir 动态生成包含 100 万个子节点、大小约 50MB - 60MB 的极端 XML 测试文件。
 * <p>
 * 精准监控：为了排除垃圾回收（GC）的干扰，未采用粗糙的 Runtime.freeMemory()，而是引入 JVM 底层的 ThreadMXBean.getThreadAllocatedBytes()，精确测量方法执行周期内真正在堆上分配的对象内存总量。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class W3CDocumentXmlParseMemoryTest {
    private static final Logger log = LoggerFactory.getLogger(W3CDocumentXmlParseMemoryTest.class);

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
    private static Path largeXmlPath;

    @BeforeAll
    static void setUp(@TempDir Path tempDir) throws IOException {
        // 1. 生成一个约 50MB 的大型 XML 文件用于测试
        largeXmlPath = tempDir.resolve("large_test.xml");
        log.info("正在生成大型 XML 测试文件...");

        try (BufferedWriter writer = Files.newBufferedWriter(largeXmlPath, StandardCharsets.UTF_8)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<root>\n");
            // 写入 100 万个子节点，大约会生成 40-50MB 的文件
            for (int i = 0; i < 1000000; i++) {
                writer.write("    <item id=\"" + i + "\">这是一段用于测试内存消耗的文本内容 " + i + "</item>\n");
            }
            writer.write("</root>");
        }

        long fileSizeMb = Files.size(largeXmlPath) / (1024 * 1024);
        log.info("{}", "测试文件生成完毕，大小: " + fileSizeMb + " MB");
        log.info("--------------------------------------------------");
    }

    @Test
    @Order(1)
    @DisplayName("测试原方法 (String 缓冲机制) 的内存消耗")
    void testOldMethodMemory() throws Exception {
        long allocatedBefore = getThreadAllocatedBytes();
        long startTime = System.currentTimeMillis();

        Document doc = getDocumentByPathOld(largeXmlPath.toString());

        long allocatedAfter = getThreadAllocatedBytes();
        long timeTaken = System.currentTimeMillis() - startTime;

        assertNotNull(doc);
        printStats("原方法 (String读入)", allocatedAfter - allocatedBefore, timeTaken);
    }

    @Test
    @Order(2)
    @DisplayName("测试新方法 (纯流式读取) 的内存消耗")
    void testNewMethodMemory() throws Exception {
        long allocatedBefore = getThreadAllocatedBytes();
        long startTime = System.currentTimeMillis();

        Document doc = getDocumentByPathNew(largeXmlPath.toString());

        long allocatedAfter = getThreadAllocatedBytes();
        long timeTaken = System.currentTimeMillis() - startTime;

        assertNotNull(doc);
        printStats("改进方法 (纯流式)", allocatedAfter - allocatedBefore, timeTaken);
    }

    // ================== 辅助方法 ==================

    private void printStats(String methodName, long allocatedBytes, long timeTakenMs) {
        double allocatedMb = allocatedBytes / (1024.0 * 1024.0);
        log.info(String.format("[%s] 执行完毕:\n", methodName));
        log.info(String.format("  - 耗时: %d ms\n", timeTakenMs));
        log.info(String.format("  - 期间分配的内存总量: %.2f MB\n", allocatedMb));
        log.info("--------------------------------------------------");
    }

    /**
     * 获取当前线程累计分配的对象内存大小 (精确计算，不受 GC 影响)
     */
    private long getThreadAllocatedBytes() {
        try {
            com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            return threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());
        } catch (Exception e) {
            System.err.println("当前 JVM 不支持 ThreadMXBean 内存分配监控");
            return 0;
        }
    }

    // ================== 待测试的两个方法 ==================

    public static Document getDocumentByPathOld(String filePath) throws IOException {
        try {
            String xmlContent = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
            xmlContent = xmlContent.startsWith("\uFEFF") ? xmlContent.substring(1) : xmlContent;
            var builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            return builder.parse(new java.io.ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("Failed to parse XML document", e);
        }
    }

    public static Document getDocumentByPathNew(String filePath) throws IOException {
        try {
            DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            try (InputStream is = new BufferedInputStream(Files.newInputStream(Path.of(filePath))); BOMInputStream bomIn = BOMInputStream.builder().setInputStream(is).get()) {
                return builder.parse(bomIn);
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse XML document", e);
        }
    }

}
