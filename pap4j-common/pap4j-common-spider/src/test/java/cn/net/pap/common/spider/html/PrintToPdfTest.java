package cn.net.pap.common.spider.html;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

/**
 * 使用 CDP (Chrome DevTools Protocol) 的 Page.printToPDF 命令将网页保存为 PDF。
 */
public class PrintToPdfTest {

    private static final Logger log = LoggerFactory.getLogger(PrintToPdfTest.class);

    /**
     * call chrome to gene pdf
     *
     * @throws Exception
     */
    @Test
    public void testPrintToPdf() throws Exception {
        String url = System.getProperty("pdf.url", "https://www.baidu.com");
        Path dest = Paths.get(System.getProperty("pdf.dest", Paths.get(System.getProperty("java.io.tmpdir"), "baidu.pdf").toString()));

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--no-sandbox");

        ChromeDriver driver = null;
        try {
            driver = new ChromeDriver(options);
        } catch (Exception e) {
            Assumptions.abort("跳过测试：未检测到本地 Chrome 浏览器环境 (" + e.getMessage() + ")");
        }
        try {
            driver.get(url);
        } catch (Exception e) {
            Assumptions.abort("跳过测试：无法访问目标 URL " + url + " (" + e.getMessage() + ")");
        }
        try {
            // 直接调用 CDP 命令：Page.printToPDF
            Map<String, Object> result = driver.executeCdpCommand("Page.printToPDF", Map.of("printBackground", true));
            byte[] pdfBytes = Base64.getDecoder().decode((String) result.get("data"));
            if (dest.getParent() != null && !Files.exists(dest.getParent())) {
                Files.createDirectories(dest.getParent());
            }
            Files.write(dest, pdfBytes);
            log.info("PDF 已保存至: {}", dest.toAbsolutePath());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

}

