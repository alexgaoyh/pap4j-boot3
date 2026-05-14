package cn.net.pap.common.opencv.tif;

import cn.net.pap.common.opencv.TestResourceUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TiffSubsamplingTest {

    private static final Logger log = LoggerFactory.getLogger(TiffSubsamplingTest.class);

    // 假设我们要做 1/8 的降采样，即将 600dpi 降至约 75dpi 用作 Web 预览
    private static final int SUBSAMPLING_RATIO = 8;

    @BeforeAll
    public static void setup() {
        // 强制完全在堆内存/直接内存中处理，禁止使用磁盘缓存，以模拟高并发下的真实压力
        ImageIO.setUseCache(false);
    }

    /**
     * <h3>TIFF 子采样 (Subsampling) 性能与内存对比测试</h3>
     * <p><b>采样比例:</b> 1/8</p>
     *
     * <h4>1. 测试报告示例</h4>
     * <ul>
     *   <li><b>[标准 Striped]</b> 耗时: 125.09 ms, 粗略内存波动: ~102 MB</li>
     *   <li><b>[瓦片 Tiled]</b> &nbsp;&nbsp;&nbsp;&nbsp;耗时: 489.80 ms, 粗略内存波动: ~30 MB</li>
     * </ul>
     * <p><i>结论：在此采样率下，Striped 结构的速度大约是 Tiled 的 3 倍以上，但内存消耗也是 3 倍多。</i></p>
     *
     * <h4>2. 底层原理解析</h4>
     * <p><b>Striped（条带状）的底层是“顺序读取 (Sequential I/O)”：</b></p>
     * <p>当进行全局子采样时，解码器知道需要遍历整张图。对于 Striped 结构，文件在磁盘上是连续的大块。
     * 操作系统的 Page Cache（页缓存）和底层机械/固态硬盘最喜欢这种模式。系统会执行“预读 (Read-ahead)”，
     * 把巨大的数据块像倒水一样瞬间倾泻到 JVM 内存里。接着，CPU 在内存里快速解压并挑选像素。<br>
     * <b>代价：</b>极其贪婪地吞噬内存（102 MB）。</p>
     *
     * <p><b>Tiled（瓦片状）的底层是“随机访问 (Random Access I/O)”：</b></p>
     * <p>为了做 1/8 子采样，解码器需要从图的左上角到右下角，读取每一个 256x256 的小瓦片。你的 600dpi A4 图大约被切成了 500 多个小瓦片。
     * 解码器被迫执行了 500 多次这样的循环：<br>
     * <code>寻址 (Seek) &rarr; 读取头信息 &rarr; 初始化解压器 &rarr; 解压 256x256 像素 &rarr; 提取像素 &rarr; 销毁解压器</code>。<br>
     * <b>代价：</b>虽然每次只占用极小内存，但这 500 多次的寻址开销和解压器上下文切换开销累加起来，就导致了耗时增加。</p>
     *
     * <h4>3. 准备测试文件命令</h4>
     * <pre>{@code
     * # 扫描生成原始条带状文件
     * scanimage -d "xxx" --format=tif --mode Color --resolution 600 --batch-count=1 --batch-start=1 -x 210 -y 297 --batch=striped_a4.tif
     * # 使用 ImageMagick 将其转为瓦片状结构
     * magick striped_a4.tif -define tiff:tile-geometry=256x256 tiled_a4.tif
     * }</pre>
     *
     * @throws Exception 测试过程中的 IO 或解码异常
     */
    @Test
    public void compareSubsamplingPerformance() throws Exception {
        File stripedFile = null;
        try {
            stripedFile = TestResourceUtil.getFile("striped_a4.tif");
        } catch (Exception e) {
            return;
        }
        File tiledFile = null;
        try {
            tiledFile = TestResourceUtil.getFile("tiled_a4.tif");
        } catch (Exception e) {
            return;
        }

        assertTrue(stripedFile.exists(), "请准备好原始条带状 TIFF 文件");
        assertTrue(tiledFile.exists(), "请准备好内部瓦片状 TIFF 文件");

        log.info("====== TIFF 子采样 (Subsampling) 性能与内存对比测试 ======");
        log.info("采样比例: 1/{}", SUBSAMPLING_RATIO);

        // JVM 预热 (Warm-up)
        readWithSubsampling(stripedFile);
        readWithSubsampling(tiledFile);

        // 1. 测试常规 Striped TIFF 的子采样
        System.gc(); // 强制垃圾回收，提供相对干净的基线
        Thread.sleep(500); // 等待 GC 完成
        Runtime rt = Runtime.getRuntime();
        long startMemStriped = rt.totalMemory() - rt.freeMemory();

        long startStriped = System.nanoTime();
        BufferedImage img1 = readWithSubsampling(stripedFile);
        long endStriped = System.nanoTime();

        long endMemStriped = rt.totalMemory() - rt.freeMemory();
        double timeStripedMs = (endStriped - startStriped) / 1_000_000.0;
        long memUsedStriped = (endMemStriped - startMemStriped) / (1024 * 1024);

        // 2. 测试 Tiled TIFF 的子采样
        System.gc();
        Thread.sleep(500);
        long startMemTiled = rt.totalMemory() - rt.freeMemory();

        long startTiled = System.nanoTime();
        BufferedImage img2 = readWithSubsampling(tiledFile);
        long endTiled = System.nanoTime();

        long endMemTiled = rt.totalMemory() - rt.freeMemory();
        double timeTiledMs = (endTiled - startTiled) / 1_000_000.0;
        long memUsedTiled = (endMemTiled - startMemTiled) / (1024 * 1024);

        // 输出报告
        log.info("[标准 Striped] 耗时: {} ms, 粗略内存波动: ~{} MB", String.format("%8.2f", timeStripedMs), memUsedStriped);
        log.info("[瓦片 Tiled  ] 耗时: {} ms, 粗略内存波动: ~{} MB", String.format("%8.2f", timeTiledMs), memUsedTiled);

        log.info("耗时对比: Tiled 结构快了约 {} 倍", String.format("%.1f", timeStripedMs / timeTiledMs));

        assertNotNull(img1);
        assertNotNull(img2);
    }

    /**
     * <h3>Pyramid TIFF (金字塔模型) 多页读取性能与内存测试</h3>
     * <p>金字塔模型是在生成 TIFF 文件的阶段，提前将 1/2、1/4、1/8 的缩略图生成好，作为额外的“页面 (Pages) / SubIFDs” 塞进同一个 TIFF 文件里。</p>
     * <p>读取时，无需在内存中解码大图并进行子采样，而是直接定位到所需分辨率的页面直接解码。这是一种典型的“空间换时间、空间换内存”策略。</p>
     *
     * <h4>生成命令示例 (ImageMagick)</h4>
     * <pre>{@code
     * # 将普通 TIFF 转换为包含多级分辨率的金字塔 TIFF (PTIF)
     * magick striped_a4.tif -define tiff:tile-geometry=256x256 ptif:pyramid_a4.tif
     * }</pre>
     *
     * @throws Exception 测试异常
     */
    @Test
    public void pyramidTiffReadingTest() throws Exception {
        File pyramidFile = null;
        try {
            pyramidFile = TestResourceUtil.getFile("striped_a4_pyramid.tif");
        } catch (Exception e) {
            log.info("未找到 striped_a4_pyramid.tif 测试文件，跳过 Pyramid TIFF 测试。");
            return;
        }

        if (!pyramidFile.exists()) {
            return;
        }

        log.info("====== Pyramid TIFF 多页面 (金字塔图层) 读取测试 ======");

        try (ImageInputStream iis = ImageIO.createImageInputStream(pyramidFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new RuntimeException("未找到 TIFF 解码器");
            }

            ImageReader reader = readers.next();
            reader.setInput(iis, false, true);

            // 获取 TIFF 包含的总页数（图层数）
            int numImages = reader.getNumImages(true);
            log.info("检测到该 TIFF 文件共包含 {} 个页面 (图层)", numImages);

            for (int i = 0; i < numImages; i++) {
                // 每次读取前强制垃圾回收，以便观察更真实的内存分配波动
                System.gc();
                Thread.sleep(500);
                Runtime rt = Runtime.getRuntime();
                long startMem = rt.totalMemory() - rt.freeMemory();

                long startTime = System.nanoTime();
                
                // 核心：直接读取指定索引的页面，无需手动设置降采样参数
                BufferedImage image = reader.read(i);
                
                long endTime = System.nanoTime();

                long endMem = rt.totalMemory() - rt.freeMemory();
                double timeMs = (endTime - startTime) / 1_000_000.0;
                long memUsedMb = (endMem - startMem) / (1024 * 1024);

                log.info("[页面 {}] 分辨率: {}x{}, 耗时: {} ms, 粗略内存波动: ~{} MB",
                        i, image.getWidth(), image.getHeight(), String.format("%8.2f", timeMs), memUsedMb);

                assertNotNull(image);
            }
            reader.dispose();
        }
    }

    /**
     * 核心逻辑：全局子采样读取
     */
    private BufferedImage readWithSubsampling(File tiffFile) throws Exception {
        try (ImageInputStream iis = ImageIO.createImageInputStream(tiffFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new RuntimeException("未找到 TIFF 解码器");
            }

            ImageReader reader = readers.next();
            reader.setInput(iis, false, true);

            ImageReadParam param = reader.getDefaultReadParam();

            // 核心：设置全局的 X 和 Y 轴步长
            param.setSourceSubsampling(SUBSAMPLING_RATIO, SUBSAMPLING_RATIO, 0, 0);

            BufferedImage result = reader.read(0, param);
            reader.dispose();

            return result;
        }
    }
}
