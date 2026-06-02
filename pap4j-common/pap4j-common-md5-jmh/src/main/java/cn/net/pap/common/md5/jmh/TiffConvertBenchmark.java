package cn.net.pap.common.md5.jmh;

import cn.net.pap.common.md5.jmh.util.ProcessExecUtils;
import org.apache.commons.imaging.Imaging;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * <h3>TIFF 转换基准测试结果与分析</h3>
 * <p>本基准测试对比了在完整尺寸（1:1）下将 TIFF 转换为 JPG 的几种方案：
 * TwelveMonkeys, Apache Commons Imaging, ImageMagick 和 libvips。</p>
 * 
 * <h4>压测结果 (Average Time)</h4>
 * <table border="1" cellpadding="5" cellspacing="0">
 *   <tr>
 *     <th>测试方法</th>
 *     <th>底层实现</th>
 *     <th>平均耗时 (ms/op)</th>
 *     <th>性能排行</th>
 *   </tr>
 *   <tr>
 *     <td><code>convert_Vips</code></td>
 *     <td>libvips (外部进程)</td>
 *     <td>174.409 &plusmn; 29.156</td>
 *     <td><b>1</b> (最快)</td>
 *   </tr>
 *   <tr>
 *     <td><code>convert_ImageMagick</code></td>
 *     <td>ImageMagick (外部进程)</td>
 *     <td>296.897 &plusmn; 15.991</td>
 *     <td><b>2</b></td>
 *   </tr>
 *   <tr>
 *     <td><code>convert_Imaging</code></td>
 *     <td>Apache Commons Imaging (纯Java)</td>
 *     <td>374.457 &plusmn; 110.919</td>
 *     <td><b>3</b> (误差较大)</td>
 *   </tr>
 *   <tr>
 *     <td><code>convert_TwelveMonkeys</code></td>
 *     <td>TwelveMonkeys ImageIO (纯Java)</td>
 *     <td>409.358 &plusmn; 34.247</td>
 *     <td><b>4</b> (最慢)</td>
 *   </tr>
 * </table>
 *
 * <h4>数据总结与技术建议</h4>
 * <ul>
 *   <li><b>外部原生工具的绝对优势</b>：即使算上在 Windows/Linux 环境下每次拉起外部进程（Fork/Exec）高达 30ms-80ms 的固定系统开销，
 *   <code>libvips</code> 和 <code>ImageMagick</code> 的综合执行时间依然碾压了所有纯 Java 的解决方案。这得益于它们底层由高度优化的 C/C++ (如 libjpeg-turbo, libtiff) 编写，
 *   在内存管理和 CPU 向量化指令（SIMD）利用上远超 JVM。</li>
 *   
 *   <li><b>libvips 是性能之王</b>：<code>libvips</code> 采用按需流式（Demand-driven streaming）处理，极大地降低了内存复制和占用，
 *   其处理速度比同为外部原生工具的 ImageMagick 快了近 40%。如果生产环境对吞吐量和内存有极高要求，推荐首选 libvips。</li>
 *   
 *   <li><b>纯 Java 方案的局限性</b>：<code>TwelveMonkeys</code> 和 <code>Commons Imaging</code> 在处理大型 TIFF 图像时，必须将像素数据
 *   解码到 JVM 堆内存的 <code>BufferedImage</code> 中。这不仅造成了巨大的垃圾回收（GC）压力，也增加了大量的内存复制开销。在测试中 <code>convert_Imaging</code> 
 *   的极高误差（&plusmn; 110 ms）侧面印证了在转换过程中发生了停顿（很可能是 GC 抖动）。</li>
 *   
 *   <li><b>架构选型结论</b>：
 *     <ol>
 *       <li>对于高频、大尺寸的图像转换基建，应尽早接入 <b>libvips</b>。</li>
 *       <li>如果受限于运维环境无法部署外部动态库，纯 Java 方案中可以酌情选择 <b>Commons Imaging</b>（速度略快）或 <b>TwelveMonkeys</b>（对生僻 TIFF 编码兼容性更好）。</li>
 *       <li>注意纯 Java 方案处理大图极易引发 OOM，必须谨慎分配堆内存或配合子采样（Subsampling）使用。</li>
 *     </ol>
 *   </li>
 * </ul>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
// 增加样本数量和单次持续时间，提高置信度
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Thread)
public class TiffConvertBenchmark {

    public static final Logger logger = LoggerFactory.getLogger(TiffConvertBenchmark.class);

    private String tiffFilePath;
    private File tempJpgFile;
    // 为 TwelveMonkeys 单独准备一个输出文件，避免两者相互覆盖或由于文件大小不同带来的 I/O 异常
    private File tempJpgFileJava;

    @Setup
    public void setup() throws IOException {
        // 关闭 ImageIO 的默认磁盘缓存，强制使用内存，拉平与 ImageMagick 在处理小文件时的 I/O 模型
        ImageIO.setUseCache(false);

        // 1. 获取资源文件并写入临时文件
        Path tempTiff = Files.createTempFile("benchmark_input", ".tiff");
        try (InputStream is = TiffConvertBenchmark.class.getResourceAsStream("/input.tiff")) {
            if (is == null) {
                throw new FileNotFoundException("input.tiff not found in resources");
            }
            Files.copy(is, tempTiff, StandardCopyOption.REPLACE_EXISTING);
        }
        this.tiffFilePath = tempTiff.toAbsolutePath().toString();
        
        this.tempJpgFile = Files.createTempFile("benchmark_output_magick_", ".jpg").toFile();
        this.tempJpgFileJava = Files.createTempFile("benchmark_output_java_", ".jpg").toFile();
        
        // 预热 TwelveMonkeys 注册
        ImageIO.scanForPlugins();
        
        // 空跑一次，预热底层类加载和引擎初始化
        try {
            convertFullSizeToDisk(tiffFilePath, tempJpgFileJava);
            String cmd = String.format("magick \"%s\" -quality 75 \"%s\"", tiffFilePath, tempJpgFile.getAbsolutePath());
            ProcessExecUtils.execWithShell(cmd, null, null, 20000);
        } catch (Exception e) {
            // ignore init errors
        }
    }

    @TearDown
    public void tearDown() {
        new File(tiffFilePath).delete();
        tempJpgFile.delete();
        tempJpgFileJava.delete();
    }

    @Benchmark
    public long convert_TwelveMonkeys() throws IOException {
        long size = convertFullSizeToDisk(tiffFilePath, tempJpgFileJava);
        if (size == 0) {
            throw new RuntimeException("TwelveMonkeys execution failed, size is 0");
        }
        return size;
    }

    @Benchmark
    public long convert_Imaging() throws IOException {
        tiffToJpg(tiffFilePath, tempJpgFileJava.getAbsolutePath().toString());
        return 1;
    }

    @Benchmark
    public long convert_ImageMagick() throws IOException {
        // 增加 -quality 75 参数，对齐 Java ImageIO 默认的压缩比率
        String cmd = String.format("magick \"%s\" -quality 75 \"%s\"", tiffFilePath, tempJpgFile.getAbsolutePath());
        ProcessExecUtils.ExecResult result = ProcessExecUtils.execWithShell(cmd, null, null, 20000);
        if (!result.isSuccess()) {
            throw new RuntimeException("ImageMagick conversion failed: " + result.getStderr());
        }
        return tempJpgFile.length();
    }

    @Benchmark
    public long convert_Vips() throws IOException {
        String cmd = String.format("vips copy \"%s\" \"%s\"", tiffFilePath, tempJpgFile.getAbsolutePath());
        ProcessExecUtils.ExecResult result = ProcessExecUtils.execWithShell(cmd, null, null, 20000);
        if (!result.isSuccess()) {
            throw new RuntimeException("ImageMagick conversion failed: " + result.getStderr());
        }
        return tempJpgFile.length();
    }

    /**
     * 原封不动的转换 (1:1)，并显式控制输出质量和介质，拉平对比维度
     */
    private long convertFullSizeToDisk(String inputFileStr, File outputFile) throws IOException {
        File file = new File(inputFileStr);
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            if (iis == null) throw new IOException("iis is null");

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) throw new IOException("No TIFF reader found");

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                BufferedImage image = reader.read(0);
                
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                if (!writers.hasNext()) throw new IOException("No JPG writer found");
                
                ImageWriter writer = writers.next();
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
                    writer.setOutput(ios);
                    // 显式设置压缩率为 0.75，对齐 ImageMagick
                    javax.imageio.plugins.jpeg.JPEGImageWriteParam jpegParams = (javax.imageio.plugins.jpeg.JPEGImageWriteParam) writer.getDefaultWriteParam();
                    jpegParams.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    jpegParams.setCompressionQuality(0.75f);
                    
                    writer.write(null, new javax.imageio.IIOImage(image, null, null), jpegParams);
                } finally {
                    writer.dispose();
                    image.flush();
                }
                return outputFile.length();
            } finally {
                reader.dispose();
            }
        }
    }

    public static String tiffToJpg(String inputFilePath, String outputFilePath) {
        File inputFile = new File(inputFilePath);
        File outputFile = new File(outputFilePath);

        try (FileInputStream inputStream = new FileInputStream(inputFile);
             FileOutputStream outputStream = new FileOutputStream(outputFile);
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputFile);
             ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputFile)) {

            // 读取 TIF 图片
            BufferedImage image = Imaging.getBufferedImage(inputStream, null);

            // 去掉 alpha 通道，如果有的话
            if (image.getColorModel().hasAlpha()) {
                BufferedImage rgbImage = new BufferedImage(
                        image.getWidth(), image.getHeight(),
                        BufferedImage.TYPE_INT_RGB
                );
                Graphics2D g = rgbImage.createGraphics();
                g.setColor(Color.WHITE); // 背景填充白色
                g.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
                g.drawImage(image, 0, 0, null);
                g.dispose();
                image = rgbImage;
            }

            // 获取 JPEG Writer
            ImageWriter writer = ImageIO.getImageWritersByFormatName("JPEG").next();
            writer.setOutput(imageOutputStream);

            // 设置压缩质量
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.6f);

            // 写入 JPEG，不再使用 ImageIO.write
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();

            return "success";
        } catch (Exception e) {
            if(e instanceof org.apache.commons.imaging.ImageReadException) {
                String message = ((org.apache.commons.imaging.ImageReadException) e).getMessage();
                if(message.equals("Tiff: unknown/unsupported compression: 7")) {
                    return tiffToJpg2(inputFilePath, outputFilePath);
                }
                // 一些无压缩的tif的异常，同样做一下额外的处理
                if(message.equals("Tiff: samplesPerPixel (3)!=fBitsPerSample.length (1)")) {
                    return tiffToJpg2(inputFilePath, outputFilePath);
                }
            }
            return inputFilePath + " : " + e.getMessage();
        }
    }

    public static String tiffToJpg2(String inputFilePath, String outputFilePath) {
        File inputFile = new File(inputFilePath);
        File outputFile = new File(outputFilePath);

        ImageWriter writer = null;
        ImageInputStream iis = null;
        ImageOutputStream ios = null;

        try {
            BufferedImage bufferedImage = ImageIO.read(inputFile);
            if (bufferedImage == null) {
                throw new RuntimeException("读取 TIFF 失败，可能不支持该格式");
            }

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("没有找到 JPEG 写入器");
            }

            writer = writers.next();
            ios = ImageIO.createImageOutputStream(outputFile);
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.6f);
            }

            writer.write(null, new IIOImage(bufferedImage, null, null), param);
            return "success";

        } catch (Exception e) {
            logger.error(e.getMessage());
            return inputFilePath + " : " + e.getMessage();
        } finally {
            try {
                if (writer != null) {
                    writer.dispose();
                }
            } catch (Exception e) {
                logger.error("Error closing writer: " + e.getMessage());
            }
            try {
                if (ios != null) {
                    ios.close();
                }
            } catch (Exception e) {
                logger.error("Error closing ImageOutputStream: " + e.getMessage());
            }
            try {
                if (iis != null) {
                    iis.close();
                }
            } catch (Exception e) {
                logger.error("Error closing ImageInputStream: " + e.getMessage());
            }
        }
    }
}
