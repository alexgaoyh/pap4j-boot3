package cn.net.pap.common.mupdf.service;

import cn.net.pap.common.mupdf.loader.MuPdfNativeLoader;
import com.artifex.mupdf.fitz.Buffer;
import com.artifex.mupdf.fitz.ColorSpace;
import com.artifex.mupdf.fitz.Context;
import com.artifex.mupdf.fitz.Device;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.DocumentWriter;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.Pixmap;
import com.artifex.mupdf.fitz.Rect;
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
    public String extractCoordinatesJson(String pdfPath, int pageNum) {
        ManagedDoc managed = acquire(pdfPath);
        Page page = null;
        Buffer buf = null;
        DocumentWriter writer = null;
        Device device = null;
        try {
            int pageCount = managed.doc.countPages();
            checkPageNumber(pageNum, pageCount);

            page = managed.doc.loadPage(pageNum);
            buf = new Buffer();
            writer = new DocumentWriter(buf, "stext.json", null);
            // beginPage 在 JNI 层执行了 fz_keep_device，返回的 Device 由 Java 侧持有一份引用，
            // 必须显式 destroy()，否则只能等待 GC 的 finalizer 回收，形成堆外内存滞留。
            device = writer.beginPage(page.getBounds());
            page.run(device, new Matrix());
            writer.endPage();
            // close 负责把 JSON 尾部（"]\n"）写入 Buffer，因此必须在读取 Buffer 之前调用。
            writer.close();

            String pageJson = new String(buf.asByteArray(), StandardCharsets.UTF_8).trim();
            // 与 mutool draw -F stext.json 的格式完全一致（包含 file 与 pages 包装）
            return buildPagedJson(pdfPath, pageJson);

        } finally {
            destroyDevice(device);
            destroyWriter(writer);
            closeQuietly(buf);
            closeQuietly(page);
            release(managed);
            // JIT 安全屏障：防止底层 C 仍在运行时 managed 句柄被 GC 提前判定死亡
            Reference.reachabilityFence(managed);
        }
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
        } catch (LinkageError e) {
            log.warn("[MuPDF] 未检测到可用的 pap4j-common-vips（{}），回退返回 PNG 图像", e.getMessage());
            return renderPageToPng(pdfPath, pageNum, dpi);
        } catch (IOException | RuntimeException e) {
            // PAM 中继偶发不被 vips 接受时，改用 PNG 中继重试一次（Error 类不再包容，避免堆栈错误被吞）
            log.debug("[MuPDF] PAM 转码 WebP 遇到问题，回退通过 PNG 中继转码: {}", e.getMessage());
            byte[] pngBytes = renderPageToPayload(pdfPath, pageNum, dpi, true);
            try {
                return cn.net.pap.common.vips.VipsImageProcessor.convertFormat(pngBytes, format);
            } catch (LinkageError e2) {
                log.warn("[MuPDF] 未检测到可用的 pap4j-common-vips（{}），回退返回 PNG 图像", e2.getMessage());
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
            log.warn("[MuPDF] 收缩全局 Store 失败: ", t);
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
                log.warn("[MuPDF] 清空全局 Store 失败: ", t);
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

    /**
     * 组装与 mutool draw -F stext.json 完全一致的 {"file":...,"pages":[...]} 包装。
     */
    private static String buildPagedJson(String pdfPath, String pageJsonArray) {
        StringBuilder sb = new StringBuilder(pageJsonArray.length() + pdfPath.length() + 32);
        sb.append("{\"file\":\"");
        escapeJson(sb, pdfPath);
        sb.append("\",\"pages\":").append(pageJsonArray).append("}\n");
        return sb.toString();
    }

    /**
     * 标准 JSON 字符串转义（旧实现仅处理了反斜杠，含引号/控制字符的路径会输出非法 JSON）。
     */
    private static void escapeJson(StringBuilder sb, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
    }

    // ------------------------------------------------------------------
    // 原生句柄释放辅助
    // ------------------------------------------------------------------

    private static void destroyWriter(DocumentWriter writer) {
        if (writer == null) {
            return;
        }
        // close 落盘/落 Buffer（幂等），destroy 才是真正 fz_drop_document_writer，
        // 两者缺一都会导致堆外内存滞留到 GC finalizer 才回收。
        try {
            writer.close();
        } catch (Throwable t) {
            log.debug("[MuPDF] 关闭 DocumentWriter 失败: {}", t.getMessage());
        }
        try {
            writer.destroy();
        } catch (Throwable t) {
            log.warn("[MuPDF] 释放 DocumentWriter 原生句柄失败", t);
        }
    }

    private static void destroyDevice(Device device) {
        if (device == null) {
            return;
        }
        try {
            device.destroy();
        } catch (Throwable t) {
            log.warn("[MuPDF] 释放 Device 原生句柄失败", t);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable t) {
            log.warn("[MuPDF] 释放原生句柄失败: {}", closeable.getClass().getSimpleName(), t);
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
        } catch (IOException e) {
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
                log.warn("[MuPDF] 释放文档原生句柄失败: {}", cacheKey, t);
            } finally {
                lock.unlock();
            }
        }
    }
}
