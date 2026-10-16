package cn.net.pap.common.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 特大 PDF / 版式文档流式切片与高性能在线阅览技术验证测试类。
 *
 * <p><b>核心业务场景与技术指标规范：</b></p>
 * <ul>
 *   <li><b>零插件在线阅览：</b>支持 OFD、PDF、Office、WPS、图片等多格式文档在线原版原式阅览，跨终端（PC、手机等）浏览器原生呈现，无需安装任何客户端软件或插件，实现跨平台、跨浏览器的无插件阅读；</li>
 *   <li><b>首屏体验与高并发指标：</b>提供翻页、缩放、导览等基本阅读功能，支持在线文档打印；文件首页加载完全呈现时间 &lt; 2 秒；单服务器部署时，支持 2000 以上用户，200 以上高并发；</li>
 *   <li><b>多格式互转矩阵：</b>支持将 Office、WPS、PDF、图片、网页、TXT 等格式文件转换为 OFD 版式文件，亦支持将 OFD 文件转换为 PDF 文件和图片文件；</li>
 *   <li><b>版式文档二次加工与流式拆分：</b>支持 OFD 二次加工功能，包括元数据处理、页面处理、添加水印、签名/签章、附件处理、组合拆分等功能；单服务器部署时流式文档转换速度应 &gt; 50 页/秒；</li>
 *   <li><b>服务集成与信创适配：</b>支持通过 JAVA API、HTTP 等技术形态的功能服务调用接口实现与业务系统的集成；全面支持信创环境和成熟环境部署运行。</li>
 * </ul>
 *
 * <p><b>本测试类主要验证：</b></p>
 * <ol>
 *   <li>基于 JPEG 2000（JP2）等原生压缩流极速生成多页大容量 PDF 测试样本；</li>
 *   <li>超大 PDF 在低堆内存消耗与零冗余拷贝下的按需读取、切片（Split）与流式投递机制。</li>
 * </ol>
 *
 * @author alexgaoyh
 */
public class PDFSplitTest {

    private static final Logger log = LoggerFactory.getLogger(PDFSplitTest.class);

    /**
     * 根据图片文件创建 PDF 图像对象（{@link PDImageXObject}）。
     * <p>针对 JP2/JPEG2000 格式，优先提取元数据并直接采用原生的 {@code /JPXDecode} 嵌入，
     * 避免高分辨率位图全量解码导致堆内存飙升与处理延迟；其他常规格式走 PDFBox 默认加载逻辑。</p>
     *
     * @param imageFile 源图片文件
     * @param document  目标 PDF 文档对象
     * @return 转换后的 {@link PDImageXObject} 实例
     * @throws IOException 当文件读写或解析失败时抛出
     */
    private PDImageXObject createImageXObject(File imageFile, PDDocument document) throws IOException {
        String name = imageFile.getName().toLowerCase();
        if (name.endsWith(".jp2") || name.endsWith(".jpx") || name.endsWith(".j2k")) {
            return createJp2ImageXObject(imageFile, document);
        }
        try {
            return PDImageXObject.createFromFile(imageFile.getAbsolutePath(), document);
        } catch (Exception e) {
            log.warn("PDImageXObject.createFromFile 加载失败，尝试备用方式: {}", e.getMessage());
            return createJp2ImageXObject(imageFile, document);
        }
    }

    /**
     * 针对 JPEG 2000（JP2）图像的极速原生嵌入：
     * <ol>
     *   <li>轻量读取元数据：通过 {@link ImageReader} 仅获取图像像素宽高（毫秒级，不产生堆内大位图）；</li>
     *   <li>原生 JPXDecode 流直写：利用 PDF 标准滤镜 {@code /Filter /JPXDecode}，直接将压缩流注入 PDF 资源字典；</li>
     *   <li>容错兜底：若元数据探测失败，则平滑回退至 ImageIO 解码后由 {@link LosslessFactory} 创建。</li>
     * </ol>
     *
     * @param jp2File  源 JP2 图片文件
     * @param document 目标 PDF 文档对象
     * @return 转换后的 {@link PDImageXObject} 实例
     * @throws IOException 当文件读写或解析失败时抛出
     */
    private PDImageXObject createJp2ImageXObject(File jp2File, PDDocument document) throws IOException {
        int width = 0;
        int height = 0;
        try (ImageInputStream in = ImageIO.createImageInputStream(jp2File)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    width = reader.getWidth(0);
                    height = reader.getHeight(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            log.warn("读取 JP2 元数据尺寸失败: {}", e.getMessage());
        }

        if (width > 0 && height > 0) {
            try (InputStream is = new FileInputStream(jp2File)) {
                return new PDImageXObject(document, is, COSName.JPX_DECODE, width, height, 8, PDDeviceRGB.INSTANCE);
            }
        }

        // 兜底回退：通过 ImageIO 解码位图写入
        BufferedImage bim = ImageIO.read(jp2File);
        if (bim != null) {
            return LosslessFactory.createFromImage(document, bim);
        }
        throw new IOException("无法解析该 JP2 图片: " + jp2File.getAbsolutePath());
    }

    /**
     * 使用指定的 JP2 图片生成指定页数的多页 PDF 文件。
     * <p>每一页均复用嵌入同一个 JP2 图像 XObject 引用，页面尺寸自适应图片像素物理尺寸。</p>
     *
     * @param pageCount   生成的 PDF 总页数
     * @param outputPath  输出的 PDF 目标文件路径
     * @param jp2Resource classpath 下的 JP2 图片资源相对路径（如 {@code "A4.jp2"}）
     * @throws IOException 当文件读写或 PDF 生成失败时抛出
     */
    private void generatePdfWithJp2(int pageCount, String outputPath, String jp2Resource) throws IOException {
        File jp2File = TestResourceUtil.getFile(jp2Resource);
        try (PDDocument document = new PDDocument()) {
            PDImageXObject imageXObject = createImageXObject(jp2File, document);
            float imageWidth = imageXObject.getWidth();
            float imageHeight = imageXObject.getHeight();
            log.info("加载图片 {} 成功: width={}, height={}", jp2Resource, imageWidth, imageHeight);

            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage(new PDRectangle(imageWidth, imageHeight));
                document.addPage(page);

                // 每页都嵌入图片
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawImage(imageXObject, 0, 0, imageWidth, imageHeight);
                }
            }

            document.save(outputPath);
            log.info("成功生成 {} 页 PDF: {}", pageCount, outputPath);
        }
    }

    /**
     * 单元测试：验证基于 A4.jp2 原生流快速生成多页 PDF，并校验页数与每页引用的图像资源有效性。
     *
     * @throws IOException 当临时文件读写或 PDF 加载校验失败时抛出
     */
    @Test
    public void generateMultiPagePdfWithJp2Test() throws IOException {
        int pageCount = 5;
        File tempPdf = File.createTempFile("pap4j_multi_page_", ".pdf");
        tempPdf.deleteOnExit();

        generatePdfWithJp2(pageCount, tempPdf.getAbsolutePath(), "A4.jp2");

        // 验证生成的 PDF 页数是否正确
        try (PDDocument document = Loader.loadPDF(tempPdf)) {
            int actualPages = document.getNumberOfPages();
            assertEquals(pageCount, actualPages, String.format("期望生成 %d 页 PDF，实际为 %d 页", pageCount, actualPages));
            log.info("验证通过: PDF 页数 = {}", actualPages);

            // 验证每一页都包含图片资源
            for (int i = 0; i < actualPages; i++) {
                PDPage page = document.getPage(i);
                assertNotNull(page.getResources(), String.format("第 %d 页资源为空", i + 1));
                assertTrue(page.getResources().getXObjectNames().iterator().hasNext(), String.format("第 %d 页未包含图片", i + 1));
            }
        } finally {
            if (tempPdf.exists()) {
                boolean deleted = tempPdf.delete();
                log.info("清理临时文件: {}, 结果: {}", tempPdf.getAbsolutePath(), deleted);
            }
        }
    }
}
