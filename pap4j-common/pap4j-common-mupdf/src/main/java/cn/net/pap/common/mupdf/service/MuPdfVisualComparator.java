package cn.net.pap.common.mupdf.service;

import com.artifex.mupdf.fitz.ColorSpace;
import com.artifex.mupdf.fitz.Device;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.DocumentWriter;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.Pixmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * MuPDF 视觉相似度与渲染保真度自动化对比工具。
 * <p>
 * 支持将 PDF 原生渲染（Golden Ground Truth）与生成的 SVG 渲染位图进行像素级/结构级对比，
 * 输出 <b>SSIM（结构相似度）</b>、<b>差异像素比例（Diff%）</b>、<b>PSNR（峰值信噪比）</b>，
 * 并可自动渲染生成可视化高亮差异图（Diff Map）与多屏并排对比图（Side-by-Side），
 * 用于直观评估排版还原度。
 * </p>
 */
public final class MuPdfVisualComparator {

    private static final Logger log = LoggerFactory.getLogger(MuPdfVisualComparator.class);

    private static final int DEFAULT_DPI = 96;
    private static final int DEFAULT_COLOR_TOLERANCE = 20; // 颜色欧式距离容差阈值 (0-255)

    // SSIM 常量 (K1=0.01, K2=0.03, L=255)
    private static final double C1 = Math.pow(0.01 * 255, 2); // 6.5025
    private static final double C2 = Math.pow(0.03 * 255, 2); // 58.5225
    private static final int BLOCK_SIZE = 8; // SSIM 计算块大小 (8x8)

    private MuPdfVisualComparator() {
    }

    /**
     * 图像视觉对比结果实体。
     *
     * @param ssim        结构相似度 (0.0 ~ 1.0, 越接近 1.0 表示视觉结构越一致)
     * @param diffPercent 差异像素占比 (0.0% ~ 100.0%)
     * @param psnr        峰值信噪比 (dB, 无穷大表示完全无损一致)
     * @param diffPixels  超出容差的差异像素数
     * @param totalPixels 比对区域总像素数
     * @param imageA      基准图（如 PDF 原生渲染图或直接导出的原生 SVG）
     * @param imageB      待测图（如通过 JSON 坐标还原的 SVG）
     * @param diffImage   可视化差异图（一致像素浅灰呈现，差异像素品红高亮）
     */
    public record CompareResult(double ssim, double diffPercent, double psnr,
                                int diffPixels, int totalPixels,
                                BufferedImage imageA, BufferedImage imageB, BufferedImage diffImage) {

        /**
         * 保持向前兼容的 6 参数构造器。
         *
         * @param ssim        结构相似度
         * @param diffPercent 差异像素占比
         * @param psnr        峰值信噪比
         * @param diffPixels  超出容差的差异像素数
         * @param totalPixels 比对区域总像素数
         * @param diffImage   可视化差异图
         */
        public CompareResult(double ssim, double diffPercent, double psnr,
                             int diffPixels, int totalPixels, BufferedImage diffImage) {
            this(ssim, diffPercent, psnr, diffPixels, totalPixels, null, null, diffImage);
        }

        /**
         * 将差异图持久化到文件系统。
         *
         * @param outputPath 输出文件路径
         * @throws IOException 文件写入失败
         */
        public void saveDiffImage(Path outputPath) throws IOException {
            if (outputPath == null || diffImage == null) {
                return;
            }
            ensureParentDir(outputPath);
            ImageIO.write(diffImage, "PNG", outputPath.toFile());
        }

        /**
         * 创建三联并排对比大图：【1: 原生/基准】 | 【2: 还原 SVG 渲染】 | 【3: 差异热力图】
         *
         * @param titleA 基准图标题
         * @param titleB 待测图标题
         * @return 三联对比大图；任一图像缺失时返回差异图
         */
        public BufferedImage createSideBySideImage(String titleA, String titleB) {
            if (imageA == null || imageB == null || diffImage == null) {
                return diffImage;
            }
            int w = Math.min(imageA.getWidth(), imageB.getWidth());
            int h = Math.min(imageA.getHeight(), imageB.getHeight());
            int bannerHeight = 44;
            int gap = 8;
            int totalWidth = w * 3 + gap * 2;
            int totalHeight = h + bannerHeight;

            BufferedImage composite = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = composite.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 背景底色
            g.setColor(new Color(241, 245, 249));
            g.fillRect(0, 0, totalWidth, totalHeight);

            // 绘制三张图
            g.drawImage(imageA, 0, bannerHeight, w, h, null);
            g.drawImage(imageB, w + gap, bannerHeight, w, h, null);
            g.drawImage(diffImage, (w + gap) * 2, bannerHeight, w, h, null);

            // 顶部横幅
            g.setColor(new Color(15, 23, 42));
            g.fillRect(0, 0, totalWidth, bannerHeight);

            // 标题字体
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(Color.WHITE);

            int titleY = 28;
            g.drawString(titleA != null ? titleA : "[1] 基准 (Ground Truth)", 20, titleY);
            g.drawString(titleB != null ? titleB : "[2] 待测 SVG 渲染", w + gap + 20, titleY);
            String diffTitle = String.format("[3] 差异透视 (SSIM: %.2f%%, Diff: %.2f%%)", ssim * 100.0, diffPercent);
            g.drawString(diffTitle, (w + gap) * 2 + 20, titleY);

            // 分割竖线
            g.setColor(new Color(148, 163, 184));
            g.fillRect(w + gap / 2 - 1, 0, 2, totalHeight);
            g.fillRect((w + gap) * 2 - gap / 2 - 1, 0, 2, totalHeight);

            g.dispose();
            return composite;
        }

        public BufferedImage createSideBySideImage() {
            return createSideBySideImage("[1] 基准渲染 (Ground Truth)", "[2] SVG 渲染图 (Rendered SVG)");
        }

        /**
         * 一键导出全套对比图：原图、待测图、差异图、三联并排对比图。
         *
         * @param outputDir 输出目录
         * @param prefix    文件名前缀
         * @param titleA    基准图标题
         * @param titleB    待测图标题
         */
        public void saveAll(Path outputDir, String prefix, String titleA, String titleB) throws IOException {
            Files.createDirectories(outputDir);
            if (imageA != null) {
                ImageIO.write(imageA, "PNG", outputDir.resolve(prefix + "-1-baseline.png").toFile());
            }
            if (imageB != null) {
                ImageIO.write(imageB, "PNG", outputDir.resolve(prefix + "-2-tested-svg.png").toFile());
            }
            if (diffImage != null) {
                ImageIO.write(diffImage, "PNG", outputDir.resolve(prefix + "-3-diff-map.png").toFile());
            }
            BufferedImage sideBySide = createSideBySideImage(titleA, titleB);
            if (sideBySide != null) {
                ImageIO.write(sideBySide, "PNG", outputDir.resolve(prefix + "-side-by-side.png").toFile());
            }
        }

        public void saveAll(Path outputDir, String prefix) throws IOException {
            saveAll(outputDir, prefix, null, null);
        }

        private static void ensureParentDir(Path path) throws IOException {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }

        @Override
        public String toString() {
            return String.format("CompareResult[SSIM=%.4f (%.2f%%), Diff=%.2f%% (%d/%d px), PSNR=%.2f dB]",
                    ssim, ssim * 100.0, diffPercent, diffPixels, totalPixels, psnr);
        }
    }

    /**
     * 调用 MuPDF 官方底层原生能力，直接将 PDF 页面导出为官方标准的矢量 SVG（不依赖 JSON 与结构化坐标）。
     *
     * @param pdfPath    PDF 文件路径
     * @param pageNum    页码 (0-indexed)
     * @param outputFile 导出的原生 SVG 目标路径
     * @throws IOException 文件与渲染异常
     */
    public static void exportNativeSvg(String pdfPath, int pageNum, Path outputFile) throws IOException {
        if (pdfPath == null || outputFile == null) {
            throw new IllegalArgumentException("PDF 路径与输出路径不能为空");
        }
        if (pageNum < 0) {
            throw new IllegalArgumentException("页码必须 >= 0（当前 " + pageNum + "）");
        }
        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Document doc = Document.openDocument(pdfPath)) {
            int pageCount = doc.countPages();
            if (pageNum >= pageCount) {
                throw new IllegalArgumentException("页码越界: " + pageNum + ", 该文档总页数: " + pageCount);
            }
            try (Page page = doc.loadPage(pageNum);
                 DocumentWriter writer = new DocumentWriter(outputFile.toAbsolutePath().toString(), "svg", "")) {
                Device dev = writer.beginPage(page.getBounds());
                page.run(dev, Matrix.Identity());
                writer.endPage();
            }
        } catch (IllegalArgumentException argEx) {
            throw argEx;
        } catch (Throwable t) {
            log.error("[VisualDiff] 导出原生 SVG 失败（第 {} 页）: {}", pageNum + 1, pdfPath, t);
            throw new IllegalStateException("导出原生 SVG 失败（第 " + (pageNum + 1) + " 页）: " + pdfPath
                                            + " -> " + t.getMessage(), t);
        }

        // MuPDF DocumentWriter 在导出单页 SVG 时会默认在文件名末尾附加页码序号 "1" (如 xxx1.svg)
        String fileName = outputFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot >= 0 ? fileName.substring(dot) : "";
        Path numbered = (parent != null ? parent : Path.of(".")).resolve(base + "1" + ext);
        if (!Files.exists(outputFile) && Files.exists(numbered)) {
            Files.move(numbered, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 对比 PDF 目标页原生位图渲染与对应 SVG 文件的渲染效果。
     *
     * @param pdfPath PDF 文件路径
     * @param pageNum 页码 (0-indexed)
     * @param svgPath 待测 SVG 文件路径
     * @param dpi     渲染 DPI，小于等于 0 时使用默认值
     * @return 视觉对比结果
     * @throws IOException 渲染或文件读取失败
     */
    public static CompareResult comparePdfAndSvg(String pdfPath, int pageNum, Path svgPath, int dpi) throws IOException {
        int effectiveDpi = dpi > 0 ? dpi : DEFAULT_DPI;

        BufferedImage pdfImg = renderPdfPage(pdfPath, pageNum, effectiveDpi);
        BufferedImage svgImg = renderSvgFile(svgPath, effectiveDpi);

        CompareResult result = compare(pdfImg, svgImg, DEFAULT_COLOR_TOLERANCE);
        log.info("[VisualDiff] PDF[{} p.{}] vs SVG[{}] -> {}",
                new File(pdfPath).getName(), pageNum + 1, svgPath.getFileName(), result);
        return result;
    }

    /**
     * 对比两个 SVG 文件的栅格化视觉效果（如直接生成的原生 SVG vs 通过 JSON 坐标还原的 SVG）。
     *
     * @param svgPathA 基准 SVG 文件路径
     * @param svgPathB 待测 SVG 文件路径
     * @param dpi      渲染 DPI，小于等于 0 时使用默认值
     * @return 视觉对比结果
     * @throws IOException 渲染或文件读取失败
     */
    public static CompareResult compareSvgs(Path svgPathA, Path svgPathB, int dpi) throws IOException {
        int effectiveDpi = dpi > 0 ? dpi : DEFAULT_DPI;

        BufferedImage imgA = renderSvgFile(svgPathA, effectiveDpi);
        BufferedImage imgB = renderSvgFile(svgPathB, effectiveDpi);

        CompareResult result = compare(imgA, imgB, DEFAULT_COLOR_TOLERANCE);
        log.info("[VisualDiff] SVG[{}] vs SVG[{}] -> {}",
                svgPathA.getFileName(), svgPathB.getFileName(), result);
        return result;
    }

    /**
     * 核心比对方法：对比两张 BufferedImage，计算 SSIM、PSNR、像素差异率并生成差异热力图。
     *
     * @param imgA           基准图
     * @param imgB           待测图
     * @param colorTolerance 颜色欧式距离容差（小于 0 时按 0 处理）
     * @return 视觉对比结果
     */
    public static CompareResult compare(BufferedImage imgA, BufferedImage imgB, int colorTolerance) {
        if (imgA == null || imgB == null) {
            throw new IllegalArgumentException("参与比对的图像不能为空");
        }

        int w = Math.min(imgA.getWidth(), imgB.getWidth());
        int h = Math.min(imgA.getHeight(), imgB.getHeight());
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("参与比对的图像尺寸无效: " + w + "x" + h);
        }
        int tolerance = Math.max(0, colorTolerance);

        BufferedImage diffImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int diffPixels = 0;
        int totalPixels = w * h;
        double sumSquareError = 0.0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgbA = imgA.getRGB(x, y);
                int rgbB = imgB.getRGB(x, y);

                int rA = (rgbA >> 16) & 0xFF, gA = (rgbA >> 8) & 0xFF, bA = rgbA & 0xFF;
                int rB = (rgbB >> 16) & 0xFF, gB = (rgbB >> 8) & 0xFF, bB = rgbB & 0xFF;

                double dR = rA - rB;
                double dG = gA - gB;
                double dB = bA - bB;

                sumSquareError += (dR * dR + dG * dG + dB * dB) / 3.0;

                double colorDist = Math.sqrt(dR * dR + dG * dG + dB * dB);
                if (colorDist > tolerance) {
                    diffPixels++;
                    // 差异像素：使用鲜亮品红高亮 (#FF0066)
                    diffImg.setRGB(x, y, 0xFFFF0066);
                } else {
                    // 一致像素：转换为浅灰淡化背景
                    int gray = (int) (0.299 * rA + 0.587 * gA + 0.114 * bA);
                    int washed = 220 + (gray * 35 / 255);
                    diffImg.setRGB(x, y, 0xFF000000 | (washed << 16) | (washed << 8) | washed);
                }
            }
        }

        double diffPercent = totalPixels > 0 ? (double) diffPixels / totalPixels * 100.0 : 0.0;
        double mse = totalPixels > 0 ? sumSquareError / totalPixels : 0.0;
        double psnr = mse <= 0.0001 ? 100.0 : 10.0 * Math.log10((255.0 * 255.0) / mse);

        double ssim = calculateSsim(imgA, imgB, w, h);

        return new CompareResult(ssim, diffPercent, psnr, diffPixels, totalPixels, imgA, imgB, diffImg);
    }

    /**
     * 比较两个 MuPDF 原生 Pixmap。
     *
     * @param pixA           基准 Pixmap
     * @param pixB           待测 Pixmap
     * @param colorTolerance 颜色欧式距离容差
     * @return 视觉对比结果
     */
    public static CompareResult comparePixmaps(Pixmap pixA, Pixmap pixB, int colorTolerance) {
        if (pixA == null || pixB == null) {
            throw new IllegalArgumentException("参与比对的 Pixmap 不能为空");
        }
        BufferedImage imgA = toBufferedImage(pixA);
        BufferedImage imgB = toBufferedImage(pixB);
        return compare(imgA, imgB, colorTolerance);
    }

    /**
     * 将 MuPDF 原生 Pixmap 高效无损转换为 Java {@link BufferedImage}。
     *
     * @param pix 原生 Pixmap
     * @return 转换后的 BufferedImage；入参为 null 时返回 null
     */
    public static BufferedImage toBufferedImage(Pixmap pix) {
        if (pix == null) {
            return null;
        }
        int w = pix.getWidth();
        int h = pix.getHeight();
        int n = pix.getNumberOfComponents();
        byte[] samples = pix.getSamples();
        int stride = pix.getStride();
        if (samples == null || w <= 0 || h <= 0 || n <= 0) {
            throw new IllegalStateException("Pixmap 数据无效: " + w + "x" + h + ", components=" + n
                                            + ", samples=" + (samples == null ? "null" : samples.length));
        }
        if (stride < w * n || samples.length < (long) stride * h) {
            throw new IllegalStateException("Pixmap 样本缓冲区长度不足: stride=" + stride + ", width=" + w
                                            + ", height=" + h + ", components=" + n
                                            + ", samples=" + samples.length);
        }

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            int rowOffset = y * stride;
            for (int x = 0; x < w; x++) {
                int idx = rowOffset + x * n;
                int r = samples[idx] & 0xFF;
                int g = (n > 1) ? (samples[idx + 1] & 0xFF) : r;
                int b = (n > 2) ? (samples[idx + 2] & 0xFF) : r;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    // ------------------------------------------------------------------
    // 私有辅助：MuPDF 原生栅格化渲染
    // ------------------------------------------------------------------

    private static BufferedImage renderPdfPage(String pdfPath, int pageNum, int dpi) {
        float scale = dpi / 72.0f;
        try (Document doc = Document.openDocument(pdfPath);
             Page page = doc.loadPage(pageNum);
             Pixmap pix = page.toPixmap(new Matrix(scale, scale), ColorSpace.DeviceRGB, false)) {
            return toBufferedImage(pix);
        } catch (Throwable t) {
            log.error("[VisualDiff] 渲染 PDF 页面失败（第 {} 页, {} DPI）: {}", pageNum + 1, dpi, pdfPath, t);
            throw new IllegalStateException("渲染 PDF 页面失败（第 " + (pageNum + 1) + " 页, " + dpi
                                            + " DPI）: " + pdfPath + " -> " + t.getMessage(), t);
        }
    }

    private static BufferedImage renderSvgFile(Path svgPath, int dpi) {
        if (svgPath == null) {
            throw new IllegalArgumentException("SVG 文件路径不能为空");
        }
        float scale = dpi / 72.0f;
        try (Document doc = Document.openDocument(svgPath.toAbsolutePath().toString());
             Page page = doc.loadPage(0);
             Pixmap pix = page.toPixmap(new Matrix(scale, scale), ColorSpace.DeviceRGB, false)) {
            return toBufferedImage(pix);
        } catch (Throwable t) {
            log.error("[VisualDiff] 渲染 SVG 文件失败（{} DPI）: {}", dpi, svgPath.toAbsolutePath(), t);
            throw new IllegalStateException("渲染 SVG 文件失败（" + dpi + " DPI）: " + svgPath.toAbsolutePath()
                                            + " -> " + t.getMessage(), t);
        }
    }

    // ------------------------------------------------------------------
    // 私有辅助：SSIM (结构相似性) 经典分块滑动计算
    // ------------------------------------------------------------------

    private static double calculateSsim(BufferedImage imgA, BufferedImage imgB, int w, int h) {
        int xBlocks = w / BLOCK_SIZE;
        int yBlocks = h / BLOCK_SIZE;
        if (xBlocks == 0 || yBlocks == 0) {
            return 1.0;
        }

        double totalSsim = 0.0;
        int blockCount = 0;

        for (int by = 0; by < yBlocks; by++) {
            for (int bx = 0; bx < xBlocks; bx++) {
                int startX = bx * BLOCK_SIZE;
                int startY = by * BLOCK_SIZE;

                double sumA = 0.0, sumB = 0.0;
                double[] lumsA = new double[BLOCK_SIZE * BLOCK_SIZE];
                double[] lumsB = new double[BLOCK_SIZE * BLOCK_SIZE];
                int idx = 0;

                for (int y = 0; y < BLOCK_SIZE; y++) {
                    for (int x = 0; x < BLOCK_SIZE; x++) {
                        int rgbA = imgA.getRGB(startX + x, startY + y);
                        int rgbB = imgB.getRGB(startX + x, startY + y);

                        double lumA = 0.299 * ((rgbA >> 16) & 0xFF) + 0.587 * ((rgbA >> 8) & 0xFF) + 0.114 * (rgbA & 0xFF);
                        double lumB = 0.299 * ((rgbB >> 16) & 0xFF) + 0.587 * ((rgbB >> 8) & 0xFF) + 0.114 * (rgbB & 0xFF);

                        lumsA[idx] = lumA;
                        lumsB[idx] = lumB;
                        sumA += lumA;
                        sumB += lumB;
                        idx++;
                    }
                }

                int n = BLOCK_SIZE * BLOCK_SIZE;
                double meanA = sumA / n;
                double meanB = sumB / n;

                double varA = 0.0, varB = 0.0, covAB = 0.0;
                for (int i = 0; i < n; i++) {
                    double diffA = lumsA[i] - meanA;
                    double diffB = lumsB[i] - meanB;
                    varA += diffA * diffA;
                    varB += diffB * diffB;
                    covAB += diffA * diffB;
                }
                varA /= (n - 1);
                varB /= (n - 1);
                covAB /= (n - 1);

                double blockSsim = ((2.0 * meanA * meanB + C1) * (2.0 * covAB + C2))
                        / ((meanA * meanA + meanB * meanB + C1) * (varA + varB + C2));

                totalSsim += blockSsim;
                blockCount++;
            }
        }

        return blockCount > 0 ? totalSsim / blockCount : 1.0;
    }
}
