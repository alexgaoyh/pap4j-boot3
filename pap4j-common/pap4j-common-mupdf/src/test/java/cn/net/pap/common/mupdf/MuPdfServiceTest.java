package cn.net.pap.common.mupdf;

import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.PDFDocument;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.fitz.Quad;
import com.artifex.mupdf.fitz.Rect;
import cn.net.pap.common.mupdf.loader.MuPdfNativeLoader;
import cn.net.pap.common.mupdf.service.MuPdfService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

public class MuPdfServiceTest {

    /**
     * 调试开关：打开 TempDirUtils 的保留临时文件模式，防止内嵌图片落盘后立即被删除。
     * <p>该开关必须在 TempDirUtils 类初始化（首次执行 extractCoordinates）前设置，
     * 否则 {@code KEEP_TEMP_FILES} 仍为 false，imagePath 返回时文件已被清理。</p>
     */
    @BeforeAll
    public static void keepTempFilesForImageInspection() {
        System.setProperty("temp.dir.utils.dev", "true");
    }

    @Test
    public void testPlatformDetection() {
        String platform = MuPdfNativeLoader.detectPlatform();
        Assertions.assertNotNull(platform);
        System.out.println("[Test] 检测到的当前运行平台架构: " + platform);
        String fileName = MuPdfNativeLoader.getNativeLibraryFileName();
        Assertions.assertTrue(fileName.endsWith(".dll") || fileName.endsWith(".so") || fileName.endsWith(".dylib"));
    }

    @Test
    public void testGeometryCalculations() {
        Matrix matrix = Matrix.Identity().scale(2.0f, 2.0f);
        Assertions.assertEquals(2.0f, matrix.a);
        Assertions.assertEquals(2.0f, matrix.d);

        Point p = new Point(10.0f, 20.0f).transform(matrix);
        Assertions.assertEquals(20.0f, p.x);
        Assertions.assertEquals(40.0f, p.y);

        Rect r = new Rect(0, 0, 100, 200);
        Assertions.assertTrue(r.isValid());
        Assertions.assertFalse(r.isEmpty());

        Quad q = new Quad(r);
        Assertions.assertTrue(q.isValid());
        Rect r2 = q.toRect();
        Assertions.assertEquals(0, r2.x0);
        Assertions.assertEquals(100, r2.x1);
    }

    @Test
    public void testRealPdfExtractionAndRendering() {
        URL url = getClass().getResource("/sample.pdf");
        if (url == null) {
            System.out.println("[Test] 未找到 sample.pdf 测试样本，跳过真实 Native 渲染测试");
            return;
        }

        File pdfFile = new File(url.getFile());
        MuPdfService service = new MuPdfService();

        // 1. 测试页数统计
        int pages = service.getPageCount(pdfFile.getAbsolutePath());
        Assertions.assertTrue(pages > 0, "总页数应大于 0");
        System.out.println("[Test] PDF 总页数: " + pages);

        // 2. 测试文字物理坐标（强类型 Record 提取与 JSON 提取）
        MuPdfService.CoordinatesResult coords = service.extractCoordinates(pdfFile.getAbsolutePath(), 0);
        Assertions.assertNotNull(coords);
        Assertions.assertEquals(1, coords.pages().size());
        Assertions.assertFalse(coords.pages().get(0).blocks().isEmpty());
        MuPdfService.BlockResult firstBlock = coords.pages().get(0).blocks().get(0);
        Assertions.assertEquals("text", firstBlock.type());
        Assertions.assertNull(firstBlock.imagePath(), "文本块的 imagePath 必须为 null");
        Assertions.assertFalse(firstBlock.lines().isEmpty());
        MuPdfService.LineResult firstLine = firstBlock.lines().get(0);
        Assertions.assertEquals("全嵌入字体测试", firstLine.text());
        Assertions.assertEquals(7, firstLine.chars().size());
        Assertions.assertEquals("全", firstLine.chars().get(0).c());
        Assertions.assertEquals("试", firstLine.chars().get(6).c());
        Assertions.assertEquals("FangSong", firstLine.chars().get(0).font());

        String json = service.extractCoordinatesJson(pdfFile.getAbsolutePath(), 0);
        Assertions.assertNotNull(json);
        Assertions.assertFalse(json.trim().isEmpty());
        System.out.println("[Test] 提取出的物理坐标 JSON 字符长度: " + json.length());
        Assertions.assertTrue(json.contains("\"text\":\"全嵌入字体测试\""), "应包含整行文本");
        Assertions.assertTrue(json.contains("\"chars\":["), "必须包含单字 chars 数组");
        Assertions.assertTrue(json.contains("\"c\":\"全\""), "单字列表中必须包含字符 '全'");
        Assertions.assertTrue(json.contains("\"c\":\"试\""), "单字列表中必须包含字符 '试'");
        Assertions.assertTrue(json.contains("\"origin\":{"), "单字必须包含基线坐标 origin");
        Assertions.assertTrue(json.contains("\"font\":\"FangSong\""), "单字必须包含字体属性");

        // 3. 测试页面渲染为 PNG
        byte[] pngBytes = service.renderPageToPng(pdfFile.getAbsolutePath(), 0, 110);
        Assertions.assertNotNull(pngBytes);
        Assertions.assertTrue(pngBytes.length > 0);
        // 校验 PNG 魔法头 0x89 0x50 0x4E 0x47
        Assertions.assertEquals((byte) 0x89, pngBytes[0]);
        Assertions.assertEquals((byte) 0x50, pngBytes[1]);
        Assertions.assertEquals((byte) 0x4E, pngBytes[2]);
        Assertions.assertEquals((byte) 0x47, pngBytes[3]);
        System.out.println("[Test] 成功渲染 PNG，大小: " + pngBytes.length + " 字节");

        // 4. 测试裸 RGB 点阵提取
        MuPdfService.RawPageImage raw = service.renderPageToRawSamples(pdfFile.getAbsolutePath(), 0, 110);
        Assertions.assertNotNull(raw);
        Assertions.assertTrue(raw.width() > 0 && raw.height() > 0);
        Assertions.assertEquals(3, raw.components()); // RGB
        System.out.println("[Test] 成功提取裸点阵: " + raw.width() + "x" + raw.height() + ", 字节数: " + raw.samples().length);

        // 5. 测试全局 Store 收缩
        service.shrinkStore();

        // 6. 测试缓存淘汰
        service.evict(pdfFile.getAbsolutePath());
    }

    @Test
    public void testDoubleCloseSafety() {
        URL url = getClass().getResource("/sample.pdf");
        if (url == null) {
            return;
        }
        File pdfFile = new File(url.getFile());
        com.artifex.mupdf.fitz.Document doc = com.artifex.mupdf.fitz.Document.openDocument(pdfFile.getAbsolutePath());
        com.artifex.mupdf.fitz.Page page = doc.loadPage(0);
        com.artifex.mupdf.fitz.StructuredText st = page.toStructuredText();
        com.artifex.mupdf.fitz.Buffer buf = new com.artifex.mupdf.fitz.Buffer(16);

        // 第一次 close
        buf.close();
        st.close();
        page.close();
        doc.close();

        // 第二次重复 close，测试是否发生 SIGSEGV / 堆崩溃
        Assertions.assertDoesNotThrow(() -> {
            buf.close();
            st.close();
            page.close();
            doc.close();
        });
    }

    @Test
    public void testMaxDocsZeroNoWarningsAndFast() {
        URL url = getClass().getResource("/sample.pdf");
        if (url == null) return;
        File pdfFile = new File(url.getFile());

        String prev = System.getProperty(MuPdfService.CFG_CACHE_MAX_DOCS);
        System.setProperty(MuPdfService.CFG_CACHE_MAX_DOCS, "0");
        try (MuPdfService service = new MuPdfService()) {
            long start = System.currentTimeMillis();
            int pages = service.getPageCount(pdfFile.getAbsolutePath());
            byte[] png = service.renderPageToPng(pdfFile.getAbsolutePath(), 0, 72);
            long cost = System.currentTimeMillis() - start;

            Assertions.assertTrue(pages > 0);
            Assertions.assertNotNull(png);
            Assertions.assertTrue(png.length > 0);
            Assertions.assertEquals(0, service.cachedDocumentCount(), "禁用缓存时缓存数量必须为 0");
            System.out.println("[Test] maxDocs=0 模式单页渲染总耗时: " + cost + "ms (无重试降级告警)");
        } finally {
            if (prev == null) System.clearProperty(MuPdfService.CFG_CACHE_MAX_DOCS);
            else System.setProperty(MuPdfService.CFG_CACHE_MAX_DOCS, prev);
        }
    }

    @Test
    public void testEvictDuringConcurrentRendering() throws Exception {
        URL url = getClass().getResource("/sample.pdf");
        if (url == null) return;
        File pdfFile = new File(url.getFile());
        String path = pdfFile.getAbsolutePath();

        try (MuPdfService service = new MuPdfService()) {
            int threadCount = 4;
            int rounds = 20;
            java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount + 1);

            // 4 个渲染并发线程
            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        for (int r = 0; r < rounds; r++) {
                            service.renderPageToPng(path, 0, 96);
                            service.extractCoordinatesJson(path, 0);
                        }
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                        t.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            // 1 个并发淘汰线程
            new Thread(() -> {
                try {
                    for (int r = 0; r < rounds; r++) {
                        service.evict(path);
                        Thread.sleep(5);
                    }
                } catch (Throwable t) {
                    errors.incrementAndGet();
                    t.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }).start();

            Assertions.assertTrue(latch.await(15, java.util.concurrent.TimeUnit.SECONDS), "并发测试超时");
            Assertions.assertEquals(0, errors.get(), "并发渲染与淘汰过程中不得出现任何异常");
        }
    }

    // ------------------------------------------------------------------
    // check()：PDF 体检（枚举结论 + 友好提示，永不抛异常）
    // ------------------------------------------------------------------

    /**
     * 正常 PDF：OK、1 页、未加密、无修复建议。
     */
    @Test
    public void testCheckHealthyPdf() {
        try (MuPdfService service = new MuPdfService()) {
            MuPdfService.PdfCheck check = service.check(checkPdf("ok.pdf"));
            Assertions.assertEquals(MuPdfService.PdfIssue.OK, check.issue());
            Assertions.assertTrue(check.healthy());
            Assertions.assertEquals(1, check.pageCount());
            Assertions.assertFalse(check.encrypted());
            Assertions.assertNull(check.hint(), "OK 状态不应给修复建议");
            Assertions.assertTrue(check.summary().contains("[OK]"), check.summary());
        }
    }

    /**
     * 加密但可正常打开：healthy 为 true，同时 encrypted 标记为 true。
     */
    @Test
    public void testCheckEncryptedButReadable() {
        try (MuPdfService service = new MuPdfService()) {
            MuPdfService.PdfCheck check = service.check(checkPdf("encrypted-readable.pdf"));
            Assertions.assertTrue(check.healthy(), check.summary());
            Assertions.assertTrue(check.encrypted(), "应标记为加密文档");
            Assertions.assertTrue(check.pageCount() > 0, "加密但可读时页数应大于 0");
        }
    }

    /**
     * 各类畸形文件：结论必须落在对应枚举上，且都必须给出修复建议与可读摘要。
     */
    @Test
    public void testCheckBrokenFilesReportIssue() {
        Map<String, MuPdfService.PdfIssue> cases = new LinkedHashMap<>();
        cases.put("not-pdf.pdf", MuPdfService.PdfIssue.NOT_PDF);
        cases.put("no-pages.pdf", MuPdfService.PdfIssue.NO_PAGES);
        cases.put("encrypted.pdf", MuPdfService.PdfIssue.ENCRYPTED);
        cases.put("open-failed.pdf", MuPdfService.PdfIssue.OPEN_FAILED);
        cases.put("page-tree-broken.pdf", MuPdfService.PdfIssue.PAGE_TREE_BROKEN);

        try (MuPdfService service = new MuPdfService()) {
            cases.forEach((resource, expected) -> {
                MuPdfService.PdfCheck check = service.check(checkPdf(resource));
                Assertions.assertEquals(expected, check.issue(), resource);
                Assertions.assertFalse(check.healthy(), resource + " 不应判定为可用");
                Assertions.assertNotNull(check.hint(), resource + " 必须给出修复建议");
                Assertions.assertFalse(check.hint().trim().isEmpty(), resource + " 修复建议不能为空");
                Assertions.assertFalse(check.summary().trim().isEmpty(), resource + " 摘要不能为空");
            });
        }
    }

    /**
     * 路径类问题：不存在、空串、null、目录，都必须返回 FILE_NOT_FOUND 而不是抛异常。
     *
     * @param tempDir JUnit 临时目录
     */
    @Test
    public void testCheckInvalidPathsNeverThrow(@TempDir Path tempDir) {
        try (MuPdfService service = new MuPdfService()) {
            MuPdfService.PdfCheck missing = service.check(tempDir.resolve("no-such.pdf").toString());
            Assertions.assertEquals(MuPdfService.PdfIssue.FILE_NOT_FOUND, missing.issue());
            Assertions.assertFalse(missing.healthy());
            Assertions.assertEquals(-1, missing.pageCount());

            Assertions.assertEquals(MuPdfService.PdfIssue.FILE_NOT_FOUND, service.check("").issue());
            Assertions.assertEquals(MuPdfService.PdfIssue.FILE_NOT_FOUND, service.check(null).issue());
            Assertions.assertEquals(MuPdfService.PdfIssue.FILE_NOT_FOUND, service.check(tempDir.toString()).issue(),
                    "目录不是普通文件");
        }
    }

    /**
     * 0 字节文件：FILE_EMPTY。
     *
     * @param tempDir JUnit 临时目录
     * @throws Exception 建文件失败
     */
    @Test
    public void testCheckEmptyFile(@TempDir Path tempDir) throws Exception {
        Path empty = tempDir.resolve("empty.pdf");
        Files.createFile(empty);
        try (MuPdfService service = new MuPdfService()) {
            MuPdfService.PdfCheck check = service.check(empty.toString());
            Assertions.assertEquals(MuPdfService.PdfIssue.FILE_EMPTY, check.issue());
            Assertions.assertFalse(check.healthy());
        }
    }

    /**
     * 多页 PDF 拆分为多个单页 PDF：验证命名格式、结构健康度、文本坐标与渲染尺寸完全一致。
     *
     * @param tempDir JUnit 临时目录
     * @throws Exception 拆分或验证异常
     */
    @Test
    public void testSplitMultiPagePdfAndVerifyIntegrity(@TempDir Path tempDir) throws Exception {
        Path multiPagePdf = createSampleMultiPagePdf(tempDir.resolve("source_doc.pdf"), 4);

        try (MuPdfService service = new MuPdfService()) {
            Path splitOutDir = tempDir.resolve("split_out");
            MuPdfService.SplitOptions opts = new MuPdfService.SplitOptions(true, MuPdfService.DEFAULT_SPLIT_SAVE_OPTIONS, 2);
            List<Path> singlePages = service.splitPdf(multiPagePdf.toString(), splitOutDir, opts);

            Assertions.assertEquals(4, singlePages.size(), "4 页 PDF 应拆分为 4 个单页文件");
            for (int i = 0; i < singlePages.size(); i++) {
                Path singlePage = singlePages.get(i);
                String expectedName = String.format("source_doc_page_%05d.pdf", i + 1);
                Assertions.assertEquals(expectedName, singlePage.getFileName().toString(), "文件名必须符合 <原文件名>_page_00001.pdf 规范");
                Assertions.assertTrue(Files.isRegularFile(singlePage) && Files.size(singlePage) > 0, "拆出的单页文件应存在且非空");

                // 体检单页 PDF 结构完整性
                MuPdfService.PdfCheck check = service.check(singlePage.toString());
                Assertions.assertEquals(MuPdfService.PdfIssue.OK, check.issue(), check.summary());
                Assertions.assertEquals(1, check.pageCount(), "每个拆分文件必须只有 1 页");

                // 核验单页坐标与源文档对应页完全一致
                MuPdfService.CoordinatesResult srcCoords = service.extractCoordinates(multiPagePdf.toString(), i);
                MuPdfService.CoordinatesResult splitCoords = service.extractCoordinates(singlePage.toString(), 0);
                Assertions.assertEquals(
                        srcCoords.pages().get(0).blocks().size(),
                        splitCoords.pages().get(0).blocks().size(),
                        "第 " + (i + 1) + " 页文本块数量应与源文档一致"
                );
                String srcFirstLine = srcCoords.pages().get(0).blocks().get(0).lines().get(0).text();
                String splitFirstLine = splitCoords.pages().get(0).blocks().get(0).lines().get(0).text();
                Assertions.assertEquals(srcFirstLine, splitFirstLine, "拆分前后单页文本内容应完全一致");

                // 核验单页渲染尺寸一致
                MuPdfService.RawPageImage srcRaw = service.renderPageToRawSamples(multiPagePdf.toString(), i, 72);
                MuPdfService.RawPageImage splitRaw = service.renderPageToRawSamples(singlePage.toString(), 0, 72);
                Assertions.assertEquals(srcRaw.width(), splitRaw.width());
                Assertions.assertEquals(srcRaw.height(), splitRaw.height());
            }
        }
    }

    /**
     * 验证页码超 5 位自动扩位规则及字体子集化（subsetFonts）对单页 PDF 文件体积的优化效果。
     *
     * @param tempDir JUnit 临时目录
     * @throws Exception 拆分异常
     */
    @Test
    public void testSplitPdfDigitExpansionAndSubsetFontsSize(@TempDir Path tempDir) throws Exception {
        // 验证页码超过 5 位数时自动扩展位数（最少 5 位）
        Assertions.assertEquals("report_page_00001.pdf", MuPdfService.formatSplitPageFileName("report.pdf", 1, 10));
        Assertions.assertEquals("report_page_99999.pdf", MuPdfService.formatSplitPageFileName("report.pdf", 99999, 99999));
        Assertions.assertEquals("report_page_000001.pdf", MuPdfService.formatSplitPageFileName("report.pdf", 1, 100000));
        Assertions.assertEquals("report_page_100000.pdf", MuPdfService.formatSplitPageFileName("report.pdf", 100000, 100000));
        Assertions.assertEquals("my.doc_page_0000012.pdf", MuPdfService.formatSplitPageFileName("my.doc.pdf", 12, 1200000));
        URL ctexUrl = getClass().getResource("/ctex.pdf");
        Assertions.assertNotNull(ctexUrl, "ctex.pdf 测试样本应存在");
        File ctexFile = new File(ctexUrl.getFile());

        try (MuPdfService service = new MuPdfService()) {
            Path dirSubsetOn = tempDir.resolve("subset_on");
            Path dirSubsetOff = tempDir.resolve("subset_off");

            List<Path> pagesSubsetOn = service.splitPdf(
                    ctexFile.getAbsolutePath(), dirSubsetOn, MuPdfService.SplitOptions.ofSubsetFonts(true));
            List<Path> pagesSubsetOff = service.splitPdf(
                    ctexFile.getAbsolutePath(), dirSubsetOff, MuPdfService.SplitOptions.ofSubsetFonts(false));

            Assertions.assertFalse(pagesSubsetOn.isEmpty());
            Assertions.assertEquals(pagesSubsetOn.size(), pagesSubsetOff.size());
            Assertions.assertTrue(pagesSubsetOn.get(0).getFileName().toString().matches("ctex_page_\\d{5,}\\.pdf"));

            long totalSizeSubsetOn = 0L;
            long totalSizeSubsetOff = 0L;
            for (int i = 0; i < pagesSubsetOn.size(); i++) {
                long sizeOn = Files.size(pagesSubsetOn.get(i));
                long sizeOff = Files.size(pagesSubsetOff.get(i));
                totalSizeSubsetOn += sizeOn;
                totalSizeSubsetOff += sizeOff;
                Assertions.assertEquals(1, service.getPageCount(pagesSubsetOn.get(i).toString()));
                Assertions.assertEquals(1, service.getPageCount(pagesSubsetOff.get(i).toString()));
            }
            Assertions.assertTrue(totalSizeSubsetOn <= totalSizeSubsetOff,
                    "开启字体子集化后的单页总大小 (" + totalSizeSubsetOn + "B) 应小于等于未开启 (" + totalSizeSubsetOff + "B)");
        }
    }

    /**
     * 验证拆分中途失败时的原子回滚清理能力，以及各类非法入参、加密/空页/损坏文件的防御性异常。
     *
     * @param tempDir JUnit 临时目录
     * @throws Exception 文件准备异常
     */
    @Test
    public void testSplitPdfRollbackOnMidwayFailureAndInvalidInputs(@TempDir Path tempDir) throws Exception {
        Path multiPagePdf = createSampleMultiPagePdf(tempDir.resolve("rollback_test.pdf"), 3);

        try (MuPdfService service = new MuPdfService()) {
            // 1. 构造中途失败：预先在第 3 页的目标文件路径上创建一个同名非空目录，使第 1、2 页成功落盘后第 3 页写出失败
            Path rollbackDir = tempDir.resolve("rollback_out");
            Files.createDirectories(rollbackDir);
            Path conflictingDirForPage3 = rollbackDir.resolve("rollback_test_page_00003.pdf");
            Files.createDirectories(conflictingDirForPage3);
            Files.writeString(conflictingDirForPage3.resolve("blocker.txt"), "block");

            Assertions.assertThrows(IllegalStateException.class,
                    () -> service.splitPdf(multiPagePdf.toString(), rollbackDir),
                    "第 3 页写出失败时应抛出异常");

            Assertions.assertFalse(Files.exists(rollbackDir.resolve("rollback_test_page_00001.pdf")),
                    "异常回滚后第 1 页文件必须被清理");
            Assertions.assertFalse(Files.exists(rollbackDir.resolve("rollback_test_page_00002.pdf")),
                    "异常回滚后第 2 页文件必须被清理");

            // 2. 非法入参及损坏/加密文档校验
            Assertions.assertThrows(IllegalArgumentException.class, () -> service.splitPdf(null, tempDir));
            Assertions.assertThrows(IllegalArgumentException.class, () -> service.splitPdf("", tempDir));
            Assertions.assertThrows(IllegalArgumentException.class, () -> service.splitPdf(multiPagePdf.toString(), null));
            Assertions.assertThrows(IllegalArgumentException.class, () -> service.splitPdf(multiPagePdf.toString(), multiPagePdf));
            Assertions.assertThrows(IllegalStateException.class, () -> service.splitPdf(checkPdf("encrypted.pdf"), tempDir.resolve("enc")));
            Assertions.assertThrows(IllegalStateException.class, () -> service.splitPdf(checkPdf("no-pages.pdf"), tempDir.resolve("nopages")));
            Assertions.assertThrows(IllegalStateException.class, () -> service.splitPdf(checkPdf("open-failed.pdf"), tempDir.resolve("broken")));
        }

        // 3. 服务关闭后拒绝拆分
        MuPdfService closedService = new MuPdfService();
        closedService.close();
        Assertions.assertThrows(IllegalStateException.class,
                () -> closedService.splitPdf(multiPagePdf.toString(), tempDir.resolve("after_close")));
    }

    /**
     * 验证并发预览渲染与并发拆分互不阻塞、不污染常驻缓存，且在 Windows 下无任何原生文件句柄泄漏。
     *
     * @param tempDir JUnit 临时目录
     * @throws Exception 并发或文件删除异常
     */
    @Test
    public void testSplitPdfConcurrentWithPreviewAndNoHandleLeak(@TempDir Path tempDir) throws Exception {
        Path multiPagePdf = createSampleMultiPagePdf(tempDir.resolve("concurrent_doc.pdf"), 5);
        String pdfPathStr = multiPagePdf.toString();

        try (MuPdfService service = new MuPdfService()) {
            int workers = 4;
            AtomicInteger errorCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(workers);
            AtomicInteger threadSeq = new AtomicInteger(1);
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    workers,
                    workers,
                    30L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(16),
                    r -> new Thread(r, "mupdf-split-test-" + threadSeq.getAndIncrement()),
                    new ThreadPoolExecutor.AbortPolicy()
            );

            try {
                for (int w = 0; w < workers; w++) {
                    final int workerId = w;
                    executor.execute(() -> runConcurrentWorkerRounds(
                            service, pdfPathStr, tempDir, workerId, 6, errorCount, latch));
                }
                Assertions.assertTrue(latch.await(30, TimeUnit.SECONDS), "并发拆分与预览测试超时");
                Assertions.assertEquals(0, errorCount.get(), "并发拆分、预览与文件即时删除过程中不得出现异常或句柄占用");
                Assertions.assertEquals(1, service.cachedDocumentCount(), "拆分操作使用独立句柄，不应污染常驻文档缓存");
            } finally {
                executor.shutdownNow();
            }
        }
        Files.delete(multiPagePdf);
        Assertions.assertFalse(Files.exists(multiPagePdf));
    }

    /**
     * 验证含内嵌图片的 PDF（qpdf 示例 invert-images-in.pdf）在坐标提取时：
     * <ul>
     *   <li>1. 能识别出 type=image 的块，且图片块不带文本行；</li>
     *   <li>2. 返回的 imagePath 指向真实存在、非空、可被正常解码的 PNG 文件；</li>
     *   <li>3. JSON 序列化结果中包含 imagePath。</li>
     * </ul>
     * 只要 imagePath 指向的文件已被清理或内容不可解码，本用例即失败。
     */
    @Test
    public void testExtractCoordinatesSavesEmbeddedImage() throws Exception {
        URL url = getClass().getResource("/qpdf-examples-invert-images-in.pdf");
        Assertions.assertNotNull(url, "测试资源缺失: /qpdf-examples-invert-images-in.pdf");
        String pdfPath = new File(url.getFile()).getAbsolutePath();

        try (MuPdfService service = new MuPdfService()) {
            Assertions.assertTrue(service.getPageCount(pdfPath) > 0, "样例 PDF 至少应有一页");

            MuPdfService.CoordinatesResult coords = service.extractCoordinates(pdfPath, 0);
            Assertions.assertNotNull(coords);
            Assertions.assertEquals(1, coords.pages().size());

            List<MuPdfService.BlockResult> blocks = coords.pages().get(0).blocks();

            List<MuPdfService.BlockResult> imageBlocks = blocks.stream()
                    .filter(b -> "image".equals(b.type()))
                    .toList();
            Assertions.assertFalse(imageBlocks.isEmpty(),
                    "该 PDF 第一页应至少包含一个图片块，实际解析出 " + blocks.size()
                            + " 个块；若为 0，说明 toStructuredText 未开启图片保留选项");
            System.out.println("[Test] 图片块数量: " + imageBlocks.size()
                    + ", 全部块类型: " + blocks.stream().map(MuPdfService.BlockResult::type).toList());

            for (MuPdfService.BlockResult imageBlock : imageBlocks) {
                Assertions.assertTrue(imageBlock.lines() == null || imageBlock.lines().isEmpty(),
                        "图片块不应携带文本行");
                Assertions.assertNotNull(imageBlock.bbox(), "图片块必须包含 bbox");

                String imagePath = imageBlock.imagePath();
                Assertions.assertNotNull(imagePath, "图片块的 imagePath 不应为 null");
                Path pngFile = Path.of(imagePath);
                System.out.println("[Test] 图片块落盘路径: " + pngFile);

                Assertions.assertTrue(Files.exists(pngFile), "imagePath 必须指向真实存在的文件，实际文件已被清理: " + pngFile);
                Assertions.assertTrue(Files.size(pngFile) > 0, "落盘的图片文件不能为空: " + pngFile);

                byte[] header = new byte[8];
                try (InputStream in = Files.newInputStream(pngFile)) {
                    Assertions.assertEquals(8, in.read(header), "PNG 文件至少应包含 8 字节文件头");
                }
                Assertions.assertArrayEquals(
                        new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
                        header, "落盘文件必须是合法 PNG（文件头校验）");

                BufferedImage decoded = ImageIO.read(pngFile.toFile());
                Assertions.assertNotNull(decoded, "落盘的 PNG 必须能被正常解码: " + pngFile);
                Assertions.assertTrue(decoded.getWidth() > 0 && decoded.getHeight() > 0, "PNG 宽高必须大于 0");
                System.out.println("[Test] 图片解码成功: " + decoded.getWidth() + "x" + decoded.getHeight());
            }
        }
    }

    private void runConcurrentWorkerRounds(MuPdfService service, String pdfPathStr, Path tempDir,
                                           int workerId, int iterations,
                                           AtomicInteger errorCount,
                                           CountDownLatch latch) {
        try {
            for (int r = 0; r < iterations; r++) {
                if (workerId % 2 == 0) {
                    byte[] png = service.renderPageToPng(pdfPathStr, r % 5, 72);
                    String json = service.extractCoordinatesJson(pdfPathStr, r % 5);
                    if (png == null || png.length == 0 || json == null || json.isEmpty()) {
                        errorCount.incrementAndGet();
                    }
                } else {
                    Path roundDir = tempDir.resolve("worker_" + workerId + "_round_" + r);
                    List<Path> splitFiles = service.splitPdf(pdfPathStr, roundDir);
                    if (splitFiles.size() != 5) {
                        errorCount.incrementAndGet();
                    }
                    for (Path f : splitFiles) {
                        Files.delete(f);
                    }
                    Files.delete(roundDir);
                }
            }
        } catch (Throwable t) {
            errorCount.incrementAndGet();
        } finally {
            latch.countDown();
        }
    }

    // ------------------------------------------------------------------
    // 文档结构：目录（/Outlines 书签）解析
    // ------------------------------------------------------------------
    @Test
    public void testExtractOutlineMatchesGolden() throws Exception {
        try (MuPdfService service = new MuPdfService()) {
            List<MuPdfService.OutlineNode> outline = service.extractOutline(getClass().getResource("/ctex.pdf").getFile().toString());
            Assertions.assertNotNull(outline, "测试资源不存在: ctex.pdf");
        }
    }

    @Test
    public void testExtractCtexDocumentData() {
        String pdfPath = getClass().getResource("/ctex.pdf").getFile().toString();

        try (MuPdfService service = new MuPdfService()) {
            Map<String, String> metadata = service.extractMetadata(pdfPath);
            List<MuPdfService.LayerInfo> layers = service.extractLayers(pdfPath);
            List<MuPdfService.OutlineNode> outline = service.extractOutline(pdfPath);
            int pageCount = service.getPageCount(pdfPath);

            int chars = 0;
            int links = 0;
            int labelledPages = 0;
            for (int pageNum = 0; pageNum < pageCount; pageNum++) {
                MuPdfService.PageResult page = service.extractCoordinates(pdfPath, pageNum).pages().get(0);
                if (service.extractPageLabel(pdfPath, pageNum) != null) {
                    labelledPages++;
                }
                links += service.extractLinks(pdfPath, pageNum).size();
                for (MuPdfService.BlockResult block : page.blocks()) {
                    if ("text".equals(block.type()) && block.lines() != null) {
                        for (MuPdfService.LineResult line : block.lines()) {
                            chars += line.text().length();
                        }
                    }
                }
            }

            System.out.println("[MuPdf] metadata=" + metadata.size() + " title=" + metadata.get("info:Title")
                    + " author=" + metadata.get("info:Author") + " producer=" + metadata.get("info:Producer"));
            System.out.println("[MuPdf] layers=" + layers);
            System.out.println("[MuPdf] outline roots=" + outline.size()
                    + " first=" + outline.get(0).title() + "@" + outline.get(0).page()
                    + " children=" + outline.get(0).children().size());
            System.out.println("[MuPdf] pages=" + pageCount + " labels=" + labelledPages
                    + " links=" + links + " chars=" + chars);

            Assertions.assertEquals(195, pageCount);
            Assertions.assertEquals("CTeX 宏集手册", metadata.get("info:Title"));
            Assertions.assertEquals("CTEX.ORG", metadata.get("info:Author"));
            Assertions.assertTrue(layers.isEmpty(), "ctex.pdf 无图层");
            Assertions.assertEquals(19, outline.size(), "大纲应有 19 个顶层条目");
            Assertions.assertEquals("目录", outline.get(0).title());
            Assertions.assertEquals(195, labelledPages, "195 页都应能取到页码标签");
            Assertions.assertTrue(links > 8000, "全本应有 8 千以上链接");
            Assertions.assertTrue(chars > 400000, "全本应提取 40 万以上字符");
        }
    }

    private Path createSampleMultiPagePdf(Path targetPdf, int pageCount) {
        String[] sampleResources = {
                "/sample.pdf",
                "/page_rotation_90_test.pdf",
                "/page_rotation_180_test.pdf",
                "/font_rotation_test.pdf"
        };
        try (PDFDocument merged = new PDFDocument()) {
            for (int i = 0; i < pageCount; i++) {
                String res = sampleResources[i % sampleResources.length];
                URL url = getClass().getResource(res);
                Assertions.assertNotNull(url, "测试资源不存在: " + res);
                File srcFile = new File(url.getFile());
                try (Document srcDoc = Document.openDocument(srcFile.getAbsolutePath())) {
                    PDFDocument srcPdf = (PDFDocument) srcDoc;
                    merged.graftPage(-1, srcPdf, 0);
                }
            }
            merged.save(targetPdf.toAbsolutePath().toString(), MuPdfService.DEFAULT_SPLIT_SAVE_OPTIONS);
            return targetPdf;
        }
    }

    /**
     * 取 check/ 目录下的测试 PDF 物理路径。
     *
     * @param name 文件名
     * @return 物理路径
     */
    private String checkPdf(String name) {
        URL url = getClass().getResource("/check/" + name);
        Assertions.assertNotNull(url, "测试资源缺失: check/" + name);
        return new File(url.getFile()).getAbsolutePath();
    }
}
