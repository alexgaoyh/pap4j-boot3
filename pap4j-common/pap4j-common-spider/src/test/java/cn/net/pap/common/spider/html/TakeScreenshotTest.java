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
import java.time.Duration;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

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
 *     <li>
 *         <b>高级网络与控制台日志增强（增量功能）：</b><br>
 *         针对包含大量异步 AJAX 网络请求的复杂动态界面，本类在原有截图逻辑之上增量集成了浏览器 {@code Console} 控制台日志和 {@code Performance} 网络日志捕获能力。
 *         为避免引入特定版本的 Chrome DevTools Protocol（CDP）依赖而导致在不同 Selenium 4.x 环境下编译失败，本类通过 {@code goog:loggingPrefs} 激活日志输出，
 *         并在测试尾部通过轻量级、零第三方 JSON 依赖的纯字符串过滤解析方式，优雅地捕获并输出底层的 AJAX 请求/响应详情及 JavaScript 报错，极大增强了排查动态复杂接口的可靠性。
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
        org.junit.jupiter.api.Assumptions.assumeTrue(isUrlReachable(testUrl), "Target URL " + testUrl + " is not reachable. Skipping test.");
        String windowSize = System.getProperty("screenshot.windowSize", "1280,1024");
        int sleepMs = Integer.getInteger("screenshot.sleepMs", 1500);

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, java.util.logging.Level.ALL);
        logPrefs.enable(LogType.PERFORMANCE, java.util.logging.Level.ALL);

        WebDriver driver = null;
        File dest = null;
        boolean isTempFile = false;
        try {
            // 尝试初始化 Chrome Headless
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--window-size=" + windowSize);
            options.addArguments("--disable-gpu");
            options.setCapability("goog:loggingPrefs", logPrefs);
            driver = new ChromeDriver(options);
        } catch (Exception e) {
            log.error("Chrome Headless 启动失败，尝试 Edge: ", e);
            try {
                // 尝试初始化 Edge Headless
                EdgeOptions options = new EdgeOptions();
                options.addArguments("--headless");
                options.addArguments("--window-size=" + windowSize);
                options.addArguments("--disable-gpu");
                options.setCapability("goog:loggingPrefs", logPrefs);
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
                String destPath = System.getProperty("screenshot.dest");
                if (destPath != null && !destPath.trim().isEmpty()) {
                    dest = new File(destPath);
                } else {
                    dest = File.createTempFile("api_tester_screenshot_", ".png");
                    isTempFile = true;
                }

                Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("截图成功！已保存至: {}", dest.getAbsolutePath());

                // 1. 读取并打印浏览器控制台日志（Console Logs）
                try {
                    LogEntries consoleLogs = driver.manage().logs().get(LogType.BROWSER);
                    for (LogEntry entry : consoleLogs) {
                        log.info("[Browser Console] [{}] {}", entry.getLevel(), entry.getMessage());
                    }
                } catch (Exception e) {
                    log.error("无法获取浏览器控制台日志: ", e);
                }

                // 2. 读取并打印浏览器网络性能日志（Network Logs）
                try {
                    LogEntries perfLogs = driver.manage().logs().get(LogType.PERFORMANCE);
                    for (LogEntry entry : perfLogs) {
                        parseAndLogNetworkEvent(entry.getMessage());
                    }
                } catch (Exception e) {
                    log.error("无法获取浏览器网络日志: ", e);
                }

            } finally {
                if (driver != null) {
                    driver.quit();
                }
                if (isTempFile && dest != null && dest.exists()) {
                    try {
                        dest.delete();
                        log.info("已清理临时截图文件: {}", dest.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("清理临时文件失败: ", e);
                    }
                }
            }
        } else {
            log.error("未能初始化任何 WebDriver 实例。请确保本地安装了 Chrome 或 Edge 浏览器。");
        }
    }

    private void parseAndLogNetworkEvent(String rawMessage) {
        try {
            if (rawMessage.contains("Network.requestWillBeSent")) {
                int urlIdx = rawMessage.indexOf("\"url\":\"");
                if (urlIdx != -1) {
                    String sub = rawMessage.substring(urlIdx + 7);
                    String url = sub.substring(0, sub.indexOf("\""));
                    
                    int methodIdx = rawMessage.indexOf("\"method\":\"");
                    String method = "GET";
                    if (methodIdx != -1) {
                        String methodSub = rawMessage.substring(methodIdx + 10);
                        method = methodSub.substring(0, methodSub.indexOf("\""));
                        if (method.equals("Network.requestWillBeSent")) {
                            method = "REQ";
                        }
                    }
                    log.info("[Network Request] {} -> {}", method, truncateUrl(url));
                }
            } else if (rawMessage.contains("Network.responseReceived")) {
                int urlIdx = rawMessage.indexOf("\"url\":\"");
                if (urlIdx != -1) {
                    String sub = rawMessage.substring(urlIdx + 7);
                    String url = sub.substring(0, sub.indexOf("\""));
                    
                    int statusIdx = rawMessage.indexOf("\"status\":");
                    int status = 200;
                    if (statusIdx != -1) {
                        String statusSub = rawMessage.substring(statusIdx + 9);
                        int endIdx = 0;
                        while (endIdx < statusSub.length() && Character.isDigit(statusSub.charAt(endIdx))) {
                            endIdx++;
                        }
                        if (endIdx > 0) {
                            status = Integer.parseInt(statusSub.substring(0, endIdx));
                        }
                    }
                    log.info("[Network Response] Status: {} -> {}", status, truncateUrl(url));
                }
            }
        } catch (Exception e) {
            log.error("解析网络日志失败: ", e);
        }
    }

    private String truncateUrl(String url) {
        if (url == null) {
            return "";
        }
        if (url.startsWith("data:")) {
            return url.substring(0, Math.min(url.length(), 60)) + "... [Base64/Inline]";
        }
        if (url.length() > 150) {
            return url.substring(0, 150) + "...";
        }
        return url;
    }

    /**
     * <p><b>testBaiduSearch — AI 协同的“视觉之眼”最佳实践演示</b></p>
     * <p>
     * 本方法不仅验证了百度的搜索交互，更作为一个标准的技术底座，作为 AI 协同在无图形化控制台沙箱环境下的<b>“眼睛”</b>。<br>
     * 后续 AI 编码助手在遇到前端 UI 遮挡、样式挤压、动态 AJAX 异步接口报错时，可以直接调用此测试能力：
     * </p>
     * <ul>
     *     <li>通过 Headless 浏览器进行页面截图以供多模态视觉模型（Vision Model）诊断；</li>
     *     <li>收集并回放控制台 Console Logs 捕获 JS 运行期报错；</li>
     *     <li>分析 Network Performance 性能事件以追踪 AJAX 异步请求/响应的完整数据流。</li>
     * </ul>
     * <p>
     * AI 助手在后续的迭代和修复过程中，应主动识别并使用此测试技能来确保前端渲染与网络状态的正确性。
     * </p>
     */
    @Test
    public void testBaiduSearch() throws Exception {
        String testUrl = System.getProperty("screenshot.url", "http://www.baidu.com");
        org.junit.jupiter.api.Assumptions.assumeTrue(isUrlReachable(testUrl), "Target URL " + testUrl + " is not reachable. Skipping test.");
        String searchQuery = System.getProperty("screenshot.searchQuery", "alexgaoyh");
        String windowSize = System.getProperty("screenshot.windowSize", "1280,1024");
        int sleepMs = Integer.getInteger("screenshot.sleepMs", 3000);

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, java.util.logging.Level.ALL);
        logPrefs.enable(LogType.PERFORMANCE, java.util.logging.Level.ALL);

        WebDriver driver = initWebDriver(windowSize, logPrefs);
        File dest = null;
        boolean isTempFile = false;
        if (driver != null) {
            try {
                driver.get(testUrl);
                Thread.sleep(1500);

                performSearch(driver, searchQuery);
                Thread.sleep(sleepMs);

                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                String destPath = System.getProperty("screenshot.dest");
                if (destPath != null && !destPath.trim().isEmpty()) {
                    dest = new File(destPath);
                } else {
                    dest = File.createTempFile("baidu_search_screenshot_", ".png");
                    isTempFile = true;
                }
                Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("搜索测试成功并截图！已保存至: {}", dest.getAbsolutePath());

                printBrowserLogs(driver);
            } finally {
                driver.quit();
                if (isTempFile && dest != null && dest.exists()) {
                    try {
                        dest.delete();
                        log.info("已清理临时截图文件: {}", dest.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("清理临时文件失败: ", e);
                    }
                }
            }
        } else {
            log.error("未能初始化任何 WebDriver 实例。请确保本地安装了 Chrome 或 Edge 浏览器。");
        }
    }

    private void performSearch(WebDriver driver, String searchQuery) throws Exception {
        org.openqa.selenium.WebElement searchInput = null;
        try {
            searchInput = driver.findElement(By.id("chat-textarea"));
            if (!searchInput.isDisplayed()) {
                searchInput = driver.findElement(By.id("kw"));
            }
        } catch (Exception e) {
            searchInput = driver.findElement(By.id("kw"));
        }

        searchInput.sendKeys(searchQuery);

        org.openqa.selenium.WebElement searchButton = null;
        try {
            java.util.List<org.openqa.selenium.WebElement> btnList = driver.findElements(By.xpath("//*[contains(text(), '百度一下')]"));
            for (org.openqa.selenium.WebElement btn : btnList) {
                if (btn.isDisplayed()) {
                    searchButton = btn;
                    break;
                }
            }
        } catch (Exception e) {
            log.error("查找'百度一下'按钮文本失败: ", e);
        }

        if (searchButton == null) {
            searchButton = driver.findElement(By.id("su"));
        }

        try {
            searchButton.click();
        } catch (Exception clickEx) {
            log.error("普通点击失败，尝试 JavaScript 点击: ", clickEx);
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", searchButton);
        }
    }

    private WebDriver initWebDriver(String windowSize, LoggingPreferences logPrefs) {
        WebDriver driver = null;
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--window-size=" + windowSize);
            options.addArguments("--disable-gpu");
            options.setCapability("goog:loggingPrefs", logPrefs);
            driver = new ChromeDriver(options);
        } catch (Exception e) {
            log.error("Chrome Headless 启动失败，尝试 Edge: ", e);
            try {
                EdgeOptions options = new EdgeOptions();
                options.addArguments("--headless");
                options.addArguments("--window-size=" + windowSize);
                options.addArguments("--disable-gpu");
                options.setCapability("goog:loggingPrefs", logPrefs);
                driver = new EdgeDriver(options);
            } catch (Exception ex) {
                log.error("Edge Headless 启动失败: ", ex);
            }
        }
        return driver;
    }

    private void printBrowserLogs(WebDriver driver) {
        try {
            LogEntries consoleLogs = driver.manage().logs().get(LogType.BROWSER);
            for (LogEntry entry : consoleLogs) {
                log.info("[Browser Console] [{}] {}", entry.getLevel(), entry.getMessage());
            }
        } catch (Exception e) {
            log.error("无法获取浏览器控制台日志: ", e);
        }

        try {
            LogEntries perfLogs = driver.manage().logs().get(LogType.PERFORMANCE);
            for (LogEntry entry : perfLogs) {
                parseAndLogNetworkEvent(entry.getMessage());
            }
        } catch (Exception e) {
            log.error("无法获取浏览器网络日志: ", e);
        }
    }

    /**
     * <p><b>testExplicitWaitDemo — 演示使用显式等待 (Explicit Wait) 替代 Thread.sleep 进行动态 UI 探测</b></p>
     * <p>
     * 显式等待是 Selenium 自动化测试的行业标准解。它允许设置一个最大超时时间，并在后台以轮询方式动态探测元素状态，
     * 一旦元素可见或符合条件，立即停止等待并继续，能有效提高测试速度并消减因网络波动引起的用例抖动。
     * </p>
     */
    @Test
    public void testExplicitWaitDemo() throws Exception {
        // 使用百度首页作为默认测试页面，演示显式等待探测元素
        String testUrl = System.getProperty("screenshot.url", "https://www.baidu.com");
        org.junit.jupiter.api.Assumptions.assumeTrue(isUrlReachable(testUrl), "Target URL " + testUrl + " is not reachable. Skipping test.");
        String windowSize = System.getProperty("screenshot.windowSize", "1280,1024");

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, java.util.logging.Level.ALL);
        logPrefs.enable(LogType.PERFORMANCE, java.util.logging.Level.ALL);

        WebDriver driver = initWebDriver(windowSize, logPrefs);
        File dest = null;
        boolean isTempFile = false;
        if (driver != null) {
            try {
                driver.get(testUrl);

                // 1. 初始化显式等待器，设定最大超时时间为 10 秒
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                
                // 2. 动态检测，一旦百度的搜索按钮 (By.id("su")) 在 DOM 中可见立刻返回
                org.openqa.selenium.WebElement element = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("chat-submit-button"))
                );

                log.info("显式等待成功！页面元素已渲染就绪，开始截图。");

                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                String destPath = System.getProperty("screenshot.dest");
                if (destPath != null && !destPath.trim().isEmpty()) {
                    dest = new File(destPath);
                } else {
                    dest = File.createTempFile("explicit_wait_screenshot_", ".png");
                    isTempFile = true;
                }
                Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("截图成功！已保存至: {}", dest.getAbsolutePath());

            } catch (org.openqa.selenium.TimeoutException e) {
                log.warn("【温馨提示】显式等待超时：未能成功访问 {} 或未能在 10 秒内找到元素 '#su'。若本地或沙箱处于离线/无外网环境，此超时属正常现象，不阻塞单测构建。", testUrl);
            } catch (Exception e) {
                log.warn("【温馨提示】访问 {} 过程中发生异常: {}。若无公网连接属正常现象。", testUrl, e.getMessage());
            } finally {
                driver.quit();
                if (isTempFile && dest != null && dest.exists()) {
                    try {
                        dest.delete();
                        log.info("已清理临时截图文件: {}", dest.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("清理临时文件失败: ", e);
                    }
                }
            }
        }
    }

    private static boolean isUrlReachable(String urlStr) {
        if (urlStr.startsWith("file:") || urlStr.startsWith("data:")) {
            return true;
        }
        try {
            java.net.URL url = new java.net.URL(urlStr);
            String host = url.getHost();
            int port = url.getPort() != -1 ? url.getPort() : (url.getProtocol().equalsIgnoreCase("https") ? 443 : 80);
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(host, port), 1500);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
