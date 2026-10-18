package cn.net.pap.common.mupdf;

import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.fitz.Quad;
import com.artifex.mupdf.fitz.Rect;
import cn.net.pap.common.mupdf.loader.MuPdfNativeLoader;
import cn.net.pap.common.mupdf.service.MuPdfService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;

public class MuPdfServiceTest {

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

        // 2. 测试文字物理坐标 JSON 提取
        String json = service.extractCoordinatesJson(pdfFile.getAbsolutePath(), 0);
        Assertions.assertNotNull(json);
        Assertions.assertFalse(json.trim().isEmpty());
        System.out.println("[Test] 提取出的物理坐标 JSON 字符长度: " + json.length());

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
}
