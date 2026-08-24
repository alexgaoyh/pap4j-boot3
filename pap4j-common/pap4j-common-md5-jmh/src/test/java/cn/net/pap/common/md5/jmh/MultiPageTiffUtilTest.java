package cn.net.pap.common.md5.jmh;

import cn.net.pap.common.md5.jmh.util.MultiPageTiffUtil;
import cn.net.pap.common.md5.jmh.util.MultiPageTiffUtil.TiffCompression;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MultiPageTiffUtil} 单元测试
 * <p>
 * 覆盖：多页 TIFF 生成（各种压缩方式 / CCITT 二值图 / 多页输入展开 / alpha 白底铺底）、
 * 多页 TIFF 转 JPG 的往返转换（命名、尺寸、位数一致性）、以及非法入参的错误处理。
 */
class MultiPageTiffUtilTest {

    private static final Logger log = LoggerFactory.getLogger(MultiPageTiffUtilTest.class);

    /** 每个测试用例独立的临时目录，@TempDir 会自动清理 */
    @TempDir
    Path tempDir;

    /** 三张不同颜色、不同尺寸的源图（依次为 100x80 红、120x90 绿、140x100 蓝） */
    private List<File> sourceImages;

    @BeforeAll
    static void registerPlugins() {
        // 防御性调用：确保 TwelveMonkeys 的 TIFF 插件已注册（ServiceLoader 一般会自动完成）
        ImageIO.scanForPlugins();
    }

    @BeforeEach
    void setUp() throws IOException {
        // 源图用 PNG（无损）生成，以便对无损压缩的 TIFF 做逐像素断言
        sourceImages = Arrays.asList(
                createSolidImage("page1", 100, 80, Color.RED),
                createSolidImage("page2", 120, 90, Color.GREEN),
                createSolidImage("page3", 140, 100, Color.BLUE)
        );
    }

    // ---------------------------------------------------------------- 多页 TIFF 生成

    @ParameterizedTest(name = "无损压缩 {0}")
    @EnumSource(value = TiffCompression.class, names = {"NONE", "PACK_BITS", "LZW", "ZLIB", "DEFLATE"})
    void createMultiPageTiff_losslessCompressions(TiffCompression compression) throws Exception {
        File output = tempDir.resolve("multi_" + compression.name() + ".tiff").toFile();

        MultiPageTiffUtil.imagesToMultiPageTiff(sourceImages, output, compression);

        assertTrue(output.isFile(), "生成的 TIFF 文件应存在");
        assertTrue(output.length() > 0, "生成的 TIFF 文件不应为空");
        assertEquals(3, countPages(output), "TIFF 应包含 3 页");

        // 无损压缩下像素应逐字节一致
        Color[] expected = {Color.RED, Color.GREEN, Color.BLUE};
        for (int i = 0; i < 3; i++) {
            BufferedImage page = readPage(output, i);
            assertEquals(expected[i].getRGB(), page.getRGB(5, 5), "第 " + (i + 1) + " 页颜色应保持");
        }
        log.info("{} 压缩成功，文件大小 {} bytes", compression, output.length());
    }

    @Test
    void createMultiPageTiff_jpegCompression() throws Exception {
        File output = tempDir.resolve("multi_jpeg.tiff").toFile();

        MultiPageTiffUtil.imagesToMultiPageTiff(sourceImages, output, TiffCompression.JPEG);

        assertTrue(output.isFile());
        assertEquals(3, countPages(output), "JPEG 压缩的 TIFF 应包含 3 页");
        // JPEG 为有损压缩，仅校验尺寸，不校验像素
        assertEquals(100, readPage(output, 0).getWidth());
        assertEquals(140, readPage(output, 2).getWidth());
    }

    @Test
    void createMultiPageTiff_ccittT6Bilevel() throws Exception {
        // CCITT 系列只支持 1-bit 二值图，这里生成两张黑白图
        List<File> bilevel = Arrays.asList(
                createBilevelImage("bw1", 200, 100, Color.BLACK),
                createBilevelImage("bw2", 180, 120, Color.BLACK)
        );
        File output = tempDir.resolve("multi_ccitt_t6.tiff").toFile();

        MultiPageTiffUtil.imagesToMultiPageTiff(bilevel, output, TiffCompression.CCITT_T6);

        assertTrue(output.isFile());
        assertEquals(2, countPages(output), "CCITT T.6 压缩的 TIFF 应包含 2 页");
        assertEquals(200, readPage(output, 0).getWidth());
        assertEquals(120, readPage(output, 1).getHeight());
    }

    @Test
    void createMultiPageTiff_ccittWithColorImageThrows() {
        // CCITT 只支持 1-bit 二值图，彩色图应在写页阶段被显式拦截并给出可读提示
        File output = tempDir.resolve("ccitt_color.tiff").toFile();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MultiPageTiffUtil.imagesToMultiPageTiff(
                        Collections.singletonList(sourceImages.get(0)), output, TiffCompression.CCITT_T6));

        assertTrue(ex.getMessage().contains("CCITT"), "错误信息应指向 CCITT 限制，实际: " + ex.getMessage());
        assertTrue(!output.exists(), "失败时不应留下输出文件");
    }

    @Test
    void createMultiPageTiff_multiPageTiffInputExpandsAllPages() throws Exception {
        // 先手工拼一张 2 页的 TIFF 作为输入
        File seed = tempDir.resolve("seed.tiff").toFile();
        MultiPageTiffUtil.imagesToMultiPageTiff(sourceImages.subList(0, 2), seed, TiffCompression.LZW);

        // 将「多页 TIFF + 一张普通 PNG」合并成 3 页
        File output = tempDir.resolve("merged.tiff").toFile();
        List<File> inputs = Arrays.asList(seed, sourceImages.get(2));

        MultiPageTiffUtil.imagesToMultiPageTiff(inputs, output, TiffCompression.LZW);

        assertEquals(3, countPages(output), "多页 TIFF 输入应按页展开并追加后续图片");
        assertEquals(100, readPage(output, 0).getWidth());
        assertEquals(120, readPage(output, 1).getWidth());
        assertEquals(140, readPage(output, 2).getWidth());
    }

    @Test
    void createMultiPageTiff_alphaSourceFlattenedToWhite() throws Exception {
        // 半透明底 + 红色方块，验证文档承诺的「alpha 白底铺底」行为
        File alpha = createAlphaImage("alpha", 80, 60);
        File output = tempDir.resolve("alpha_flattened.tiff").toFile();

        MultiPageTiffUtil.imagesToMultiPageTiff(Collections.singletonList(alpha), output, TiffCompression.LZW);

        BufferedImage page = readPage(output, 0);
        assertFalse(page.getColorModel().hasAlpha(), "导出 TIFF 不应带 alpha 通道");
        assertEquals(Color.WHITE.getRGB(), page.getRGB(5, 5), "原透明区域应被铺白底");
        assertEquals(Color.RED.getRGB(), page.getRGB(40, 30), "原红色区域应保留");
    }

    @Test
    void createMultiPageTiff_emptyInputThrows() {
        File output = tempDir.resolve("empty.tiff").toFile();

        assertThrows(IllegalArgumentException.class,
                () -> MultiPageTiffUtil.imagesToMultiPageTiff(Collections.emptyList(), output, TiffCompression.LZW));
    }

    @Test
    void createMultiPageTiff_nullItemInInputThrows() {
        File output = tempDir.resolve("null_item.tiff").toFile();
        List<File> listWithNull = Arrays.asList(sourceImages.get(0), null);

        assertThrows(IllegalArgumentException.class,
                () -> MultiPageTiffUtil.imagesToMultiPageTiff(listWithNull, output, TiffCompression.LZW));
    }

    @Test
    void createMultiPageTiff_nonExistingItemInInputThrows() {
        File output = tempDir.resolve("non_existing.tiff").toFile();
        List<File> listWithNonExisting = Arrays.asList(sourceImages.get(0), tempDir.resolve("missing.png").toFile());

        assertThrows(IOException.class,
                () -> MultiPageTiffUtil.imagesToMultiPageTiff(listWithNonExisting, output, TiffCompression.LZW));
    }

    @Test
    void createMultiPageTiff_nullOutputThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MultiPageTiffUtil.imagesToMultiPageTiff(sourceImages, null, TiffCompression.LZW));
    }

    @Test
    void createMultiPageTiff_invalidQualityThrows() {
        File output = tempDir.resolve("quality.tiff").toFile();

        assertThrows(IllegalArgumentException.class,
                () -> MultiPageTiffUtil.imagesToMultiPageTiff(sourceImages, output, TiffCompression.JPEG, 1.5f));
    }

    // ---------------------------------------------------------------- 多页 TIFF 转 JPG

    @Test
    void tiffToJpg_generatesOneJpgPerPageUnderRootDir() throws Exception {
        File multiTiff = tempDir.resolve("multi.tiff").toFile();
        MultiPageTiffUtil.imagesToMultiPageTiff(sourceImages, multiTiff, TiffCompression.LZW);

        File outRoot = tempDir.resolve("jpg_out").toFile();
        // 补零位数为 3，所有导出文件名长度一致
        List<File> jpgs = MultiPageTiffUtil.tiffToJpgs(multiTiff, outRoot, 3);

        assertTrue(outRoot.isDirectory(), "导出根目录应被创建");
        assertEquals(3, jpgs.size(), "应导出与页数相同的 JPG");
        assertEquals(Arrays.asList(
                new File(outRoot, "multi_001.jpg"),
                new File(outRoot, "multi_002.jpg"),
                new File(outRoot, "multi_003.jpg")
        ), jpgs, "导出文件应按 <名称>_<按入参位数补零页码>.jpg 规则命名");

        // 补零位数为 3 时所有文件名长度一致
        assertEquals(1, jpgs.stream().map(f -> f.getName().length()).distinct().count(),
                "同一批导出的文件名长度应一致");

        // 逐页校验尺寸与原图一致（JPEG 保持分辨率）
        int[] widths = {100, 120, 140};
        int[] heights = {80, 90, 100};
        for (int i = 0; i < jpgs.size(); i++) {
            assertTrue(jpgs.get(i).isFile(), "JPG 文件应存在: " + jpgs.get(i));
            BufferedImage img = ImageIO.read(jpgs.get(i));
            assertEquals(widths[i], img.getWidth(), "第 " + (i + 1) + " 页宽度");
            assertEquals(heights[i], img.getHeight(), "第 " + (i + 1) + " 页高度");
        }
    }

    @Test
    void tiffToJpg_customQualityWorks() throws Exception {
        File multiTiff = tempDir.resolve("multi.tiff").toFile();
        MultiPageTiffUtil.imagesToMultiPageTiff(sourceImages, multiTiff, TiffCompression.LZW);

        File outRoot = tempDir.resolve("jpg_lowq").toFile();
        List<File> jpgs = MultiPageTiffUtil.tiffToJpgs(multiTiff, outRoot, 3, 0.4f);

        assertEquals(3, jpgs.size(), "自定义质量导出应生成全部页");
        for (File jpg : jpgs) {
            assertTrue(jpg.isFile(), "JPG 文件应存在: " + jpg);
            BufferedImage img = ImageIO.read(jpg);
            assertTrue(img.getWidth() > 0 && img.getHeight() > 0, "JPG 应可正常解码");
        }
    }

    @Test
    void tiffToJpg_invalidQualityThrows() throws Exception {
        File multiTiff = tempDir.resolve("multi.tiff").toFile();
        MultiPageTiffUtil.imagesToMultiPageTiff(sourceImages, multiTiff, TiffCompression.LZW);

        assertThrows(IllegalArgumentException.class,
                () -> MultiPageTiffUtil.tiffToJpgs(multiTiff, tempDir.resolve("out").toFile(), 3, 1.1f));
    }

    @Test
    void tiffToJpg_zeroPadWidthTooSmallThrows() throws Exception {
        // 10 页 TIFF 的页码位数需要 2，width=1 无法保证所有文件名等长，应直接拒绝
        List<File> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(createSolidImage("p" + i, 40, 30, Color.GRAY));
        }
        File multiTiff = tempDir.resolve("ten.tiff").toFile();
        MultiPageTiffUtil.imagesToMultiPageTiff(many, multiTiff, TiffCompression.LZW);

        assertThrows(IllegalArgumentException.class,
                () -> MultiPageTiffUtil.tiffToJpgs(multiTiff, tempDir.resolve("out").toFile(), 1));
    }

    @Test
    void tiffToJpg_invalidZeroPadWidthThrows() throws Exception {
        File multiTiff = tempDir.resolve("multi.tiff").toFile();
        MultiPageTiffUtil.imagesToMultiPageTiff(sourceImages, multiTiff, TiffCompression.LZW);

        assertThrows(IllegalArgumentException.class,
                () -> MultiPageTiffUtil.tiffToJpgs(multiTiff, tempDir.resolve("out0").toFile(), 0));
    }

    @Test
    void tiffToJpg_missingInputThrows() {
        File missing = tempDir.resolve("no_such.tiff").toFile();

        assertThrows(IOException.class,
                () -> MultiPageTiffUtil.tiffToJpgs(missing, tempDir.toFile(), 3));
    }

    @Test
    void tiffToJpg_nullInputThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MultiPageTiffUtil.tiffToJpgs(null, tempDir.toFile(), 3));
    }

    // ---------------------------------------------------------------- 辅助方法

    /** 生成一张纯色 PNG 测试图（PNG 无损，保证颜色逐字节精确） */
    private File createSolidImage(String name, int width, int height, Color color) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        File f = tempDir.resolve(name + ".png").toFile();
        assertTrue(ImageIO.write(img, "png", f), "测试图写出失败: " + name);
        return f;
    }

    /** 生成一张二值 PNG 测试图（黑底白心，PNG 保持 1-bit，供 CCITT 压缩使用） */
    private File createBilevelImage(String name, int width, int height, Color color) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(color);
            g.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            g.dispose();
        }
        File f = tempDir.resolve(name + ".png").toFile();
        assertTrue(ImageIO.write(img, "png", f), "测试图写出失败: " + name);
        return f;
    }

    /** 生成一张带 alpha 通道的 PNG 测试图（全透明底 + 中央红色方块） */
    private File createAlphaImage(String name, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(width / 3, height / 3, width / 3, height / 3);
        } finally {
            g.dispose();
        }
        File f = tempDir.resolve(name + ".png").toFile();
        assertTrue(ImageIO.write(img, "png", f), "测试图写出失败: " + name);
        return f;
    }

    /** 读取 TIFF 的页数 */
    private static int countPages(File tiff) throws Exception {
        try (ImageInputStream iis = ImageIO.createImageInputStream(tiff)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            assertTrue(readers.hasNext(), "应找到 TIFF 读取器");
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false, true);
                return reader.getNumImages(true);
            } finally {
                reader.dispose();
            }
        }
    }

    /** 读取 TIFF 指定页 */
    private static BufferedImage readPage(File tiff, int index) throws Exception {
        try (ImageInputStream iis = ImageIO.createImageInputStream(tiff)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            assertTrue(readers.hasNext(), "应找到 TIFF 读取器");
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false, true);
                return reader.read(index);
            } finally {
                reader.dispose();
            }
        }
    }
}
