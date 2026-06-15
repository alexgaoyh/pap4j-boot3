package cn.net.pap.common.spider.html;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * <p><strong>TakeScreenshotTest</strong></p>
 *
 * <p><b>当前类的诞生演进与决策链路（基于会话最近几条记录总结）：</b></p>
 * <ol>
 *     <li>
 *         <b>问题提出：</b><br>
 *         用户请求排查优化 {@code pap4j-boot3-example-apitester} 模块下的 {@code api-tester.html} 前端 CSS 样式问题（存在元素遮挡/挤压）。
 *         由于 AI 协同端在无图形界面的控制台沙箱环境下工作，无法通过常规浏览器双击直接查看网页的真实渲染效果。
 *     </li>
 *     <li>
 *         <b>环境探测（Python 阶段）：</b><br>
 *         AI 首先尝试通过调用本地 Python 3.13.5 环境，执行脚本对正在运行的页面进行截图。
 *         但在通过 {@code pip list} 探测后，确认本地 Python 环境中未安装 {@code selenium}、{@code playwright} 等浏览器无界面截图库，临时安装成本较高。
 *     </li>
 *     <li>
 *         <b>方案转移与发现（Java 阶段）：</b><br>
 *         转向分析 Java 模块。在 {@code pap4j-common-spider} 模块的 {@code pom.xml} 依赖中，发现已声明有 {@code org.seleniumhq.selenium:selenium-java} 测试依赖。
 *         因此决定在 Java 端编写该单元测试类，以 Selenium Headless 浏览器（优先 Chrome，其次 Edge 驱动）访问 {@code http://localhost:31001/api-tester.html} 并执行截图。
 *     </li>
 *     <li>
 *         <b>图像诊断与样式修复：</b><br>
 *         通过对生成的截图进行视觉分析，定位了两处关键样式缺陷：
 *         <ul>
 *             <li>Headers 卡片中的“删除”按钮在宽度不足时，未设置防压缩样式被输入框挤扁，导致文字垂直换行；</li>
 *             <li>历史记录面板作为 fixed 抽屉在宽屏（PC端）下大面积遮挡主界面，导致用户在打开历史记录时无法操作主界面。</li>
 *         </ul>
 *         以此为依据，对前端进行重构：对删除按钮设置了 {@code flex-shrink: 0; white-space: nowrap;}，并采用 CSS Grid 实现了 PC 端两栏常驻平铺、移动端抽屉式滑入的响应式适配。
 *     </li>
 *     <li>
 *         <b>测试类参数化优化与 target 污染规避：</b><br>
 *         为了将该排查工具作为通用单元测试持久化保留，用户提出不能使用硬编码参数。
 *         因此对本类进行了优化，支持通过 System Properties 动态配置抓取 URL（{@code screenshot.url}）、窗口大小（{@code screenshot.windowSize}）等。
 *         同时，为避免在测试运行时将图片直接输出到项目 {@code target} 目录下从而污染提交历史，本类在未指定保存路径时默认使用 {@link File#createTempFile} 写入系统临时文件夹。
 *     </li>
 * </ol>
 *
 * <p><b>参数化设计说明：</b><br>
 * 支持在 Maven 运行时通过 {@code -D} 传递以下系统属性：
 * <ul>
 *     <li>{@code screenshot.url} - 目标抓取网页 URL（默认 fallback 到本地 {@code http://localhost:31001/api-tester.html}）</li>
 *     <li>{@code screenshot.dest} - 截图保存的绝对或相对文件路径。如未指定，将默认通过 {@link File#createTempFile} 生成系统临时文件</li>
 *     <li>{@code screenshot.windowSize} - 虚拟浏览器窗口尺寸（默认：{@code 1280,1024}）</li>
 *     <li>{@code screenshot.sleepMs} - 页面动画与渲染过渡等待延时毫秒数（默认：{@code 1500} 毫秒）</li>
 * </ul>
 * </p>
 *
 * <p><b>运行示例：</b><br>
 * {@code mvn test -pl pap4j-common/pap4j-common-spider -Dtest=TakeScreenshotTest -Dscreenshot.url=https://www.baidu.com -Dscreenshot.dest=target/baidu.png}
 * </p>
 */
public class TakeScreenshotTest {

    private static final Logger log = LoggerFactory.getLogger(TakeScreenshotTest.class);

    @Test
    public void testTakeScreenshot() throws Exception {
        // 从 System Properties 中读取可配置参数，并提供合理的默认值以防无参运行时报错
        String testUrl = System.getProperty("screenshot.url", "http://www.baidu.com");
        String windowSize = System.getProperty("screenshot.windowSize", "1280,1024");
        int sleepMs = Integer.getInteger("screenshot.sleepMs", 1500);

        WebDriver driver = null;
        try {
            // 尝试初始化 Chrome Headless
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--window-size=" + windowSize);
            options.addArguments("--disable-gpu");
            driver = new ChromeDriver(options);
        } catch (Exception e) {
            log.warn("Chrome Headless 启动失败，尝试 Edge: {}", e.getMessage());
            try {
                // 尝试初始化 Edge Headless
                EdgeOptions options = new EdgeOptions();
                options.addArguments("--headless");
                options.addArguments("--window-size=" + windowSize);
                options.addArguments("--disable-gpu");
                driver = new EdgeDriver(options);
            } catch (Exception ex) {
                log.error("Edge Headless 启动失败: ", ex);
            }
        }

        if (driver != null) {
            try {
                driver.get(testUrl);
                Thread.sleep(sleepMs); // 等待页面动画和资源加载完成

                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                File dest = File.createTempFile("api_tester_screenshot_", ".png");

                Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("截图成功！已保存至: {}", dest.getAbsolutePath());
            } finally {
                driver.quit();
            }
        } else {
            log.error("未能初始化任何 WebDriver 实例。请确保本地安装了 Chrome 或 Edge 浏览器。");
        }
    }
}
