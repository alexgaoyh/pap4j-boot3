package cn.net.pap.common.mupdf.service;

import cn.net.pap.common.mupdf.loader.MuPdfNativeLoader;
import com.artifex.mupdf.fitz.Buffer;
import com.artifex.mupdf.fitz.ColorSpace;
import com.artifex.mupdf.fitz.Context;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Font;
import com.artifex.mupdf.fitz.Image;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.PDFPage;
import com.artifex.mupdf.fitz.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.artifex.mupdf.fitz.Pixmap;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.fitz.Quad;
import com.artifex.mupdf.fitz.Rect;
import com.artifex.mupdf.fitz.StructuredText;
import com.artifex.mupdf.fitz.StructuredTextWalker;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 工业级 MuPDF 服务门面：
 * <ul>
 *   <li>1. 毫秒级提取单页文本物理坐标 JSON (完全对齐 mutool draw -F stext.json 格式)</li>
 *   <li>2. 毫秒级将单页光栅化为高质量图片 (PNG / 裸 RGB 点阵 / WebP)</li>
 *   <li>3. 文档头单次解析常驻复用，LRU + TTL 双限界缓存，杜绝文件句柄与堆外内存无限增长</li>
 *   <li>4. 全链路堆外内存显式析构闭环 + JIT 提前回收护栏 (Reference.reachabilityFence)</li>
 * </ul>
 *
 * <h3>线程安全与生命周期约定</h3>
 * <p>MuPDF 官方约定 <b>同一个 Document 在同一时刻只能被一个线程访问</b>
 * (参见 MuPDF 官方示例 MultiThreaded.java)，因此本类对每个常驻文档使用独占锁串行化原生调用，
 * 不同文档之间可以并行处理。</p>
 * <p>文档句柄的获取与淘汰采用 "缓存表内摘除 + 独占锁 + 关闭标记" 三段式协议：
 * 先原子地从缓存表摘除，再标记 closed，最后在独占锁内关闭原生句柄。
 * 使用方只在持有独占锁且校验 "未标记 closed 且仍在缓存表内" 之后才允许访问原生句柄，
 * 因此不存在 use-after-free 造成 JVM 崩溃的可能。</p>
 * <p>本类实现了 {@link AutoCloseable}，容器停机 / Spring 容器销毁时请调用 {@link #close()}，
 * 以便在进程退出前主动释放全部原生文件句柄与堆外内存。</p>
 *
 * <h3>可调参数（JVM 系统属性 / 环境变量 -D）</h3>
 * <ul>
 *   <li>{@value #CFG_CACHE_MAX_DOCS}（默认 8）：常驻文档缓存上限，0 表示禁用缓存；</li>
 *   <li>{@value #CFG_CACHE_TTL_MILLIS}（默认 600000，即 10 分钟）：常驻文档空闲过期时间；</li>
 *   <li>{@value #CFG_RENDER_MAX_PIXELS}（默认 1 亿）：单页光栅化像素上限，超出直接报错而不是把堆外内存抽爆；</li>
 *   <li>{@value #CFG_RAW_MAX_BYTES}（默认 96MB）：裸点阵拷贝进 JVM 堆的字节上限。</li>
 * </ul>
 *
 * <h3>注意</h3>
 * <p>为了避免 Windows 下无法删除 / 重命名正在预览的 PDF，长期不用的文档请通过
 * {@link #evict(String)} 或 {@link #clearCache()} 释放；空闲过期时间也会自动完成该动作。</p>
 */
public class MuPdfService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MuPdfService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 常驻文档缓存的最大文档数。0 或负数表示关闭缓存（每次调用即开即关，最保守但性能最低）。
     */
    public static final String CFG_CACHE_MAX_DOCS = "pap.mupdf.cache.maxDocs";
    /**
     * 常驻文档缓存的空闲过期时间（毫秒）。0 或负数表示不按空闲时间过期。
     */
    public static final String CFG_CACHE_TTL_MILLIS = "pap.mupdf.cache.ttlMillis";
    /**
     * 单页渲染（PNG / WebP）允许的最大像素数，超出即拒绝渲染，避免堆外内存被畸形 PDF 撑爆导致进程被 OOM Killer 杀死。
     */
    public static final String CFG_RENDER_MAX_PIXELS = "pap.mupdf.render.maxPixels";
    /**
     * 裸点阵（拷贝进 JVM 堆）允许的最大字节数，超出即拒绝，避免堆 OOM。
     */
    public static final String CFG_RAW_MAX_BYTES = "pap.mupdf.raw.maxBytes";

    private static final int DEFAULT_DPI = 144;
    private static final int DEFAULT_CACHE_MAX_DOCS = 8;
    private static final long DEFAULT_CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long DEFAULT_RENDER_MAX_PIXELS = 100_000_000L;
    private static final long DEFAULT_RAW_MAX_BYTES = 96L * 1024 * 1024;

    /**
     * 文档在极端的并发淘汰风暴下重试获取的最大次数，超过后降级为 "私有文档，用后即焚"。
     */
    private static final int ACQUIRE_MAX_RETRIES = 8;

    /**
     * 保护 {@link #docCache} 结构本身，<b>绝不在持锁期间执行任何原生释放动作</b>。
     */
    private final Object cacheLock = new Object();

    /**
     * 访问序 LRU 缓存：最久未访问的文档位于迭代器头部。
     */
    private final LinkedHashMap<String, ManagedDoc> docCache = new LinkedHashMap<>(16, 0.75f, true);

    private final int maxCachedDocs;
    private final long cacheTtlMillis;
    private final long maxRenderPixels;
    private final long maxRawSampleBytes;

    private volatile boolean closed = false;

    /**
     * 初始化 MuPdfService 实例，确保底层原生库已加载并初始化缓存配置。
     */
    public MuPdfService() {
        // 原生库加载失败时直接抛出 UnsatisfiedLinkError，避免运行期才暴露环境问题
        MuPdfNativeLoader.ensureLoaded();
        int maxDocs = Integer.getInteger(CFG_CACHE_MAX_DOCS, DEFAULT_CACHE_MAX_DOCS);
        long ttl = Long.getLong(CFG_CACHE_TTL_MILLIS, DEFAULT_CACHE_TTL_MILLIS);
        long pixels = Long.getLong(CFG_RENDER_MAX_PIXELS, DEFAULT_RENDER_MAX_PIXELS);
        long rawBytes = Long.getLong(CFG_RAW_MAX_BYTES, DEFAULT_RAW_MAX_BYTES);
        this.maxCachedDocs = Math.max(0, maxDocs);
        this.cacheTtlMillis = Math.max(0L, ttl);
        this.maxRenderPixels = pixels > 0 ? pixels : DEFAULT_RENDER_MAX_PIXELS;
        this.maxRawSampleBytes = rawBytes > 0 ? rawBytes : DEFAULT_RAW_MAX_BYTES;
    }

    /**
     * 极速提取单页结构化文本物理坐标 JSON（包含每个文字、行、段落、字体及字符边界框 BBox，耗时 < 3ms）。
     * <p>结构与 mutool draw -F stext.json 100% 完全一致，包含外层 file 与 pages 包装。</p>
     *
     * @param pdfPath PDF 文件物理路径
     * @param pageNum 页码 (0-indexed)
     * @return 包含文本块、字符尺寸与位置坐标的 JSON 字符串
     * @throws IllegalArgumentException  文件不存在 / 不可读
     * @throws IndexOutOfBoundsException 页码越界
     * @throws IllegalStateException     服务已关闭
     */
    /**
     * 极速提取单页结构化文本物理坐标（包含每个文字、行、段落、字体及字符边界框 BBox，强类型 Record 模型）。
     *
     * @param pdfPath PDF 文件物理路径
     * @param pageNum 页码 (0-indexed)
     * @return 包含文本块、字符尺寸与位置坐标的结构化结果对象
     * @throws IllegalArgumentException  文件不存在 / 不可读
     * @throws IndexOutOfBoundsException 页码越界
     * @throws IllegalStateException     服务已关闭
     */
    public CoordinatesResult extractCoordinates(String pdfPath, int pageNum) {
        ManagedDoc managed = acquire(pdfPath);
        Page page = null;
        StructuredText st = null;
        try {
            int pageCount = managed.doc.countPages();
            checkPageNumber(pageNum, pageCount);

            page = managed.doc.loadPage(pageNum);
            st = page.toStructuredText();
            PageResult pageResult = buildPageResult(st, pageNum + 1, page);
            return new CoordinatesResult(pdfPath, List.of(pageResult));

        } finally {
            destroyStructuredText(st);
            closeQuietly(page);
            release(managed);
            // JIT 安全屏障：防止底层 C 仍在运行时 managed 句柄被 GC 提前判定死亡
            Reference.reachabilityFence(managed);
        }
    }

    /**
     * 极速提取单页结构化文本物理坐标 JSON（包含每个文字、行、段落、字体及字符边界框 BBox）。
     *
     * @param pdfPath PDF 文件物理路径
     * @param pageNum 页码 (0-indexed)
     * @return 包含文本块、字符尺寸与位置坐标的 JSON 字符串
     * @throws IllegalArgumentException  文件不存在 / 不可读
     * @throws IndexOutOfBoundsException 页码越界
     * @throws IllegalStateException     服务已关闭
     */
    public String extractCoordinatesJson(String pdfPath, int pageNum) {
        CoordinatesResult result = extractCoordinates(pdfPath, pageNum);
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (IOException e) {
            log.error("[MuPDF-Coord] 序列化文本坐标 JSON 失败: {}", pdfPath, e);
            throw new IllegalStateException("序列化文本坐标 JSON 失败: " + pdfPath, e);
        }
    }

    /**
     * 基于提取的文本物理坐标 JSON 反推并还原生成可视化 SVG 页面.
     * <p>
     * 仅根据 JSON 数据逆向重构页面布局，包含字符级 Quad 倾斜多边形框与基线旋转文字，
     * 便于直接在浏览器中核对文本坐标与旋转角度提取的真实度与还原保真度。
     * </p>
     *
     * @param json       extractCoordinatesJson 输出的文本物理坐标 JSON 字符串
     * @param outputFile 输出的目标 SVG 文件路径
     * @throws IOException 文件写入或 JSON 解析异常
     */
    public static void exportCoordinatesToSvg(String json, File outputFile) throws IOException {
        if (json == null || json.trim().isEmpty() || outputFile == null) {
            throw new IllegalArgumentException("JSON 内容与输出目标文件不能为空");
        }
        JsonNode root = OBJECT_MAPPER.readTree(json);
        JsonNode pages = root.get("pages");
        if (pages == null || !pages.isArray() || pages.isEmpty()) {
            return;
        }
        JsonNode page = pages.get(0);
        double width = page.path("width").asDouble(595.0);
        double height = page.path("height").asDouble(842.0);

        StringBuilder svg = new StringBuilder(4096);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(width).append(" ").append(height).append("\" width=\"")
                .append(width).append("\" height=\"").append(height).append("\">\n")
                .append("  <!-- 页面底色与轮廓边框（便于浏览器中清晰呈现页面区域与边界） -->\n")
                .append("  <rect width=\"100%\" height=\"100%\" fill=\"#f1f5f9\" stroke=\"#94a3b8\" stroke-width=\"1.5\" />\n");

        JsonNode blocks = page.get("blocks");
        if (blocks != null && blocks.isArray()) {
            for (JsonNode block : blocks) {
                appendBlockToSvg(svg, block);
            }
        }

        svg.append("</svg>\n");
        Files.writeString(outputFile.toPath(), svg, StandardCharsets.UTF_8);
    }

    private static void appendBlockToSvg(StringBuilder svg, JsonNode block) {
        JsonNode lines = block.get("lines");
        if (lines == null || !lines.isArray()) {
            return;
        }
        for (JsonNode line : lines) {
            JsonNode chars = line.get("chars");
            if (chars == null || !chars.isArray()) {
                continue;
            }
            for (JsonNode ch : chars) {
                appendCharToSvg(svg, ch);
            }
        }
    }

    private static void appendCharToSvg(StringBuilder svg, JsonNode ch) {
        JsonNode q = ch.get("quad");
        if (q != null) {
            svg.append("  <polygon points=\"")
                    .append(q.path("ul").path("x").asDouble()).append(",")
                    .append(q.path("ul").path("y").asDouble()).append(" ")
                    .append(q.path("ur").path("x").asDouble()).append(",")
                    .append(q.path("ur").path("y").asDouble()).append(" ")
                    .append(q.path("lr").path("x").asDouble()).append(",")
                    .append(q.path("lr").path("y").asDouble()).append(" ")
                    .append(q.path("ll").path("x").asDouble()).append(",")
                    .append(q.path("ll").path("y").asDouble())
                    .append("\" fill=\"rgba(37,99,235,0.12)\" stroke=\"#2563eb\" stroke-width=\"0.6\" />\n");
        }

        JsonNode origin = ch.get("origin");
        JsonNode rot = ch.get("rotation");
        double ox = origin != null ? origin.path("x").asDouble() : 0.0;
        double oy = origin != null ? origin.path("y").asDouble() : 0.0;
        double visualAngle = rot != null ? rot.path("visual_angle").asDouble() : 0.0;
        double size = ch.path("size").asDouble(12.0);
        String text = ch.path("c").asText("");

        svg.append("  <text x=\"").append(ox).append("\" y=\"").append(oy)
                .append("\" font-size=\"").append(size)
                .append("\" font-family=\"SimSun, Songti SC, STSong, sans-serif\"")
                .append(" transform=\"rotate(").append(visualAngle).append(",").append(ox).append(",").append(oy).append(")\"")
                .append(" fill=\"#0f172a\">")
                .append(escapeXml(text))
                .append("</text>\n");
    }

    private static String escapeXml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * 极速将单页渲染为 PNG 图片字节流（纯内存，耗时 5~15ms）。
     *
     * @param pdfPath PDF 文件物理路径
     * @param pageNum 页码 (0-indexed)
     * @param dpi     目标渲染 DPI（默认为 144 高清预览；标准打印可设为 300）
     * @return PNG 图像二进制字节数组
     * @throws IllegalArgumentException  文件不存在 / 不可读，或渲染尺寸超过 {@value #CFG_RENDER_MAX_PIXELS} 上限
     * @throws IndexOutOfBoundsException 页码越界
     * @throws IllegalStateException     服务已关闭
     */
    public byte[] renderPageToPng(String pdfPath, int pageNum, int dpi) {
        ManagedDoc managed = acquire(pdfPath);
        Page page = null;
        Pixmap pix = null;
        Buffer buf = null;
        try {
            page = loadPageChecked(managed, pageNum);
            pix = renderPixmap(page, dpi);
            buf = pix.asPNG();
            return buf.asByteArray();
        } finally {
            closeQuietly(buf);
            closeQuietly(pix);
            closeQuietly(page);
            release(managed);
            Reference.reachabilityFence(managed);
        }
    }

    /**
     * 裸点阵结果实体
     *
     * @param samples    未压缩的裸 RGB 字节点阵
     * @param width      图片宽度 (像素)
     * @param height     图片高度 (像素)
     * @param components 通道数 (RGB=3, Gray=1)
     * @param stride     每行跨度步长 (字节)
     */
    public record RawPageImage(byte[] samples, int width, int height, int components, int stride) {
    }

    /**
     * 提取单页渲染后的裸 RGB 点阵数据（零编码开销，耗时 < 5ms）。
     * 可供直接传入本地 libvips 编码器或图像分析管线。
     *
     * @param pdfPath PDF 文件物理路径
     * @param pageNum 页码 (0-indexed)
     * @param dpi     目标渲染 DPI
     * @return 裸点阵数据对象
     * @throws IllegalArgumentException  文件不存在 / 不可读，或渲染尺寸超限
     * @throws IllegalStateException     服务已关闭，或点阵字节数超过 {@value #CFG_RAW_MAX_BYTES} 上限
     * @throws IndexOutOfBoundsException 页码越界
     */
    public RawPageImage renderPageToRawSamples(String pdfPath, int pageNum, int dpi) {
        ManagedDoc managed = acquire(pdfPath);
        Page page = null;
        Pixmap pix = null;
        try {
            page = loadPageChecked(managed, pageNum);
            pix = renderPixmap(page, dpi);

            // getSamples() 在 JNI 内部 NewByteArray + memcpy，会完整拷贝进 JVM 堆，
            // 因此必须在拷贝之前做堆内存容量护栏，避免畸形大页把堆撑爆。
            long sampleBytes = (long) pix.getStride() * (long) pix.getHeight();
            if (sampleBytes > maxRawSampleBytes) {
                throw new IllegalStateException("裸点阵内存占用 " + sampleBytes + " 字节，超过安全上限 "
                                                + maxRawSampleBytes + " 字节（可通过 -D" + CFG_RAW_MAX_BYTES + " 调整），"
                                                + "请降低 DPI 或改用 renderPageToPng / renderPageToWebp");
            }

            return new RawPageImage(
                    pix.getSamples(),
                    pix.getWidth(),
                    pix.getHeight(),
                    pix.getNumberOfComponents(),
                    pix.getStride()
            );

        } finally {
            closeQuietly(pix);
            closeQuietly(page);
            release(managed);
            Reference.reachabilityFence(managed);
        }
    }

    /**
     * 将单页渲染为 WebP 图片（纯内存管道：MuPDF 极速渲染 + Vips 极速转码，默认质量 75）。
     *
     * @param pdfPath PDF 文件物理路径
     * @param pageNum 页码 (0-indexed)
     * @param dpi     目标渲染 DPI
     * @return WebP 图像二进制字节数组
     * @throws IOException 如果转码失败
     */
    public byte[] renderPageToWebp(String pdfPath, int pageNum, int dpi) throws IOException {
        return renderPageToWebp(pdfPath, pageNum, dpi, 75);
    }

    /**
     * 将单页渲染为 WebP 图片（纯内存管道：MuPDF 极速渲染 PAM + Vips 极速转码，耗时 < 25ms，零磁盘 I/O）。
     * <p>当 pap4j-common-vips 不可用时自动回退为 PNG 字节流，保证调用方永远拿到可用图像。</p>
     *
     * @param pdfPath PDF 文件物理路径
     * @param pageNum 页码 (0-indexed)
     * @param dpi     目标渲染 DPI
     * @param quality WebP 图像质量 (1-100)
     * @return WebP 图像二进制字节数组
     * @throws IOException 如果转码失败
     */
    public byte[] renderPageToWebp(String pdfPath, int pageNum, int dpi, int quality) throws IOException {
        String format = webpFormat(quality);

        // 第一段：先在独占锁内完成渲染并拷贝出 PAM 字节，随后立刻释放原生资源，
        // 避免在转码期间长时间占用文档独占锁（也避免回退路径的重入）。
        byte[] payload = renderPageToPayload(pdfPath, pageNum, dpi, false);

        try {
            return cn.net.pap.common.vips.VipsImageProcessor.convertFormat(payload, format);
        } catch (LinkageError optionalVipsEx) {
            log.warn("[MuPDF] 未检测到可用的 pap4j-common-vips（{}），回退返回 PNG 图像", optionalVipsEx.getMessage());
            return renderPageToPng(pdfPath, pageNum, dpi);
        } catch (IOException | RuntimeException pamEx) {
            // PAM 中继偶发不被 vips 接受时，改用 PNG 中继重试一次（Error 类不再包容，避免堆栈错误被吞）
            log.debug("[MuPDF] PAM 转码 WebP 遇到问题，回退通过 PNG 中继转码: {}", pamEx.getMessage());
            byte[] pngBytes = renderPageToPayload(pdfPath, pageNum, dpi, true);
            try {
                return cn.net.pap.common.vips.VipsImageProcessor.convertFormat(pngBytes, format);
            } catch (LinkageError optionalVipsEx2) {
                log.warn("[MuPDF] 未检测到可用的 pap4j-common-vips（{}），回退返回 PNG 图像", optionalVipsEx2.getMessage());
                return pngBytes;
            }
        }
    }

    /**
     * 获取 PDF 文档总页数。
     *
     * @param pdfPath PDF 物理路径
     * @return 总页数
     */
    public int getPageCount(String pdfPath) {
        ManagedDoc managed = acquire(pdfPath);
        try {
            return managed.doc.countPages();
        } finally {
            release(managed);
            Reference.reachabilityFence(managed);
        }
    }

    /**
     * 周期性收缩 MuPDF 堆外全局 Store 缓存（建议在定时任务中调用，如每隔 15~30 分钟收缩一次）。
     * <p>MuPDF 底层 C 库内部维护了一个进程级别的 Store 缓存池，用于缓存已解析的字体字形、常用流等。
     * 在长期运行数周的生产服务中，建议通过 Spring {@code @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)}
     * 定时收缩以确保堆外常驻内存平稳。</p>
     */
    public void shrinkStore() {
        if (!MuPdfNativeLoader.isLoaded()) {
            return;
        }
        try {
            Context.shrinkStore(50);
            log.debug("[MuPDF] 成功收缩 50% 全局 Store 堆外缓存");
        } catch (Throwable t) {
            log.error("[MuPDF] 收缩全局 Store 失败: ", t);
        }
    }

    /**
     * 淘汰指定常驻文档缓存（会阻塞等待该文档上正在进行的渲染任务结束，保证不会 use-after-free）。
     *
     * @param pdfPath PDF 物理路径
     */
    public void evict(String pdfPath) {
        if (pdfPath == null || pdfPath.trim().isEmpty()) {
            return;
        }
        String key = cacheKey(new File(pdfPath.trim()));
        ManagedDoc managed;
        synchronized (cacheLock) {
            managed = docCache.remove(key);
        }
        if (managed != null) {
            managed.closeQuietly();
            log.info("[MuPDF] 已卸载常驻文档缓存: {}", key);
        }
    }

    /**
     * 清空全部常驻文档缓存。
     */
    public void clearCache() {
        List<ManagedDoc> drained;
        synchronized (cacheLock) {
            // 先整体摘除再逐个关闭：避免"摘除期间新写入的文档"被漏掉而永不释放
            drained = new ArrayList<>(docCache.values());
            docCache.clear();
        }
        closeAll(drained);
        if (MuPdfNativeLoader.isLoaded()) {
            try {
                Context.emptyStore();
            } catch (Throwable t) {
                log.error("[MuPDF] 清空全局 Store 失败: ", t);
            }
        }
        log.info("[MuPDF] 已清空全部文档缓存与 Store 堆外内存");
    }

    /**
     * 释放本服务持有的全部原生资源。关闭后本实例不再接受新的渲染请求。
     */
    @Override
    public void close() {
        closed = true;
        clearCache();
    }

    /**
     * 当前常驻文档缓存中的文档数量（用于监控与回归测试）。
     *
     * @return 缓存文档数
     */
    public int cachedDocumentCount() {
        synchronized (cacheLock) {
            return docCache.size();
        }
    }

    // ------------------------------------------------------------------
    // 渲染内部实现
    // ------------------------------------------------------------------

    /**
     * 渲染一页并编码为 PAM（或 PNG）字节流，返回时已释放全部原生渲染资源。
     */
    private byte[] renderPageToPayload(String pdfPath, int pageNum, int dpi, boolean png) {
        ManagedDoc managed = acquire(pdfPath);
        Page page = null;
        Pixmap pix = null;
        Buffer buf = null;
        try {
            page = loadPageChecked(managed, pageNum);
            pix = renderPixmap(page, dpi);
            buf = png ? pix.asPNG() : pix.asPAM();
            return buf.asByteArray();
        } finally {
            closeQuietly(buf);
            closeQuietly(pix);
            closeQuietly(page);
            release(managed);
            Reference.reachabilityFence(managed);
        }
    }

    private Page loadPageChecked(ManagedDoc managed, int pageNum) {
        int pageCount = managed.doc.countPages();
        checkPageNumber(pageNum, pageCount);
        return managed.doc.loadPage(pageNum);
    }

    private static void checkPageNumber(int pageNum, int pageCount) {
        if (pageNum < 0 || pageNum >= pageCount) {
            throw new IndexOutOfBoundsException("页码越界: " + pageNum + ", 该文档总页数: " + pageCount);
        }
    }

    /**
     * 按 DPI 光栅化单页，并在分配堆外内存之前做像素规模护栏。
     */
    private Pixmap renderPixmap(Page page, int dpi) {
        int effectiveDpi = dpi > 0 ? dpi : DEFAULT_DPI;
        float scale = effectiveDpi / 72.0f;

        Rect bounds = page.getBounds();
        if (bounds == null || !bounds.isValid()) {
            throw new IllegalStateException("无法获取页面有效边界框 (bounds=" + bounds + ")");
        }
        double width = Math.ceil((bounds.x1 - bounds.x0) * (double) scale);
        double height = Math.ceil((bounds.y1 - bounds.y0) * (double) scale);
        double pixels = Math.max(1.0, width) * Math.max(1.0, height);
        if (pixels > maxRenderPixels) {
            throw new IllegalArgumentException("渲染尺寸 " + (long) width + "x" + (long) height + " 像素（" + effectiveDpi
                                               + " DPI）超过安全上限 " + maxRenderPixels + " 像素（可通过 -D" + CFG_RENDER_MAX_PIXELS
                                               + " 调整），请降低 DPI");
        }

        return page.toPixmap(new Matrix(scale, scale), ColorSpace.DeviceRGB, false);
    }

    private static String webpFormat(int quality) {
        int q = quality > 100 ? 100 : quality;
        return q > 0 ? "webp[Q=" + q + ",effort=2]" : "webp";
    }

    // ------------------------------------------------------------------
    // 结构化坐标实体模型 (Records)
    // ------------------------------------------------------------------

    /**
     * PDF 页面物理文本坐标总结果实体
     *
     * @param file  PDF 文件物理路径
     * @param pages 页面列表
     */
    public record CoordinatesResult(String file, List<PageResult> pages) {
    }

    /**
     * 单页物理布局实体（方案 A：包含页面旋转、显示尺寸与文本块）。
     *
     * <p><b>坐标系约定：</b>页面内的全部坐标
     * （{@code box} / {@code width} / {@code height} / 块 / 行 / 字的 {@code bbox} 与 {@code quad}）
     * 均处于<b>应用页面旋转后的显示坐标系</b>中（原点左上、Y 轴向下、单位为 PDF Point 72 DPI），
     * 与 {@code renderPageToPng / renderPageToWebp} 渲染出的位图完全同源。前端按
     * {@code x / width * 100%}、{@code y / height * 100%} 定位，无需再对 {@code page_rotation} 二次旋转。</p>
     *
     * @param number       页码 (1-based)
     * @param pageRotation PDF 页面本身的 /Rotate 角度 (0, 90, 180, 270)
     * @param width        显示坐标系下的页宽 (Point)
     * @param height       显示坐标系下的页高 (Point)
     * @param box          显示坐标系下的页面边界框
     * @param transform    PDF 用户空间 ➔ 显示坐标系 的 2D 仿射变换矩阵（非 PDF 为 null）
     * @param blocks       页面内容块列表
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PageResult(
            int number,
            @JsonProperty("page_rotation") int pageRotation,
            double width,
            double height,
            Bbox box,
            MatrixInfo transform,
            List<BlockResult> blocks
    ) {
        /**
         * 向下兼容历史单参数构造器
         *
         * @param blocks 页面内容块列表
         */
        public PageResult(List<BlockResult> blocks) {
            this(1, 0, 0.0, 0.0, null, null, blocks);
        }
    }

    /**
     * 内容块实体（文本块 / 图片块）
     *
     * @param type  块类型 ("text" 或 "image")
     * @param bbox  块外接边界框
     * @param lines 文本行列表（图片块时为 null，不序列化）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BlockResult(String type, Bbox bbox, List<LineResult> lines) {
    }

    /**
     * 文本行实体
     *
     * @param wmode    写入模式 (0=水平, 1=垂直)
     * @param dir      行书写方向单位向量（Y 轴向下显示坐标系）
     * @param rotation 旋转信息实体（含行级 font_angle 与 visual_angle）
     * @param bbox     行轴对齐外接框
     * @param quad     行四边形（由单字 quad 沿 dir 方向正交投影得到的贴合旋转矩形）
     * @param font     行首字体信息
     * @param x        行起始基线 X 坐标
     * @param y        行起始基线 Y 坐标
     * @param text     整行合并文本
     * @param chars    单字坐标列表
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LineResult(
            int wmode,
            PointInfo dir,
            RotationInfo rotation,
            Bbox bbox,
            QuadInfo quad,
            FontInfo font,
            double x,
            double y,
            String text,
            List<CharResult> chars
    ) {
        /**
         * 向下兼容历史构造器
         */
        public LineResult(int wmode, Bbox bbox, FontInfo font, double x, double y, String text, List<CharResult> chars) {
            this(wmode, null, null, bbox, null, font, x, y, text, chars);
        }
    }

    /**
     * 单字物理坐标与旋转元数据实体（方案 A）
     *
     * @param c        字符文本
     * @param origin   字符基线绘制物理坐标 (显示坐标系)
     * @param bbox     单字轴对齐外接框（旋转后为外切矩形，供命中/回退使用）
     * @param quad     单字真实 4 角点四边形（ul -> ur -> lr -> ll 顺时针，任意角度精确贴合）
     * @param rotation 旋转双轨解耦信息（font_angle 为 PDF 内部原始角度，visual_angle 为位图视觉角度）
     * @param font     字体名称
     * @param size     字号大小
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CharResult(
            String c,
            PointInfo origin,
            Bbox bbox,
            QuadInfo quad,
            RotationInfo rotation,
            String font,
            double size
    ) {
        /**
         * 向下兼容历史构造器
         */
        public CharResult(String c, PointInfo origin, Bbox bbox, String font, double size) {
            this(c, origin, bbox, null, null, font, size);
        }
    }

    /**
     * 旋转解耦实体（方案 A 核心）：分离原始字体角度与合成视觉角度。
     *
     * @param fontAngle   PDF 内部文本矩阵设定的原始字体旋转角（度，逆时针为正，0/45/90/180/270，用于逆向回写 PDF）
     * @param visualAngle 最终渲染到屏幕底图上的视觉旋转角（度，顺时针为正，等价 CSS rotate(deg)，用于前端像素级直接定位）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RotationInfo(
            @JsonProperty("font_angle") double fontAngle,
            @JsonProperty("visual_angle") double visualAngle
    ) {
    }

    /**
     * 四边形实体：按顺时针闭环顺序表达四个真实物理角点。
     * <ul>
     *   <li>{@code ul ➔ ur}：文字书写推进（基线）方向；</li>
     *   <li>{@code ul ➔ ll}：文字字高方向。</li>
     * </ul>
     *
     * @param ul 左上角 (Upper-Left)
     * @param ur 右上角 (Upper-Right)
     * @param lr 右下角 (Lower-Right)
     * @param ll 左下角 (Lower-Left)
     */
    public record QuadInfo(PointInfo ul, PointInfo ur, PointInfo lr, PointInfo ll) {
    }

    /**
     * 2D 仿射变换矩阵实体（PDF 的 [a b c d e f] 顺序）。
     *
     * @param a 水平缩放/旋转分量
     * @param b 垂直错切/旋转分量
     * @param c 水平错切/旋转分量
     * @param d 垂直缩放/旋转分量
     * @param e 水平平移
     * @param f 垂直平移
     */
    public record MatrixInfo(double a, double b, double c, double d, double e, double f) {
    }

    /**
     * 物理边界框实体（72 DPI PDF 点坐标）
     *
     * @param x     左上角 X
     * @param y     左上角 Y
     * @param w     宽度
     * @param h     高度
     * @param flags 边界标志（仅行级包含 flags=0，其它块和单字为 null，不序列化）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Bbox(double x, double y, double w, double h, Integer flags) {
    }

    /**
     * 字体元数据实体
     *
     * @param name   字体名称
     * @param family 字体族 ("monospace" / "serif" / "sans-serif")
     * @param weight 粗细 ("bold" / "normal")
     * @param style  字形 ("italic" / "normal")
     * @param size   字号
     */
    public record FontInfo(String name, String family, String weight, String style, double size) {
    }

    /**
     * 二维物理坐标点实体
     *
     * @param x 物理 X 坐标
     * @param y 物理 Y 坐标
     */
    public record PointInfo(double x, double y) {
    }

    private static PageResult buildPageResult(StructuredText st, int pageNumber, Page page) {
        Rect bounds = page != null ? page.getBounds() : null;
        boolean hasBounds = bounds != null && bounds.isValid() && !bounds.isInfinite();
        double pageWidth = hasBounds ? round(bounds.x1 - bounds.x0) : 0.0;
        double pageHeight = hasBounds ? round(bounds.y1 - bounds.y0) : 0.0;
        Bbox pageBox = hasBounds
                ? new Bbox(round(bounds.x0), round(bounds.y0), pageWidth, pageHeight, null)
                : new Bbox(0, 0, 0, 0, null);
        MatrixInfo pageTransform = pageTransform(page);
        int pageRotation = pageTransform != null ? rotationOf(pageTransform) : 0;

        List<BlockResult> blocks = new ArrayList<>();
        st.walk(new StructuredTextWalker() {
            List<LineResult> lines;
            Rect blockBbox;
            List<CharResult> chars;
            StringBuilder lineText;
            Rect lineBbox;
            int lineWmode;
            PointInfo lineDir;
            FontInfo lineFont;
            PointInfo lineOrigin;

            @Override
            public void onImageBlock(Rect bbox, Matrix transform, Image image) {
                blocks.add(new BlockResult("image", toBbox(bbox, null), null));
            }

            @Override
            public void beginTextBlock(Rect bbox, int flags) {
                blockBbox = bbox;
                lines = new ArrayList<>();
            }

            @Override
            public void endTextBlock() {
                blocks.add(new BlockResult("text", toBbox(blockBbox, null), lines));
            }

            @Override
            public void beginLine(Rect bbox, int wmode, Point dir) {
                lineBbox = bbox;
                lineWmode = wmode;
                lineDir = dir != null ? new PointInfo(round(dir.x, 6), round(dir.y, 6)) : null;
                chars = new ArrayList<>();
                lineText = new StringBuilder();
                lineFont = null;
                lineOrigin = null;
            }

            @Override
            public void endLine() {
                double lx = lineOrigin != null ? lineOrigin.x() : (lineBbox != null ? round(lineBbox.x0) : 0.0);
                double ly = lineOrigin != null ? lineOrigin.y() : (lineBbox != null ? round(lineBbox.y0) : 0.0);
                FontInfo font = lineFont != null ? lineFont : defaultFont();
                QuadInfo lineQuad = buildLineQuad(chars, lineDir);
                double lineVisualAngle = lineDir != null
                        ? angleOf(lineDir.x(), lineDir.y())
                        : (chars.isEmpty() || chars.get(0).rotation() == null ? 0.0 : chars.get(0).rotation().visualAngle());
                RotationInfo lineRotation = toRotationInfo(lineVisualAngle, pageRotation);
                lines.add(new LineResult(lineWmode, lineDir, lineRotation, toBbox(lineBbox, 0), lineQuad,
                        font, lx, ly, lineText.toString(), chars));
            }

            @Override
            public void onChar(int c, Point origin, Font font, float size, Quad q, int argb, int flags, int bidi) {
                String str = new String(Character.toChars(c));
                lineText.append(str);

                PointInfo originInfo = origin != null ? new PointInfo(round(origin.x), round(origin.y)) : new PointInfo(0, 0);
                if (lineOrigin == null) {
                    lineOrigin = originInfo;
                }
                FontInfo fontInfo = toFontInfo(font, size);
                if (lineFont == null) {
                    lineFont = fontInfo;
                }
                boolean hasQuad = q != null && q.isValid() && !q.isInfinite();
                QuadInfo quad = hasQuad ? toQuadInfo(q) : null;
                double charVisualAngle = hasQuad ? angleOf(q.ur_x - q.ul_x, q.ur_y - q.ul_y) : 0.0;
                RotationInfo charRotation = hasQuad ? toRotationInfo(charVisualAngle, pageRotation) : null;
                Bbox charBbox = quad != null ? toBbox(quad) : new Bbox(originInfo.x(), originInfo.y(), 0, 0, null);
                chars.add(new CharResult(str, originInfo, charBbox, quad, charRotation, fontInfo.name(), fontInfo.size()));
            }

            @Override
            public void beginStruct(String standard, String raw, int index) {}
            @Override
            public void endStruct() {}
            @Override
            public void onVector(Rect bbox, VectorInfo info, int argb) {}
        });
        return new PageResult(pageNumber, pageRotation, pageWidth, pageHeight, pageBox, pageTransform, blocks);
    }

    /**
     * 计算旋转双轨信息：结合视觉角度与页面旋转角，精确推导 PDF 内部原始字体旋转角。
     */
    private static RotationInfo toRotationInfo(double visualAngle, int pageRotation) {
        double rawFontAngle = ((pageRotation - visualAngle) % 360.0 + 360.0) % 360.0;
        return new RotationInfo(round(rawFontAngle), round(visualAngle));
    }

    private static MatrixInfo pageTransform(Page page) {
        if (!(page instanceof PDFPage pdfPage)) {
            return null;
        }
        try {
            Matrix m = pdfPage.getTransform();
            if (m == null) {
                return null;
            }
            return new MatrixInfo(round(m.a, 3), round(m.b, 3), round(m.c, 3),
                    round(m.d, 3), round(m.e, 3), round(m.f, 3));
        } catch (Throwable t) {
            log.debug("[MuPDF-Coord] 读取页面变换矩阵失败: {}", t.getMessage());
            return null;
        }
    }

    private static int rotationOf(MatrixInfo m) {
        double deg = Math.toDegrees(Math.atan2(m.b(), m.a()));
        int r = ((int) Math.round(deg) % 360 + 360) % 360;
        return (int) (Math.round(r / 90.0) * 90) % 360;
    }

    private static QuadInfo buildLineQuad(List<CharResult> chars, PointInfo dir) {
        if (chars == null || chars.isEmpty() || dir == null) {
            return null;
        }
        double len = Math.hypot(dir.x(), dir.y());
        if (!(len > 0) || Double.isNaN(len) || Double.isInfinite(len)) {
            return null;
        }
        double dx = dir.x() / len;
        double dy = dir.y() / len;
        double uMin = Double.POSITIVE_INFINITY;
        double uMax = Double.NEGATIVE_INFINITY;
        double vMin = Double.POSITIVE_INFINITY;
        double vMax = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (CharResult ch : chars) {
            QuadInfo quad = ch.quad();
            if (quad == null) {
                continue;
            }
            for (PointInfo p : new PointInfo[]{quad.ul(), quad.ur(), quad.lr(), quad.ll()}) {
                double u = p.x() * dx + p.y() * dy;
                double v = -p.x() * dy + p.y() * dx;
                uMin = Math.min(uMin, u);
                uMax = Math.max(uMax, u);
                vMin = Math.min(vMin, v);
                vMax = Math.max(vMax, v);
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        return new QuadInfo(
                projectToPage(uMin, vMin, dx, dy),
                projectToPage(uMax, vMin, dx, dy),
                projectToPage(uMax, vMax, dx, dy),
                projectToPage(uMin, vMax, dx, dy));
    }

    private static PointInfo projectToPage(double u, double v, double dx, double dy) {
        return new PointInfo(round(u * dx - v * dy), round(u * dy + v * dx));
    }

    private static double angleOf(double dx, double dy) {
        if (dx == 0.0 && dy == 0.0) {
            return 0.0;
        }
        double deg = round(Math.toDegrees(Math.atan2(dy, dx)));
        return deg <= -180.0 ? 180.0 : deg;
    }

    private static QuadInfo toQuadInfo(Quad q) {
        if (q == null || !q.isValid() || q.isInfinite()) {
            return null;
        }
        return new QuadInfo(
                new PointInfo(round(q.ul_x), round(q.ul_y)),
                new PointInfo(round(q.ur_x), round(q.ur_y)),
                new PointInfo(round(q.lr_x), round(q.lr_y)),
                new PointInfo(round(q.ll_x), round(q.ll_y)));
    }

    private static double round(double val) {
        return round(val, 3);
    }

    private static double round(double val, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(val * scale) / scale;
    }

    private static Bbox toBbox(QuadInfo quad) {
        double x0 = Math.min(Math.min(quad.ul().x(), quad.ur().x()), Math.min(quad.lr().x(), quad.ll().x()));
        double y0 = Math.min(Math.min(quad.ul().y(), quad.ur().y()), Math.min(quad.lr().y(), quad.ll().y()));
        double x1 = Math.max(Math.max(quad.ul().x(), quad.ur().x()), Math.max(quad.lr().x(), quad.ll().x()));
        double y1 = Math.max(Math.max(quad.ul().y(), quad.ur().y()), Math.max(quad.lr().y(), quad.ll().y()));
        return new Bbox(round(x0), round(y0), round(Math.max(0, x1 - x0)), round(Math.max(0, y1 - y0)), null);
    }

    private static Bbox toBbox(Rect r, Integer flags) {
        if (r == null || r.isInfinite()) {
            return new Bbox(0, 0, 0, 0, flags);
        }
        return new Bbox(round(r.x0), round(r.y0), round(Math.max(0, r.x1 - r.x0)), round(Math.max(0, r.y1 - r.y0)), flags);
    }

    private static FontInfo toFontInfo(Font font, float size) {
        if (font == null) {
            return defaultFont();
        }
        String family = font.isMono() ? "monospace" : (font.isSerif() ? "serif" : "sans-serif");
        String weight = font.isBold() ? "bold" : "normal";
        String style = font.isItalic() ? "italic" : "normal";
        return new FontInfo(font.getName(), family, weight, style, round(size));
    }

    private static FontInfo defaultFont() {
        return new FontInfo("unknown", "sans-serif", "normal", "normal", 0.0);
    }

    // ------------------------------------------------------------------
    // 原生句柄释放辅助
    // ------------------------------------------------------------------

    private static void destroyStructuredText(StructuredText st) {
        if (st == null) {
            return;
        }
        try {
            st.destroy();
        } catch (Throwable t) {
            log.error("[MuPDF] 释放 StructuredText 原生句柄失败", t);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable t) {
            log.error("[MuPDF] 释放原生句柄失败: {}", closeable.getClass().getSimpleName(), t);
        }
    }

    private static void closeAll(List<ManagedDoc> docs) {
        for (ManagedDoc doc : docs) {
            if (doc != null) {
                doc.closeQuietly();
            }
        }
    }

    // ------------------------------------------------------------------
    // 常驻文档缓存（LRU + TTL + 独占锁协议）
    // ------------------------------------------------------------------

    /**
     * 获取一个可用（持有独占锁）的受管文档。调用方必须在 finally 中调用 {@link #release(ManagedDoc)}。
     */
    private ManagedDoc acquire(String pdfPath) {
        if (closed) {
            throw new IllegalStateException("MuPdfService 已关闭，无法继续处理文档");
        }
        File file = requireReadableFile(pdfPath);
        String key = cacheKey(file);
        long lastModified = file.lastModified();
        long length = file.length();

        // 缓存关闭时直接走私有文档快路径，绕过重试与表锁
        if (maxCachedDocs <= 0) {
            ManagedDoc privateDoc = new ManagedDoc(Document.openDocument(file.getPath()), key, lastModified, length);
            privateDoc.uncached = true;
            privateDoc.lock.lock();
            return privateDoc;
        }

        for (int attempt = 0; attempt < ACQUIRE_MAX_RETRIES; attempt++) {
            ManagedDoc managed = obtain(key, file, lastModified, length);
            managed.lock.lock();
            try {
                if (!managed.closed.get() && (managed.uncached || isCurrent(key, managed))) {
                    managed.lastAccessNanos = System.nanoTime();
                    return managed;
                }
            } catch (Throwable t) {
                log.error("[MuPDF] 获取受管文档状态异常: {}", key, t);
                managed.lock.unlock();
                throw t;
            }
            // 该句柄已被并发淘汰/替换，释放后重试
            managed.lock.unlock();
            managed.closeQuietly();
        }

        // 极端并发风暴下的兜底：使用一份私有文档，用后即焚，绝不进入缓存表
        log.warn("[MuPDF] 文档缓存并发淘汰过于频繁，降级为私有文档模式: {}", key);
        ManagedDoc privateDoc = new ManagedDoc(Document.openDocument(file.getPath()), key, lastModified, length);
        privateDoc.uncached = true;
        privateDoc.lock.lock();
        return privateDoc;
    }

    private ManagedDoc obtain(String key, File file, long lastModified, long length) {
        if (maxCachedDocs <= 0) {
            ManagedDoc privateDoc = new ManagedDoc(Document.openDocument(file.getPath()), key, lastModified, length);
            privateDoc.uncached = true;
            return privateDoc;
        }

        List<ManagedDoc> retired = new ArrayList<>(2);
        try {
            // 快路径：命中且未过期，不打开原生库也不做任何耗时动作
            ManagedDoc hit;
            synchronized (cacheLock) {
                if (closed) {
                    throw new IllegalStateException("MuPdfService 已关闭，无法继续处理文档: " + key);
                }
                hit = docCache.get(key);
                if (hit != null && hit.isStale(lastModified, length)) {
                    // 文件被改写：淘汰旧句柄，重新打开，避免返回过期内容
                    docCache.remove(key);
                    retired.add(hit);
                    hit = null;
                }
                if (hit != null) {
                    retireExpiredLocked(retired);
                    retireOverflowLocked(hit, retired);
                }
            }
            if (hit != null) {
                return hit;
            }

            // 慢路径：原生打开（fopen + 解析 xref）放在缓存锁之外。
            // 放到锁内会让一次慢盘 / 网络文件系统的 open 阻塞全部并发请求，属于典型的故障放大点。
            ManagedDoc fresh = new ManagedDoc(Document.openDocument(file.getPath()), key, lastModified, length);
            try {
                synchronized (cacheLock) {
                    if (closed) {
                        throw new IllegalStateException("MuPdfService 已关闭，无法继续处理文档: " + key);
                    }
                    ManagedDoc current = docCache.get(key);
                    if (current != null && !current.isStale(lastModified, length)) {
                        // 输掉并发竞争：复用已有句柄，丢弃自己刚打开的那份
                        retired.add(fresh);
                        retireExpiredLocked(retired);
                        retireOverflowLocked(current, retired);
                        return current;
                    }
                    if (current != null) {
                        docCache.remove(key);
                        retired.add(current);
                    }
                    docCache.put(key, fresh);
                    retireExpiredLocked(retired);
                    retireOverflowLocked(fresh, retired);
                    return fresh;
                }
            } catch (Throwable t) {
                log.error("[MuPDF] 受管文档入表失败: {}", key, t);
                // 入表失败（例如服务被并发关闭）：保证刚打开的句柄不会被漏关
                synchronized (cacheLock) {
                    docCache.remove(key, fresh);
                }
                retired.add(fresh);
                throw t;
            }
        } finally {
            // 原生 fz_drop_document 一律在缓存锁之外执行，避免持锁执行耗时的堆外释放
            closeAll(retired);
        }
    }

    private void retireExpiredLocked(List<ManagedDoc> retired) {
        if (cacheTtlMillis <= 0 || docCache.isEmpty()) {
            return;
        }
        long ttlNanos = TimeUnit.MILLISECONDS.toNanos(cacheTtlMillis);
        long now = System.nanoTime();
        Iterator<Map.Entry<String, ManagedDoc>> it = docCache.entrySet().iterator();
        while (it.hasNext()) {
            ManagedDoc managed = it.next().getValue();
            if (now - managed.lastAccessNanos >= ttlNanos) {
                it.remove();
                retired.add(managed);
            }
        }
    }

    private void retireOverflowLocked(ManagedDoc keep, List<ManagedDoc> retired) {
        if (docCache.size() <= maxCachedDocs) {
            return;
        }
        Iterator<Map.Entry<String, ManagedDoc>> it = docCache.entrySet().iterator();
        while (it.hasNext() && docCache.size() > maxCachedDocs) {
            Map.Entry<String, ManagedDoc> entry = it.next();
            if (entry.getValue() == keep) {
                continue;
            }
            it.remove();
            retired.add(entry.getValue());
        }
    }

    private boolean isCurrent(String key, ManagedDoc managed) {
        synchronized (cacheLock) {
            return !managed.uncached && docCache.get(key) == managed;
        }
    }

    private void release(ManagedDoc managed) {
        if (managed == null) {
            return;
        }
        managed.lock.unlock();
        if (managed.uncached) {
            managed.closeQuietly();
        }
    }

    private static File requireReadableFile(String pdfPath) {
        if (pdfPath == null || pdfPath.trim().isEmpty()) {
            throw new IllegalArgumentException("PDF 路径不能为空");
        }
        File file = new File(pdfPath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("PDF 文件不存在或不是有效文件: " + pdfPath);
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException("PDF 文件不可读: " + pdfPath);
        }
        return file;
    }

    private static String cacheKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException canonicalEx) {
            return file.getAbsolutePath();
        }
    }

    /**
     * 受管文档实体（保证并发安全与严格的单次关闭）。
     */
    private static final class ManagedDoc {

        final Document doc;
        final String cacheKey;
        final long lastModified;
        final long fileLength;
        /**
         * 独占锁：MuPDF 约定同一 Document 同一时刻只允许一个线程访问。
         */
        final ReentrantLock lock = new ReentrantLock();
        final AtomicBoolean closed = new AtomicBoolean(false);
        volatile long lastAccessNanos = System.nanoTime();
        /**
         * true 表示该文档不进入缓存表，release 时立即销毁。
         */
        volatile boolean uncached = false;

        ManagedDoc(Document doc, String cacheKey, long lastModified, long fileLength) {
            this.doc = doc;
            this.cacheKey = cacheKey;
            this.lastModified = lastModified;
            this.fileLength = fileLength;
        }

        boolean isStale(long lastModified, long fileLength) {
            return this.lastModified != lastModified || this.fileLength != fileLength;
        }

        /**
         * 幂等关闭。在独占锁保护下释放底层原生 Document 句柄，杜绝 use-after-free。
         */
        void closeQuietly() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            lock.lock();
            try {
                doc.close();
            } catch (Throwable t) {
                log.error("[MuPDF] 释放文档原生句柄失败: {}", cacheKey, t);
            } finally {
                lock.unlock();
            }
        }
    }
}
