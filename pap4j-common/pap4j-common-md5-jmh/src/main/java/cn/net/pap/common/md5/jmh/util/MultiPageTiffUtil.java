package cn.net.pap.common.md5.jmh.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 多页 TIFF 工具类
 * <p>
 * 基于 TwelveMonkeys imageio-tiff 实现多页 TIFF 的生成与转换，仅暴露两个公开功能（各有带
 * {@code compressionQuality} 压缩质量的重载）：
 * <ol>
 *   <li>{@link #imagesToMultiPageTiff(List, File, TiffCompression)}：将一组图片转换为一张多页 TIFF，
 *       并可按需指定 TIFF 压缩方式。</li>
 *   <li>{@link #tiffToJpgs(File, File, int)}：将多页 TIFF 的每一页分别导出为一张 JPG 图片，
 *       导出文件统一放置在调用方给定的根目录下，页码按调用方传入的位数补零。</li>
 * </ol>
 * <p>
 * 说明：
 * <ul>
 *   <li>输入图片既可以是普通图片（JPEG/PNG 等，每张占一页），也可以是本身就含多页的 TIFF（会按页展开）。
 *       展开采用逐页流式读写，不会将整份多页大图一次性载入内存。</li>
 *   <li>TIFF 有各种压缩方式，其名称需与底层 ImageIO 写器严格一致（见 {@link TiffCompression} 枚举）。</li>
 *   <li>CCITT 系列压缩只适用于 1-bit 二值（黑白）图像；传入彩色/灰度图时，写页阶段会抛出明确的
 *       {@link IllegalArgumentException} 提示先二值化或改用其他压缩。</li>
 *   <li>JPEG 压缩为有损压缩，且不支持 alpha 通道；含透明通道的源图会自动以白色背景铺底后写 RGB。</li>
 * </ul>
 *
 * @see TiffCompression
 */
public final class MultiPageTiffUtil {

    private static final Logger log = LoggerFactory.getLogger(MultiPageTiffUtil.class);

    /** JPG 导出 / JPEG-in-TIFF 的默认压缩质量 */
    private static final float DEFAULT_JPEG_QUALITY = 0.75f;

    private static final String TIFF_FORMAT = "tiff";
    private static final String JPEG_FORMAT = "jpg";

    private MultiPageTiffUtil() {
    }

    /**
     * TIFF 压缩方式枚举
     * <p>
     * {@link #imageioName} 为 TwelveMonkeys TIFF 写器 {@code getCompressionTypes()} 返回的
     * 严格名称（大小写敏感），不可随意改写。
     */
    public enum TiffCompression {
        /** 无压缩（TIFF 基线 Baseline） */
        NONE("None"),
        /** PackBits 压缩（TIFF 基线 Baseline） */
        PACK_BITS("PackBits"),
        /** LZW 压缩（TIFF 扩展 Extension） */
        LZW("LZW"),
        /** ZLib(Deflate) 压缩（TIFF 扩展 Extension） */
        ZLIB("ZLib"),
        /** Deflate 压缩（TIFF 扩展 Extension，与 ZLib 同族） */
        DEFLATE("Deflate"),
        /** JPEG 有损压缩（TIFF 扩展 Extension，不支持 alpha） */
        JPEG("JPEG"),
        /** CCITT 修改哈夫曼 RLE，仅适用于 1-bit 二值图 */
        CCITT_RLE("CCITT RLE"),
        /** CCITT T.4（Group 3）传真编码，仅适用于 1-bit 二值图 */
        CCITT_T4("CCITT T.4"),
        /** CCITT T.6（Group 4）传真编码，仅适用于 1-bit 二值图 */
        CCITT_T6("CCITT T.6");

        private final String imageioName;

        TiffCompression(String imageioName) {
            this.imageioName = imageioName;
        }

        /**
         * 获取 ImageIO 写器要求使用的压缩方式名称
         *
         * @return 压缩方式名称
         */
        public String getImageioName() {
            return imageioName;
        }

        /**
         * 是否为 CCITT 系列压缩（仅支持 1-bit 二值图）
         *
         * @return true 表示该压缩方式需要 1-bit 二值图
         */
        public boolean requiresBilevel() {
            return this == CCITT_RLE || this == CCITT_T4 || this == CCITT_T6;
        }
    }

    /**
     * 将一组图片转换为一张多页 TIFF（JPEG 压缩使用默认质量 {@value #DEFAULT_JPEG_QUALITY}）
     * <p>
     * 输入列表中的每个文件都会作为一个（或多个）页写入输出 TIFF：
     * 普通图片（JPEG/PNG 等）占一页；若输入本身是多页 TIFF，则会按页全部展开。
     * 展开过程逐页流式写出，避免将整份多页大图一次性物化到内存。
     * 输出文件所在的父目录不存在时会自动创建。
     *
     * @param inputImages 输入图片文件列表，不能为空且元素不能为 null
     * @param outputTiff  输出的多页 TIFF 文件路径
     * @param compression 指定的 TIFF 压缩方式
     * @throws IllegalArgumentException inputImages 为空或包含 null 元素、outputTiff 或 compression 为 null、压缩方式为 CCITT 但输入非 1-bit 二值图时抛出
     * @throws IOException              inputImages 中存在不存在或不可读的文件、读图失败或写出失败时抛出
     * @see #imagesToMultiPageTiff(List, File, TiffCompression, float)
     */
    public static void imagesToMultiPageTiff(List<File> inputImages, File outputTiff, TiffCompression compression) throws IOException {
        imagesToMultiPageTiff(inputImages, outputTiff, compression, DEFAULT_JPEG_QUALITY);
    }

    /**
     * 将一组图片转换为一张多页 TIFF，并可指定 JPEG 压缩质量
     * <p>
     * 语义同 {@link #imagesToMultiPageTiff(List, File, TiffCompression)}。
     * {@code compressionQuality} 仅在压缩方式为 {@link TiffCompression#JPEG} 时生效，其余无损/CCITT 压缩会忽略该参数。
     * 输出先写入同目录临时文件、全部成功后再原子替换到目标路径，中途失败不会留下损坏/半截的 TIFF。
     *
     * @param inputImages        输入图片文件列表，不能为空且元素不能为 null
     * @param outputTiff         输出的多页 TIFF 文件路径
     * @param compression        指定的 TIFF 压缩方式
     * @param compressionQuality JPEG 压缩质量（0~1），仅在 compression 为 JPEG 时生效
     * @throws IllegalArgumentException inputImages 为空或包含 null 元素、outputTiff/compression/compressionQuality 非法、压缩方式为 CCITT 但输入非 1-bit 二值图时抛出
     * @throws IOException              inputImages 中存在不存在或不可读的文件、读图失败或写出失败时抛出
     */
    public static void imagesToMultiPageTiff(List<File> inputImages, File outputTiff, TiffCompression compression, float compressionQuality) throws IOException {
        validateQuality(compressionQuality);
        validateImagesToTiffParams(inputImages, outputTiff, compression);
        ensureDirectory(outputTiff.getAbsoluteFile().getParentFile());

        // 先写同目录临时文件，全部成功后再原子替换到目标路径：
        // 无论目标是新建还是覆盖已有文件，中途失败都不会留下损坏/半截的 TIFF
        File tempOutput = Files.createTempFile(
                outputTiff.getAbsoluteFile().getParentFile().toPath(), outputTiff.getName() + ".", ".tmp").toFile();
        ImageWriter writer = null;
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(tempOutput)) {
            if (ios == null) {
                throw new IOException("Cannot create image output stream for: " + tempOutput);
            }
            writer = getImageWriter(TIFF_FORMAT);
            writer.setOutput(ios);
            ImageWriteParam param = configureTiffWriteParam(writer, compression, compressionQuality);

            int totalPages = writeAllInputsToSequence(writer, param, inputImages, compression);
            log.info("[MultiPageTiff-Build] Wrote multi-page TIFF with {} pages to {}", totalPages, outputTiff);
        } catch (Exception e) {
            deleteQuietly(tempOutput);
            throw e;
        } finally {
            if (writer != null) {
                writer.dispose();
            }
        }
        try {
            promoteTempToTarget(tempOutput, outputTiff);
        } catch (IOException e) {
            deleteQuietly(tempOutput);
            throw e;
        }
    }

    /**
     * 将多页 TIFF 的每一页导出为一张 JPG 图片（JPEG 压缩使用默认质量 {@value #DEFAULT_JPEG_QUALITY}）
     * <p>
     * 导出文件统一放到 {@code outputRootDir} 目录下，命名规则为
     * {@code <输入文件名去扩展名>_<页码，按入参 zeroPadWidth 补零>.jpg}，
     * 例如 {@code multi.tif} 且 {@code zeroPadWidth = 3} 时将生成
     * {@code multi_001.jpg}、{@code multi_002.jpg}、{@code multi_003.jpg}。
     * 由于同一批导出共用一个补零位数，所有生成的文件名长度一致；
     * 若 {@code zeroPadWidth} 不足以容纳总页数（如 12 页却传 1），将直接抛异常拒绝导出。
     * 目录不存在时会自动创建。
     *
     * @param inputTiff     输入的多页 TIFF 文件
     * @param outputRootDir 导出 JPG 的根目录
     * @param zeroPadWidth  页码补零后的总位数（如 3 表示 {@code 001}），必须大于等于 1 且足以容纳总页数
     * @return 已生成的全部 JPG 文件列表（按页码顺序）
     * @throws IllegalArgumentException inputTiff/outputRootDir 为 null、zeroPadWidth 非法或不足以容纳总页数时抛出
     * @throws IOException              输入文件不存在、读页失败或写图失败时抛出
     * @see #tiffToJpgs(File, File, int, float)
     */
    public static List<File> tiffToJpgs(File inputTiff, File outputRootDir, int zeroPadWidth) throws IOException {
        return tiffToJpgs(inputTiff, outputRootDir, zeroPadWidth, DEFAULT_JPEG_QUALITY);
    }

    /**
     * 将多页 TIFF 的每一页导出为一张 JPG 图片，并可指定 JPEG 压缩质量
     * <p>
     * 语义同 {@link #tiffToJpgs(File, File, int)}，仅额外指定导出 JPG 的压缩质量。
     *
     * @param inputTiff          输入的多页 TIFF 文件
     * @param outputRootDir      导出 JPG 的根目录
     * @param zeroPadWidth       页码补零后的总位数（如 3 表示 {@code 001}），必须大于等于 1 且足以容纳总页数
     * @param compressionQuality JPG 压缩质量（0~1）
     * @return 已生成的全部 JPG 文件列表（按页码顺序）
     * @throws IllegalArgumentException inputTiff/outputRootDir 为 null、zeroPadWidth 或 compressionQuality 非法时抛出
     * @throws IOException              输入文件不存在、读页失败或写图失败时抛出
     */
    public static List<File> tiffToJpgs(File inputTiff, File outputRootDir, int zeroPadWidth, float compressionQuality) throws IOException {
        validateQuality(compressionQuality);
        validateTiffToJpgsParams(inputTiff, outputRootDir, zeroPadWidth);
        ensureDirectory(outputRootDir);

        String baseName = stripExtension(inputTiff.getName());
        ImageReader reader = null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(inputTiff)) {
            if (iis == null) {
                throw new IOException("Cannot create image input stream for: " + inputTiff);
            }
            reader = getImageReader(iis, inputTiff);
            int numPages = reader.getNumImages(true);
            validatePageCountAndWidth(numPages, zeroPadWidth, inputTiff);

            List<File> written = exportPagesToJpg(reader, numPages, outputRootDir, baseName, zeroPadWidth, compressionQuality);
            log.info("[MultiPageTiff-Export] Exported {} page(s) of {} to directory {}", numPages, inputTiff, outputRootDir);
            return written;
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private static void validateImagesToTiffParams(List<File> inputImages, File outputTiff, TiffCompression compression) throws IOException {
        if (inputImages == null || inputImages.isEmpty()) {
            throw new IllegalArgumentException("inputImages must not be null or empty");
        }
        for (int i = 0; i < inputImages.size(); i++) {
            File f = inputImages.get(i);
            if (f == null) {
                throw new IllegalArgumentException("inputImages[" + i + "] must not be null");
            }
            if (!f.isFile()) {
                throw new IOException("inputImages[" + i + "] does not exist or is not a file: " + f);
            }
        }
        if (outputTiff == null) {
            throw new IllegalArgumentException("outputTiff must not be null");
        }
        if (compression == null) {
            throw new IllegalArgumentException("compression must not be null");
        }
    }

    private static void validateTiffToJpgsParams(File inputTiff, File outputRootDir, int zeroPadWidth) throws IOException {
        if (inputTiff == null) {
            throw new IllegalArgumentException("inputTiff must not be null");
        }
        if (outputRootDir == null) {
            throw new IllegalArgumentException("outputRootDir must not be null");
        }
        if (zeroPadWidth < 1) {
            throw new IllegalArgumentException("zeroPadWidth must be >= 1, got: " + zeroPadWidth);
        }
        if (!inputTiff.isFile()) {
            throw new IOException("inputTiff does not exist or is not a file: " + inputTiff);
        }
    }

    private static void validatePageCountAndWidth(int numPages, int zeroPadWidth, File inputTiff) throws IOException {
        if (numPages < 1) {
            throw new IOException("The image contains no pages: " + inputTiff);
        }
        int minWidth = String.valueOf(numPages).length();
        if (zeroPadWidth < minWidth) {
            throw new IllegalArgumentException(
                    "zeroPadWidth (" + zeroPadWidth + ") is too small for " + numPages + " pages; "
                            + "pass at least " + minWidth + " so all output file names keep the same length");
        }
    }

    private static void validateQuality(float compressionQuality) {
        if (compressionQuality < 0f || compressionQuality > 1f) {
            throw new IllegalArgumentException("compressionQuality must be in [0, 1], got: " + compressionQuality);
        }
    }

    private static ImageWriter getImageWriter(String format) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new IOException("No ImageIO writer found for format '" + format + "'");
        }
        return writers.next();
    }

    private static ImageWriteParam configureTiffWriteParam(ImageWriter writer, TiffCompression compression, float compressionQuality) {
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(compression.getImageioName());
            if (compression == TiffCompression.JPEG) {
                param.setCompressionQuality(compressionQuality);
            }
        } else {
            log.warn("[MultiPageTiff-Build] The TIFF writer does not support compression, setting '{}' will be ignored", compression);
        }
        return param;
    }

    private static int writeAllInputsToSequence(ImageWriter writer, ImageWriteParam param, List<File> inputImages, TiffCompression compression) throws IOException {
        int totalPages = 0;
        writer.prepareWriteSequence(null);
        try {
            for (File input : inputImages) {
                totalPages += writeInputToSequence(writer, param, input, compression);
            }
        } finally {
            writer.endWriteSequence();
        }
        return totalPages;
    }

    private static ImageReader getImageReader(ImageInputStream iis, File file) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (!readers.hasNext()) {
            throw new IOException("No image reader found for: " + file);
        }
        ImageReader reader = readers.next();
        reader.setInput(iis, false, true);
        return reader;
    }

    private static List<File> exportPagesToJpg(ImageReader reader, int numPages, File outputRootDir,
                                               String baseName, int zeroPadWidth, float compressionQuality) throws IOException {
        List<File> written = new ArrayList<>(numPages);
        String pattern = baseName + "_%0" + zeroPadWidth + "d.jpg";
        for (int i = 0; i < numPages; i++) {
            BufferedImage page = reader.read(i);
            try {
                File out = new File(outputRootDir, String.format(Locale.ROOT, pattern, i + 1));
                writeJpg(page, out, compressionQuality);
                written.add(out);
            } finally {
                page.flush();
            }
        }
        return written;
    }

    /**
     * 将单个输入文件的全部帧逐页流式写出到 TIFF 序列，返回实际写出的页数
     * <p>
     * 逐页「读取 → RGB 归一化 → 写出 → flush 释放」，避免把多页大图的全部像素同时驻留内存。
     */
    private static int writeInputToSequence(ImageWriter writer, ImageWriteParam param, File input, TiffCompression compression) throws IOException {
        int pageCount = 0;
        try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
            if (iis == null) {
                throw new IOException("Cannot read image: " + input);
            }
            ImageReader reader = getImageReader(iis, input);
            try {
                int numImages = reader.getNumImages(true);
                if (numImages < 1) {
                    throw new IOException("No readable image found in: " + input);
                }
                for (int i = 0; i < numImages; i++) {
                    BufferedImage page = reader.read(i);
                    BufferedImage toWrite = null;
                    try {
                        toWrite = toRgbIfNeeded(page);
                        validateCcittBilevel(compression, toWrite, input, i);
                        writer.writeToSequence(new IIOImage(toWrite, null, null), param);
                        pageCount++;
                        log.debug("[MultiPageTiff-Build] Added page {}x{} from {} to sequence",
                                toWrite.getWidth(), toWrite.getHeight(), input.getName());
                    } finally {
                        flushImages(page, toWrite);
                    }
                }
            } finally {
                reader.dispose();
            }
        }
        return pageCount;
    }

    /**
     * CCITT 系列压缩要求 1-bit 二值图；若压缩方式为 CCITT 但图像非 1-bit，提前抛出可读性更强的提示
     */
    private static void validateCcittBilevel(TiffCompression compression, BufferedImage image, File input, int pageIndex) {
        if (compression.requiresBilevel() && image.getSampleModel().getSampleSize(0) != 1) {
            throw new IllegalArgumentException(
                    "CCITT compression (" + compression + ") requires a 1-bit bilevel (black & white) image, "
                            + "but page " + (pageIndex + 1) + " of " + input.getName() + " is a "
                            + image.getSampleModel().getNumBands() + "-channel image with "
                            + image.getSampleModel().getSampleSize(0) + " bits/sample; "
                            + "binarize it first, or switch to LZW/JPEG compression");
        }
    }

    /**
     * 将单帧图片写为 JPG
     * <p>
     * 缓冲所有权约定：本方法只负责释放内部新建的 RGB 副本（源图带 alpha 被铺底时）；
     * 传入的 {@code image} 生命周期由调用方管理，调用方在用完后负责 {@link BufferedImage#flush()}。
     *
     * @param image  源图（调用方负责其生命周期，本方法不会释放）
     * @param output 输出的 JPG 文件
     * @throws IOException 写图失败时抛出
     */
    private static void writeJpg(BufferedImage image, File output, float compressionQuality) throws IOException {
        BufferedImage toWrite = null;
        ImageWriter writer = null;
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(output)) {
            if (ios == null) {
                throw new IOException("Cannot create image output stream for: " + output);
            }
            toWrite = toRgbIfNeeded(image);
            writer = getImageWriter(JPEG_FORMAT);
            writer.setOutput(ios);
            ImageWriteParam param = configureJpegWriteParam(writer, compressionQuality);
            writer.write(null, new IIOImage(toWrite, null, null), param);
        } finally {
            if (writer != null) {
                writer.dispose();
            }
            flushImages(null, toWrite == image ? null : toWrite);
        }
    }


    private static ImageWriteParam configureJpegWriteParam(ImageWriter writer, float compressionQuality) {
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(compressionQuality);
        }
        return param;
    }

    /**
     * 若图像带 alpha 通道则铺白底转 RGB（JPEG / JPEG-in-TIFF 不支持透明通道）
     */
    private static BufferedImage toRgbIfNeeded(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static void flushImages(BufferedImage primary, BufferedImage secondary) {
        if (primary != null) {
            primary.flush();
        }
        if (secondary != null && secondary != primary) {
            secondary.flush();
        }
    }

    private static void ensureDirectory(File dir) throws IOException {
        if (dir != null && !dir.exists()) {
            Files.createDirectories(dir.toPath());
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                log.debug("[MultiPageTiff-Build] Failed to delete temporary file {}: {}", file, e.getMessage());
            }
        }
    }

    /**
     * 将已写好的临时文件原子替换到目标路径（跨卷等不支持原子移动时退化为普通替换移动）
     */
    private static void promoteTempToTarget(File temp, File target) throws IOException {
        try {
            Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

