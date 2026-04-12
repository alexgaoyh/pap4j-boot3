package cn.net.pap.common.pdf;

import org.junit.jupiter.api.Test;
import org.ofdrw.core.basicStructure.ofd.docInfo.CustomData;
import org.ofdrw.core.basicStructure.ofd.docInfo.CustomDatas;
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class OFDTest {

    private static final Logger log = LoggerFactory.getLogger(OFDTest.class);

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
        double dpi = 96.0;

        // 定义要写入的文字内容及样式
        String textContent = "法";
        int textR = 255, textG = 0, textB = 0; // 字体颜色 (红色)

        // 定义文字在原图上的像素级位置及容器大小 (单位：px)
        // 这里的 X 和 Y 是指文字容器左上角相对于图片左上角的坐标
        int textPixelX = 7767;
        int textPixelY = 1337;
        int textPixelWidth = 229;
        int textPixelHeight = 266;

        // ====================================================
        // 2. 动态读取图片像素，并转换为 OFD 的物理尺寸 (单位：mm)
        // ====================================================
        BufferedImage bimg = ImageIO.read(imgFile);
        if (bimg == null) {
            log.error("提示：无法解析图片内容，可能格式不支持，跳过处理。");
            return;
        }

        int pixelWidth = bimg.getWidth();
        int pixelHeight = bimg.getHeight();

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
        log.info("读取到图片像素: {} x {} px", pixelWidth, pixelHeight);
        log.info(String.format("画布物理尺寸: %.2f x %.2f mm", targetWidthMm, targetHeightMm));
        log.info(String.format("文字物理坐标: X=%.2f mm, Y=%.2f mm", textXmm, textYmm));

        // ====================================================
        // 3. 校验：检查字体文件是否存在
        // ====================================================
        ClassPathResource simfangResource = new ClassPathResource("fonts/simfang.ttf");
        if (!simfangResource.exists()) {
            log.error("提示：字体文件不存在，跳过处理 (资源路径: fonts/simfang.ttf)");
            return;
        }

        // ====================================================
        // 4. 开始构建 OFD 文档
        // ====================================================
        try (OFDDoc ofdDoc = new OFDDoc(outPath)) {
            try {
                java.lang.reflect.Field ofdDirField = OFDDoc.class.getDeclaredField("ofdDir");
                ofdDirField.setAccessible(true);
                org.ofdrw.pkg.container.OFDDir ofdDir = (org.ofdrw.pkg.container.OFDDir) ofdDirField.get(ofdDoc);
                ofdDir.getOfd().addAttribute("DocType", "OFD-A");
                CustomDatas customDatas = new CustomDatas();
                customDatas.addCustomData(new CustomData("发文字号", "TEA-2026-001A"));
                ofdDir.getOfd().getDocBody().getDocInfo().setAuthor("河南许昌").setCustomDatas(customDatas);
            } catch (Exception e) {
                log.error("设置 OFD 元数据失败", e);
            }

            // 加载字体
            Font archiveFont = new Font("仿宋", "Simfang", simfangResource.getFile().toPath());

            // --- 构造图片层 ---
            Img topImage = new Img(targetWidthMm, targetHeightMm, imgPath);
            // 清空图片的默认外边距和内边距
            topImage.setPosition(Position.Absolute).setX(0d).setY(0d).setWidth(targetWidthMm).setHeight(targetHeightMm).setMargin(0d).setPadding(0d);

            // --- 构造文字层 ---
            Span textSpan = new Span(textContent).setColor(textR, textG, textB).setFont(archiveFont).setFontSize(finalFontSizeMm);

            Paragraph textParagraph = new Paragraph().add(textSpan);
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

        log.info(String.format("画布物理尺寸: %.2f x %.2f mm", targetWidthMm, targetHeightMm));

        ClassPathResource simfangResource = new ClassPathResource("fonts/simfang.ttf");
        if (!simfangResource.exists()) {
            log.error("提示：字体文件不存在，跳过处理 (资源路径: fonts/simfang.ttf)");
            return;
        }

        try (OFDDoc ofdDoc = new OFDDoc(outPath)) {
            try {
                java.lang.reflect.Field ofdDirField = OFDDoc.class.getDeclaredField("ofdDir");
                ofdDirField.setAccessible(true);
                org.ofdrw.pkg.container.OFDDir ofdDir = (org.ofdrw.pkg.container.OFDDir) ofdDirField.get(ofdDoc);
                ofdDir.getOfd().addAttribute("DocType", "OFD-A");
                CustomDatas customDatas = new CustomDatas();
                customDatas.addCustomData(new CustomData("发文字号", "TEA-2026-001A"));
                ofdDir.getOfd().getDocBody().getDocInfo().setAuthor("河南许昌").setCustomDatas(customDatas);
            } catch (Exception e) {
                log.error("设置 OFD 元数据失败", e);
            }

            Font archiveFont = new Font("仿宋", "Simfang", simfangResource.getFile().toPath());

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

                log.info(String.format("已添加文字 [%s]: 物理坐标 X=%.2f mm, Y=%.2f mm", mark.content(), textXmm, textYmm));
            }

            // 写入文档
            ofdDoc.addVPage(vPage);

            log.info("完美贴合图片尺寸，且多处文字位置精确转换的 OFD 文件生成成功！");
            log.info("输出路径：{}", outPath.toAbsolutePath());
        }
    }

}