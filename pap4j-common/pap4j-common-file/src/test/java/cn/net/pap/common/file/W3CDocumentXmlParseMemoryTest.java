package cn.net.pap.common.file;

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
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class W3CDocumentXmlParseMemoryTest {

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
    private static Path largeXmlPath;

    @BeforeAll
    static void setUp(@TempDir Path tempDir) throws IOException {
        // 1. 生成一个约 50MB 的大型 XML 文件用于测试
        largeXmlPath = tempDir.resolve("large_test.xml");
        System.out.println("正在生成大型 XML 测试文件...");

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
        System.out.println("测试文件生成完毕，大小: " + fileSizeMb + " MB");
        System.out.println("--------------------------------------------------");
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
        System.out.printf("[%s] 执行完毕:\n", methodName);
        System.out.printf("  - 耗时: %d ms\n", timeTakenMs);
        System.out.printf("  - 期间分配的内存总量: %.2f MB\n", allocatedMb);
        System.out.println("--------------------------------------------------");
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
