package cn.net.pap.common.pdf;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ofdrw.core.basicStructure.ofd.docInfo.CustomData;
import org.ofdrw.core.basicStructure.ofd.docInfo.CustomDatas;
import org.ofdrw.core.basicStructure.pageObj.layer.Type;
import org.ofdrw.font.Font;
import org.ofdrw.layout.OFDDoc;
import org.ofdrw.layout.PageLayout;
import org.ofdrw.layout.VirtualPage;
import org.ofdrw.layout.element.Img;
import org.ofdrw.layout.element.Paragraph;
import org.ofdrw.layout.element.Position;
import org.ofdrw.layout.element.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;

public class OFDTest {

    private static final Logger log = LoggerFactory.getLogger(OFDTest.class);

    // 缓存反射 Field，提升高并发下的性能，避免每次实例化都去反射获取
    private static final java.lang.reflect.Field OFD_DIR_FIELD;
    
    // 缓存基础字体文件路径，避免高并发下每次都去复制几MB的字体文件
    private static Path GLOBAL_BASE_FONT_PATH;

    static {
        try {
            OFD_DIR_FIELD = OFDDoc.class.getDeclaredField("ofdDir");
            OFD_DIR_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("OFDDoc 初始化反射获取 ofdDir 失败", e);
        }
    }

    // 类加载时仅执行一次磁盘拷贝
    @BeforeAll
    public static void setupGlobalResources() throws IOException {
        ClassPathResource simfangResource = new ClassPathResource("fonts/simfang.ttf");
        if (simfangResource.exists()) {
            GLOBAL_BASE_FONT_PATH = Files.createTempFile("simfang_base_global_", ".ttf");
            try (InputStream in = simfangResource.getInputStream()) {
                Files.copy(in, GLOBAL_BASE_FONT_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("全局基础字体文件缓存完毕: {}", GLOBAL_BASE_FONT_PATH);
        } else {
            log.warn("提示：基础字体测试资源不存在：fonts/simfang.ttf");
        }
    }

    // 测试结束后清理全局缓存的文件
    @AfterAll
    public static void cleanupGlobalResources() {
        if (GLOBAL_BASE_FONT_PATH != null) {
            try {
                Files.deleteIfExists(GLOBAL_BASE_FONT_PATH);
            } catch (IOException e) {
                log.warn("清理全局基础字体文件失败", e);
            }
        }
    }

    // 辅助函数：仅读取图片尺寸，避免将整张大图加载到内存中导致 OOM
    private Dimension getImageDimensions(File imgFile) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(imgFile)) {
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return new Dimension(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            }
        }
        return null;
    }

    @Test
    public void test1() throws Exception {
        Path outPath = Paths.get("C:\\Users\\86181\\Desktop\\ofda-check.ofd");
        Path imgPath = Paths.get("C:\\Users\\86181\\Desktop\\1.jpg");

        // ====================================================
        // 0. 校验：检查输入图片是否存在，不存在则直接跳过
        // ====================================================
        File imgFile = imgPath.toFile();
        if (!imgFile.exists() || !imgFile.isFile()) {
            log.error("提示：原图片文件不存在，跳过当前处理任务。路径：{}", imgPath);
            return;
        }

        // ====================================================
        // 1. 前置配置：定义 DPI 及基于图片像素坐标系的参数
        // ====================================================
        // 目标显示 DPI：96 (Windows 标准)，高清打印场景可调高至 300
        // todo 这个值要根据具体图像做调整.
        double dpi = 600.0;

        // 定义要写入的文字内容及样式
        String textContent = "法";
        int textR = 255, textG = 255, textB = 255; // 字体颜色

        // 定义文字在原图上的像素级位置及容器大小 (单位：px)
        // 这里的 X 和 Y 是指文字容器左上角相对于图片左上角的坐标
        int textPixelX = 7767;
        int textPixelY = 1337;
        int textPixelWidth = 229;
        int textPixelHeight = 266;

        // ====================================================
        // 2. 动态读取图片像素，并转换为 OFD 的物理尺寸 (单位：mm)
        // ====================================================
        // 使用自定义的方法仅读取图片尺寸，避免整图加载到内存中引发 OOM
        Dimension imgDim = getImageDimensions(imgFile);
        if (imgDim == null) {
            log.error("提示：无法解析图片内容，可能格式不支持，跳过处理。");
            return;
        }

        int pixelWidth = imgDim.width;
        int pixelHeight = imgDim.height;

        // 像素转毫米公式：物理长度(mm) = (像素px / DPI) * 25.4 (1英寸=25.4毫米)
        double targetWidthMm = (pixelWidth / dpi) * 25.4d;
        double targetHeightMm = (pixelHeight / dpi) * 25.4d;

        // 将文字的像素坐标和尺寸也等比例转换为 OFD 画布上的物理数值(mm)
        double textXmm = (textPixelX / dpi) * 25.4d;
        double textYmm = (textPixelY / dpi) * 25.4d;
        double textWidthMm = (textPixelWidth / dpi) * 25.4d;
        double textHeightMm = (textPixelHeight / dpi) * 25.4d;

        // 计算字体大小：取容器宽高较小者的 90% 作为字体实际物理大小，未解决出现的 图元对象外接矩阵过大
        double baseFontSizeMm = Math.min(textWidthMm, textHeightMm);
        double finalFontSizeMm = baseFontSizeMm * 0.9;

        // slf4j 占位符 {} 不支持直接限制小数位数，因此针对浮点数使用 String.format
        if (log.isInfoEnabled()) {
            log.info("读取到图片像素: {} x {} px", pixelWidth, pixelHeight);
            log.info(String.format("画布物理尺寸: %.2f x %.2f mm", targetWidthMm, targetHeightMm));
            log.info(String.format("文字物理坐标: X=%.2f mm, Y=%.2f mm", textXmm, textYmm));
        }

        // ====================================================
        // 3. 校验：检查字体文件是否存在
        // ====================================================
        if (GLOBAL_BASE_FONT_PATH == null) {
            log.error("提示：字体文件不存在，跳过处理 (资源路径: fonts/simfang.ttf)");
            return;
        }

        Path tmpFontPath = null;
        try {
            tmpFontPath = Files.createTempFile("simfang_subset_", ".ttf");
            // 直接使用全局初始化的字体文件，避免重复 IO，提升并发性能
            FontSubsetUtils.createSubset(GLOBAL_BASE_FONT_PATH, tmpFontPath, textContent);

            // ====================================================
            // 4. 开始构建 OFD 文档
            // ====================================================
            try (OFDDoc ofdDoc = new OFDDoc(outPath)) {
                try {
                    // 使用静态初始化的 Field 对象，避免并发下重复执行反射消耗性能
                    org.ofdrw.pkg.container.OFDDir ofdDir = (org.ofdrw.pkg.container.OFDDir) OFD_DIR_FIELD.get(ofdDoc);
                    ofdDir.getOfd().addAttribute("DocType", "OFD-A");
                    CustomDatas customDatas = new CustomDatas();
                    customDatas.addCustomData(new CustomData("发文字号", "TEA-2026-001A"));
                    ofdDir.getOfd().getDocBody().getDocInfo().setAuthor("河南许昌").setCustomDatas(customDatas);
                } catch (Exception e) {
                    log.error("设置 OFD 元数据失败", e);
                }

                // 加载字体
                Font archiveFont = new Font("仿宋", "Simfang", tmpFontPath);

                // --- 构造图片层 ---
                Img topImage = new Img(targetWidthMm, targetHeightMm, imgPath);
                // 清空图片的默认外边距和内边距
                topImage.setPosition(Position.Absolute).setX(0d).setY(0d).setWidth(targetWidthMm).setHeight(targetHeightMm).setMargin(0d).setPadding(0d);
                topImage.setLayer(Type.Background);

                // --- 构造文字层 ---
                Span textSpan = new Span(textContent).setColor(textR, textG, textB).setFont(archiveFont).setFontSize(finalFontSizeMm);

                Paragraph textParagraph = new Paragraph().add(textSpan);
                textParagraph.setOpacity(0d);
                // 清空段落的默认边距（非常关键，段落默认有边距）
                textParagraph.setPosition(Position.Absolute).setX(textXmm).setY(textYmm).setWidth(textWidthMm).setHeight(textHeightMm).setMargin(0d).setPadding(0d);

                // --- 使用计算出的动态尺寸创建画布 ---
                PageLayout customLayout = new PageLayout(targetWidthMm, targetHeightMm);

                ofdDoc.setDefaultPageLayout(customLayout);

                VirtualPage vPage = new VirtualPage(customLayout);

                vPage.add(textParagraph);
                vPage.add(topImage);

                // 写入文档
                ofdDoc.addVPage(vPage);

                log.info("完美贴合图片尺寸，且文字位置精确转换的 OFD 文件生成成功！");
                log.info("输出路径：{}", outPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("提示：字体处理或 OFD 生成失败", e);
        } finally {
            if (tmpFontPath != null) {
                try {
                    Files.deleteIfExists(tmpFontPath);
                } catch (IOException e) {
                    log.error("清理临时字体子集文件失败: {}", tmpFontPath, e);
                }
            }
        }
    }

    public record TextMark(String content, int pixelX, int pixelY, int pixelWidth, int pixelHeight, int r, int g,
                           int b) {
    }

    @Test
    public void test2() throws Exception {
        Path outPath = Paths.get("C:\\Users\\86181\\Desktop\\ofda-test2.ofd");

        List<TextMark> textMarks = List.of(
                new TextMark("河", 7767, 1337, 229, 266, 255, 0, 0),    // 红色
                new TextMark("南", 8067, 1337, 229, 266, 0, 128, 0),    // 绿色
                new TextMark("许", 8367, 1337, 229, 266, 0, 0, 255),    // 蓝色
                new TextMark("昌", 8667, 1337, 229, 266, 0, 0, 0)       // 黑色
        );

        double dpi = 96.0;

        int pixelWidth = 9300;
        int pixelHeight = 7136;

        // 像素转毫米公式
        double targetWidthMm = (pixelWidth / dpi) * 25.4d;
        double targetHeightMm = (pixelHeight / dpi) * 25.4d;

        if (log.isInfoEnabled()) {
            log.info(String.format("画布物理尺寸: %.2f x %.2f mm", targetWidthMm, targetHeightMm));
        }

        if (GLOBAL_BASE_FONT_PATH == null) {
            log.error("提示：字体文件不存在，跳过处理 (资源路径: fonts/simfang.ttf)");
            return;
        }

        try {
            try (OFDDoc ofdDoc = new OFDDoc(outPath)) {
                try {
                    // 使用静态初始化的 Field 对象
                    org.ofdrw.pkg.container.OFDDir ofdDir = (org.ofdrw.pkg.container.OFDDir) OFD_DIR_FIELD.get(ofdDoc);
                    ofdDir.getOfd().addAttribute("DocType", "OFD-A");
                    CustomDatas customDatas = new CustomDatas();
                    customDatas.addCustomData(new CustomData("发文字号", "TEA-2026-001A"));
                    ofdDir.getOfd().getDocBody().getDocInfo().setAuthor("河南许昌").setCustomDatas(customDatas);
                } catch (Exception e) {
                    log.error("设置 OFD 元数据失败", e);
                }

                Font archiveFont = new Font("仿宋", "Simfang", GLOBAL_BASE_FONT_PATH);

                PageLayout customLayout = new PageLayout(targetWidthMm, targetHeightMm);
                ofdDoc.setDefaultPageLayout(customLayout);
                VirtualPage vPage = new VirtualPage(customLayout);

                // --- 循环遍历 Record 集合，添加文字层 ---
                for (TextMark mark : textMarks) {
                    // 将当前文字的像素坐标和尺寸等比例转换为物理数值(mm)
                    double textXmm = (mark.pixelX() / dpi) * 25.4d;
                    double textYmm = (mark.pixelY() / dpi) * 25.4d;
                    double textWidthMm = (mark.pixelWidth() / dpi) * 25.4d;
                    double textHeightMm = (mark.pixelHeight() / dpi) * 25.4d;

                    // 计算字体大小
                    double baseFontSizeMm = Math.min(textWidthMm, textHeightMm);
                    double finalFontSizeMm = baseFontSizeMm * 0.9;

                    // 构造 Span 文本
                    Span textSpan = new Span(mark.content())
                            .setColor(mark.r(), mark.g(), mark.b())
                            .setFont(archiveFont)
                            .setFontSize(finalFontSizeMm);

                    // 构造 Paragraph 容器
                    Paragraph textParagraph = new Paragraph().add(textSpan);
                    textParagraph.setPosition(Position.Absolute)
                            .setX(textXmm).setY(textYmm)
                            .setWidth(textWidthMm).setHeight(textHeightMm)
                            .setMargin(0d).setPadding(0d);

                    // 将文字添加到虚拟页（后添加的会在图片上方）
                    vPage.add(textParagraph);

                    if (log.isInfoEnabled()) {
                        log.info(String.format("已添加文字 [%s]: 物理坐标 X=%.2f mm, Y=%.2f mm", mark.content(), textXmm, textYmm));
                    }
                }

                // 写入文档
                ofdDoc.addVPage(vPage);

                log.info("完美贴合图片尺寸，且多处文字位置精确转换的 OFD 文件生成成功！");
                log.info("输出路径：{}", outPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("提示：字体处理或 OFD 生成失败", e);
        } finally {

        }
    }
}