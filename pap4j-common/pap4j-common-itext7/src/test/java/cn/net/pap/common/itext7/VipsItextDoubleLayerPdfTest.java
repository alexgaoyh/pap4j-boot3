package cn.net.pap.common.itext7;

import cn.net.pap.common.vips.VipsImageProcessor;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.Property;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h3>libvips + iText 7 高性能双层 PDF 生产对比单元测试</h3>
 *
 * <p><b>本测试目的：</b></p>
 * <p>在同一维度上，对比“libvips 纯内存字节流管道”与“传统 Java ImageIO 管道”在处理 TIFF 格式与 JPEG 格式图像时的性能与堆内存消耗。</p>
 *
 * <p><b>核心设计原则与可靠性保障：</b></p>
 * <ul>
 *   <li><b>1. JVM 预热 (Warm-up)</b>：在计时测量前，TIFF 与 JPEG 的管道均进行 5 次预热运行。用以消除 JRE 类加载、JIT 即时编译优化以及 libvips 首次初始化 和 JNA 本地映射带来的初次调用延迟。</li>
 *   <li><b>2. 堆内存精确统计 (ThreadMXBean)</b>：使用 {@link ThreadMXBean} 统计测试线程生命周期内累计向 JVM 堆申请分配的字节量，避免垃圾回收（GC）异步触发时机对测量结果的干扰，保障数据高度可复现。</li>
 *   <li><b>3. JPEG 无解压直通 (/DCTDecode) 原理</b>：PDF 格式原生支持 JPEG 编码。iText 7 在载入 JPEG 字节流时，仅解析文件头获取图片尺寸，<b>完全不需要在 JVM 堆内存中解压像素点阵</b>，而是直接将压缩字节流写入 PDF。因此其堆分配极小 (~0.44MB)。而 TIFF 格式则必须在堆内完全解压出原始像素点阵并重压缩，造成较大开销 (~51MB)。</li>
 * </ul>
 *
 * <p><b>基准测试实测数据对比 (3000x3000px 彩色图像，5 次 JVM 预热，本地同一环境实测)：</b></p>
 * <table border="1">
 *   <tr>
 *     <th>格式类型</th>
 *     <th>处理管道</th>
 *     <th>JVM 堆内存累计分配</th>
 *     <th>单次运行耗时</th>
 *     <th>生成的 PDF 大小</th>
 *   </tr>
 *   <tr>
 *     <td rowspan="2"><b>TIFF 格式</b></td>
 *     <td>传统 Java ImageIO 管道</td>
 *     <td>189.74 MB</td>
 *     <td>487 毫秒</td>
 *     <td>50,687 字节</td>
 *   </tr>
 *   <tr>
 *     <td>libvips 纯内存堆外管道</td>
 *     <td>51.81 MB</td>
 *     <td>211 毫秒</td>
 *     <td>50,687 字节</td>
 *   </tr>
 *   <tr>
 *     <td rowspan="2"><b>JPEG 格式</b></td>
 *     <td>传统 Java ImageIO 管道</td>
 *     <td>79.45 MB</td>
 *     <td>197 毫秒</td>
 *     <td>183,225 字节</td>
 *   </tr>
 *   <tr>
 *     <td>libvips 纯内存堆外管道</td>
 *     <td>0.44 MB</td>
 *     <td>27 毫秒</td>
 *     <td>183,397 字节</td>
 *   </tr>
 * </table>
 */
public class VipsItextDoubleLayerPdfTest {

    private static final Logger log = LoggerFactory.getLogger(VipsItextDoubleLayerPdfTest.class);

    @TempDir
    static File tempDir;

    private static File testTiffFile;
    private static File testJpgFile;
    private static final int IMAGE_WIDTH = 3000;
    private static final int IMAGE_HEIGHT = 3000;
    private static final int WARMUP_RUNS = 5;
    private static boolean vipsAvailable = false;

    @BeforeAll
    public static void setUp() throws Exception {
        log.info("====== 1. 初始化 libvips 并创建 3000x3000x3 高清测试源图 ======");
        try {
            VipsImageProcessor.ensureInitialized();
            vipsAvailable = true;
            log.info("libvips 预初始化完成");
        } catch (Throwable t) {
            log.error("[Vips-Test-Setup] 无法初始化 libvips 本地库，测试类将被跳过。错误详情: ", t);
        }

        // 若 libvips 环境不可用，则跳过测试
        org.junit.jupiter.api.Assumptions.assumeTrue(vipsAvailable,
                "当前环境缺少 libvips 本地动态库，跳过 VipsItextDoubleLayerPdfTest");

        // 动态创建一张 3000x3000x3 的带细节的 RGB 彩色图片，模拟历史典籍扫描件
        BufferedImage img = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        g.setColor(Color.RED);
        g.drawRect(50, 50, IMAGE_WIDTH - 100, IMAGE_HEIGHT - 100);
        g.setColor(Color.BLACK);
        g.drawString("古籍文献测试 - Digital Humanities Archive Sample", 200, 300);
        g.drawLine(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        g.dispose();

        // 保存 TIFF 源图
        testTiffFile = new File(tempDir, "source_archive.tif");

        // 优先获取 TIFF 的 ImageWriter 写入格式
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
        if (writers.hasNext()) {
            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(testTiffFile)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                // 启用 LZW 压缩保存以模拟真实的归档 TIF 文件大小
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionType("LZW");
                }
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
            log.info("成功创建并压缩保存高清归档彩色 TIFF 图片：{}，大小: {} 字节",
                    testTiffFile.getAbsolutePath(), testTiffFile.length());
        } else {
            // 降级使用 PNG 格式写入以防极少数精简环境不支持 TIFF ImageIO 写
            log.warn("当前环境 JRE 缺乏 TIFF ImageIO Writer，降级保存为 PNG 进行测试");
            testTiffFile = new File(tempDir, "source_archive.png");
            ImageIO.write(img, "png", testTiffFile);
        }

        // 保存 JPEG 源图
        testJpgFile = new File(tempDir, "source_archive.jpg");
        ImageIO.write(img, "jpg", testJpgFile);
        log.info("成功创建高清 JPEG 图片：{}，大小: {} 字节",
                testJpgFile.getAbsolutePath(), testJpgFile.length());

        // 2. 预热，排除类加载与首次连接耗时
        log.info("====== 2. 开始执行 {} 次 JVM 预热，消除 JIT 及 JNA 初次加载干扰 ======", WARMUP_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            runVipsPipeline(new File(tempDir, "warmup_vips_tiff_" + i + ".pdf"));
            runJavaPipeline(new File(tempDir, "warmup_java_tiff_" + i + ".pdf"));
            runVipsJpgPipeline(new File(tempDir, "warmup_vips_jpg_" + i + ".pdf"));
            runJavaJpgPipeline(new File(tempDir, "warmup_java_jpg_" + i + ".pdf"));
        }
        log.info("====== 预热完成，JVM 已处于最优热点编译状态 ======");
    }

    @AfterAll
    public static void tearDown() {
        VipsImageProcessor.shutdown();
    }

    @Test
    public void testVipsPlusItextTiffPipeline() throws Exception {
        log.info("====== 开始测试: libvips TIFF 纯内存堆外管道 ======");

        // 强行回收并让线程静置以获取准确的内存基线
        triggerGc();
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long bytesBefore = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());
        long startTime = System.nanoTime();

        File outputPdf = new File(tempDir, "vips_output_double_layer.pdf");
        byte[] pdfBytes = runVipsPipeline(outputPdf);

        long endTime = System.nanoTime();
        long bytesAfter = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());

        long durationMs = (endTime - startTime) / 1000000;
        long memDeltaBytes = Math.max(0, bytesAfter - bytesBefore);

        log.info("[Vips-TIFF-Pipeline] 正式运行耗时: {} 毫秒", durationMs);
        log.info("[Vips-TIFF-Pipeline] JVM 堆内存累计分配: {} MB", String.format("%.2f", memDeltaBytes / (1024.0 * 1024.0)));
        log.info("[Vips-TIFF-Pipeline] 生成的 PDF 大小: {} 字节", outputPdf.length());

        assertNotNull(pdfBytes);
        assertTrue(outputPdf.exists());
    }

    @Test
    public void testStandardJavaPlusItextTiffPipeline() throws Exception {
        log.info("====== 开始测试: 传统 Java ImageIO TIFF 管道 ======");

        // 强行回收并让线程静置以获取准确的内存基线
        triggerGc();
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long bytesBefore = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());
        long startTime = System.nanoTime();

        File outputPdf = new File(tempDir, "java_output_double_layer.pdf");
        byte[] pdfBytes = runJavaPipeline(outputPdf);

        long endTime = System.nanoTime();
        long bytesAfter = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());

        long durationMs = (endTime - startTime) / 1000000;
        long memDeltaBytes = Math.max(0, bytesAfter - bytesBefore);

        log.info("[Java-TIFF-Pipeline] 正式运行耗时: {} 毫秒", durationMs);
        log.info("[Java-TIFF-Pipeline] JVM 堆内存累计分配: {} MB", String.format("%.2f", memDeltaBytes / (1024.0 * 1024.0)));
        log.info("[Java-TIFF-Pipeline] 生成的 PDF 大小: {} 字节", outputPdf.length());

        assertNotNull(pdfBytes);
        assertTrue(outputPdf.exists());
    }

    @Test
    public void testVipsPlusItextJpgPipeline() throws Exception {
        log.info("====== 开始测试: libvips JPEG 纯内存堆外管道 ======");

        triggerGc();
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long bytesBefore = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());
        long startTime = System.nanoTime();

        File outputPdf = new File(tempDir, "vips_output_double_layer_jpg.pdf");
        byte[] pdfBytes = runVipsJpgPipeline(outputPdf);

        long endTime = System.nanoTime();
        long bytesAfter = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());

        long durationMs = (endTime - startTime) / 1000000;
        long memDeltaBytes = Math.max(0, bytesAfter - bytesBefore);

        log.info("[Vips-JPG-Pipeline] 正式运行耗时: {} 毫秒", durationMs);
        log.info("[Vips-JPG-Pipeline] JVM 堆内存累计分配: {} MB", String.format("%.2f", memDeltaBytes / (1024.0 * 1024.0)));
        log.info("[Vips-JPG-Pipeline] 生成的 PDF 大小: {} 字节", outputPdf.length());

        assertNotNull(pdfBytes);
        assertTrue(outputPdf.exists());
    }

    @Test
    public void testStandardJavaPlusItextJpgPipeline() throws Exception {
        log.info("====== 开始测试: 传统 Java ImageIO JPEG 管道 ======");

        triggerGc();
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long bytesBefore = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());
        long startTime = System.nanoTime();

        File outputPdf = new File(tempDir, "java_output_double_layer_jpg.pdf");
        byte[] pdfBytes = runJavaJpgPipeline(outputPdf);

        long endTime = System.nanoTime();
        long bytesAfter = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());

        long durationMs = (endTime - startTime) / 1000000;
        long memDeltaBytes = Math.max(0, bytesAfter - bytesBefore);

        log.info("[Java-JPG-Pipeline] 正式运行耗时: {} 毫秒", durationMs);
        log.info("[Java-JPG-Pipeline] JVM 堆内存累计分配: {} MB", String.format("%.2f", memDeltaBytes / (1024.0 * 1024.0)));
        log.info("[Java-JPG-Pipeline] 生成的 PDF 大小: {} 字节", outputPdf.length());

        assertNotNull(pdfBytes);
        assertTrue(outputPdf.exists());
    }

    /**
     * libvips 纯内存流式处理管线：
     * 读取 TIFF 文件 -> 堆外流式预处理 -> C 内存直接编码为 TIFF 字节 -> JNA 拷贝为 Java byte[] -> iText 7 生成双层 PDF (不进行磁盘中转)
     */
    private static byte[] runVipsPipeline(File outputPdf) throws Exception {
        // 1. 调用 vips 本地堆外处理。无裁剪缩放，色彩通道保持不变，输出为标准的 tiff 格式字节流
        byte[] tiffBytes = VipsImageProcessor.processImage(
                testTiffFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0,
                "0",
                "default",
                "tiff"
        );

        // 2. 借助 iText 7 将 tiff 字节流在内存中构建成 PDF 并在相同坐标上绘制 Mock OCR 透明文本层
        generateDoubleLayerPdf(tiffBytes, outputPdf.getAbsolutePath());

        return tiffBytes;
    }

    /**
     * 传统 Java ImageIO 处理管线：
     * 读取 TIFF 文件 -> 完全解压为 JVM 堆内 BufferedImage -> 堆内重新编码为 TIFF 字节数组 -> iText 7 生成双层 PDF
     */
    private static byte[] runJavaPipeline(File outputPdf) throws Exception {
        // 1. 完全加载大图并解压缩至 JVM 堆内
        BufferedImage img = ImageIO.read(testTiffFile);
        if (img == null) {
            throw new IOException("Java ImageIO 无法解码测试图片: " + testTiffFile.getAbsolutePath());
        }

        byte[] tiffBytes;
        // 2. 使用 ImageIO ImageWriter 对其重新编码压缩为 TIFF 字节
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
            if (!writers.hasNext()) {
                throw new IOException("未找到 TIFF ImageWriter");
            }
            ImageWriter writer = writers.next();
            writer.setOutput(ios);
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionType("LZW");
                }
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
        }
        tiffBytes = baos.toByteArray();

        // 3. 同样的 iText 7 写入双层 PDF
        generateDoubleLayerPdf(tiffBytes, outputPdf.getAbsolutePath());

        return tiffBytes;
    }

    private static byte[] runVipsJpgPipeline(File outputPdf) throws Exception {
        byte[] jpgBytes = VipsImageProcessor.processImage(
                testJpgFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0,
                "0",
                "default",
                "jpg"
        );
        generateDoubleLayerPdf(jpgBytes, outputPdf.getAbsolutePath());
        return jpgBytes;
    }

    private static byte[] runJavaJpgPipeline(File outputPdf) throws Exception {
        BufferedImage img = ImageIO.read(testJpgFile);
        if (img == null) {
            throw new IOException("Java ImageIO 无法解码测试图片: " + testJpgFile.getAbsolutePath());
        }

        byte[] jpgBytes;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        jpgBytes = baos.toByteArray();

        generateDoubleLayerPdf(jpgBytes, outputPdf.getAbsolutePath());
        return jpgBytes;
    }

    private static void generateDoubleLayerPdf(byte[] imageBytes, String outputPdfPath) throws Exception {
        ImageData imageData = ImageDataFactory.create(imageBytes);
        Image image = new Image(imageData);

        try (PdfWriter writer = new PdfWriter(outputPdfPath);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc)) {

            // 图像大小与页面大小保持 1:1 的 Points 对齐以简化坐标换算
            PageSize pageSize = new PageSize(IMAGE_WIDTH, IMAGE_HEIGHT);
            pdfDoc.setDefaultPageSize(pageSize);

            // 绘制底层的 背景图
            image.setFixedPosition(0, 0);
            image.scaleToFit(IMAGE_WIDTH, IMAGE_HEIGHT);
            doc.add(image);

            // 绘制上层的透明文字 (Mock OCR 文本，保证可搜索与可复制，但视觉不可见)
            // 采用 iText 7 的 INVISIBLE 渲染模式实现真正的双层 PDF 效果
            Paragraph mockOcrText = new Paragraph("数字人文古籍正文样本 (OCR 可检索文本)")
                    .setFixedPosition(1, 200, IMAGE_HEIGHT - 400, 800)
                    .setFontSize(36);
            mockOcrText.setProperty(Property.TEXT_RENDERING_MODE, PdfCanvasConstants.TextRenderingMode.INVISIBLE);
            doc.add(mockOcrText);

            Paragraph mockOcrText2 = new Paragraph("Additional Metadata Layer for Archiving")
                    .setFixedPosition(1, 200, IMAGE_HEIGHT - 600, 800)
                    .setFontSize(28);
            mockOcrText2.setProperty(Property.TEXT_RENDERING_MODE, PdfCanvasConstants.TextRenderingMode.INVISIBLE);
            doc.add(mockOcrText2);
        }
    }

    private static void triggerGc() {
        System.gc();
        System.runFinalization();
        System.gc();
        try {
            Thread.sleep(300); // 暂定 300 毫秒让内存回收稳定
        } catch (InterruptedException ignored) {
        }
    }

}
