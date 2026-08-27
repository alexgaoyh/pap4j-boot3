package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.autoclose.AutoCloseableFTPClient;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * FTPClient 完整使用示例与全技术点演示测试。
 *
 * <p>本类以可运行的 JUnit 5 测试形式，系统演示 Apache Commons Net {@code FTPClient}
 * 在生产中必须注意的全部技术点，与 {@link cn.net.pap.example.proguard.controller.FtpController}
 * 描述的注意事项一一对应；并与 {@link FtpDirectoryCopyTest}（目录拷贝/单连接读写禁令）、
 * {@link FtpActivePortRangeTest}（主动模式+固定端口区间）互补，本类只使用被动模式。</p>
 *
 * <h2>技术点清单（与 FtpController 注意事项对应）</h2>
 * <ol>
 *   <li><b>连接生命周期</b>：connect → login → enterLocalPassiveMode → setFileType(BINARY) →
 *       logout → disconnect；推荐 {@link AutoCloseableFTPClient} + try-with-resources 统一收尾。</li>
 *   <li><b>被动模式必须在 connect 之后调用</b>：connect() 会把数据连接模式重置回主动模式，
 *       见 {@link #testPassiveModeMustBeSetAfterConnect()}。</li>
 *   <li><b>控制通道编码</b>：{@code setControlEncoding("UTF-8")} 必须在 connect 之前设置，
 *       以支持中文文件名（服务端 ftpserver.encoding=UTF-8），见
 *       {@link #testChineseFilenameWithUtf8Encoding()}。</li>
 *   <li><b>原子方法</b>：{@code storeFile / retrieveFile / appendFile} 内部完整 copy 并等 226，
 *       返回 true 即已落盘，见 {@link #testAtomicMethodsStoreRetrieveRoundTrip()} 与
 *       {@link #testAppendFileAppendsContent()}。</li>
 *   <li><b>流式方法必须完整收尾</b>：{@code storeFileStream / retrieveFileStream} 只给裸流，
 *       必须 ① 关流 ② {@code completePendingCommand()} 收 226，否则控制通道错位/文件残留，见
 *       {@link #testStreamUploadRequiresCompletePendingCommand()} 与
 *       {@link #testStreamDownloadRequiresCompletePendingCommand()}。</li>
 *   <li><b>同一连接不可同时读写</b>：retrieveFileStream 挂起时 storeFileStream 返回 null，见
 *       {@link #testSameConnectionCannotSimultaneousReadWrite()}。</li>
 *   <li><b>中断必须发 ABOR</b>：部分读取后提前退出应发 abort() 让服务端立即释放文件，
 *       不能只断开连接，见 {@link #testAbortOnInterruptedDownloadReleasesFile()}。</li>
 *   <li><b>失败按应答码分流</b>：550/551/553=不存在或权限不足（永久）、450/451/421=忙（可重试）、
 *       425/426=传输中断（可重试）、530=未登录，见
 *       {@link #testReplyCodeClassificationOnMissingFile()}。</li>
 *   <li><b>下载成功≠内容完整</b>：retrieveFile 返回 true 也须校验实际字节数，见
 *       {@link #testAtomicMethodsStoreRetrieveRoundTrip()}。</li>
 *   <li><b>重试只对可恢复失败生效</b>：450/451/421/425/426 可重试，550/553/530 永久失败不重试，
 *       且任务抛异常应继续计数重试，见 {@link #testRetryableReplyCodeClassification()}、
 *       {@link #testRetryPolicyPermanentFailureStopsImmediately()} 与
 *       {@link #testRetryPolicyRetriesTransientIOException()}。</li>
 *   <li><b>断点续传</b>：{@code setRestartOffset(offset)} + retrieveFileStream 只取偏移之后的内容，见
 *       {@link #testRestartOffsetResumeDownload()}。</li>
 *   <li><b>目录与文件操作</b>：makeDirectory / rename / deleteFile / removeDirectory / listFiles，见
 *       {@link #testDirectoryRenameDeleteOps()}。</li>
 * </ol>
 *
 * <p><b>运行前提</b>：本类是集成测试，需配套启动 {@code pap4j-boot3-example-ftp-server} 模块，
 * FTP 服务在 {@code 127.0.0.1:21} 可用（用户 bj/123456，home 目录 d:/knowledge，与
 * {@code application.properties} 一致）。服务不可用时，测试通过 JUnit 5 {@code Assumptions}
 * 跳过（显示 SKIPPED）而非假通过。</p>
 *
 * <p><b>连接管理约定</b>：本类统一使用 {@link AutoCloseableFTPClient}（项目唯一允许的 FTP 客户端），
 * 测试内 {@code client} 字段的生命周期由 {@code @BeforeEach}/{@code @AfterEach} 管理，
 * 等价于 try-with-resources；新建的临时客户端一律 try-with-resources 包裹。</p>
 *
 * @see cn.net.pap.example.proguard.controller.FtpController
 * @see FtpDirectoryCopyTest
 * @see FtpActivePortRangeTest
 */
public class FtpClientUsageExampleTest {

    private static final Logger log = LoggerFactory.getLogger(FtpClientUsageExampleTest.class);

    private static final String FTP_HOST = "127.0.0.1";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "bj";
    private static final String FTP_PASS = "123456";

    /** 本测试专用的远程测试目录（挂在 FTP home 根下）。 */
    private static final String TEST_DIR = "/ftp_usage_example";

    /** 与 FtpController.isFtpReplyRetryable 一致的可重试应答集合。 */
    private static final int[] RETRYABLE_REPLY_CODES = {450, 451, 421, 425, 426};

    /** 每个测试共享的连接，由 @BeforeEach/@AfterEach 管理生命周期。 */
    private AutoCloseableFTPClient client;

    /**
     * 每个测试开始前：确保 FTP 服务可用（不可用则跳过整类），并清理/重建测试目录。
     *
     * @throws IOException FTP 命令失败
     */
    @BeforeEach
    void setUp() throws IOException {
        client = openClient();
        Assumptions.assumeTrue(client != null, "FTP 服务不可用(127.0.0.1:21)，跳过 FTP 完整使用示例测试");
        deleteRecursive(client, TEST_DIR);
        makeDirectoryRecursive(client, TEST_DIR);
    }

    /**
     * 每个测试结束后：清理测试目录并断开共享连接。
     * 清理失败只记日志不抛错，下一轮 setUp 会再次重建目录。
     */
    @AfterEach
    void tearDown() {
        if (client != null) {
            try {
                deleteRecursive(client, TEST_DIR);
            } catch (IOException e) {
                log.warn("[FtpUsage] tearDown 清理测试目录失败: {}", e.getMessage());
            }
            client.close();
        }
    }

    /**
     * 技术点：被动模式必须在 connect() 之后调用。
     *
     * <p>commons-net 的 FTPClient 默认是主动模式（ACTIVE_LOCAL_DATA_CONNECTION_MODE）。
     * 若在 connect() 之前调用 enterLocalPassiveMode()，connect() 内部的 _connectAction_()
     * 会强制将其重置回主动模式。因此 enterLocalPassiveMode() 必须放在 connect() 之后调用，
     * 否则数据连接会静默以主动模式发起，在 NAT/容器/防火墙环境下必然握手超时失败。</p>
     *
     * @throws IOException connect 失败
     */
    @Test
    void testPassiveModeMustBeSetAfterConnect() throws IOException {
        AutoCloseableFTPClient raw = new AutoCloseableFTPClient();
        try {
            assertEquals(FTPClient.ACTIVE_LOCAL_DATA_CONNECTION_MODE, raw.getDataConnectionMode(),
                    "新建客户端初始默认应是主动模式");

            // 试验：在 connect 前显式设置被动模式
            raw.enterLocalPassiveMode();
            assertEquals(FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE, raw.getDataConnectionMode(),
                    "connect 前设置被动模式已生效");

            // 执行 connect 动作（加防护避免服务未启动时抛异常）
            try {
                raw.connect(FTP_HOST, FTP_PORT);
            } catch (IOException e) {
                Assumptions.assumeTrue(false, "FTP 服务不可用(127.0.0.1:21)，跳过连接断言: " + e.getMessage());
            }

            // 证伪断言：connect() 执行后，模式会被内部 _connectAction_() 强制重置回主动模式！
            assertEquals(FTPClient.ACTIVE_LOCAL_DATA_CONNECTION_MODE, raw.getDataConnectionMode(),
                    "connect() 会强制重置数据连接模式回主动模式！证明 connect 前设置被动模式会被冲掉");

            // 正确做法：必须在 connect() 之后再次调用 enterLocalPassiveMode()
            raw.enterLocalPassiveMode();
            assertEquals(FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE, raw.getDataConnectionMode(),
                    "connect() 之后调用 enterLocalPassiveMode() 才能保持被动模式生效");
        } finally {
            raw.close();
        }
    }

    /**
     * 技术点：默认 ASCII 模式陷阱与 setFileType(FTP.BINARY_FILE_TYPE) 必须性。
     *
     * <p>commons-net FTPClient 默认（含 connect() 之后）文件类型为 {@link FTP#ASCII_FILE_TYPE}。
     * ASCII 模式下客户端数据通道会做 NVT-ASCII 转义：上传时 {@code ToNetASCIIOutputStream}
     * 把 0x0A 转成 0x0D 0x0A、下载时 {@code FromNetASCIIInputStream} 把 0x0D 0x0A 还原成 0x0A，
     * 因此图片/视频/PDF 等二进制内容会被破坏。生产必须在 connect() 之后显式调用
     * {@code setFileType(FTP.BINARY_FILE_TYPE)}。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testFileTypeDefaultAsciiVsBinaryMode() throws IOException {
        String binFile = TEST_DIR + "/binary-mode-check.bin";
        // 包含各类控制字符与换行字节（0x0D/0x0A/0x00/0xFF）的二进制数据
        byte[] binaryPayload = new byte[] {0x00, 0x0D, 0x0A, 0x1A, (byte) 0xFF, 0x0A, 0x0D, 0x42};

        // 1) BINARY 模式：含 \r\n 等控制字节的二进制数据必须逐字节往返一致
        uploadBytes(client, binFile, binaryPayload);
        assertArrayEquals(binaryPayload, downloadBytes(client, binFile),
                "BINARY_FILE_TYPE 下二进制数据必须逐字节一致");

        // 2) 切回 ASCII 模式（FTPClient 默认文件类型）再下载：
        //    commons-net 客户端侧 FromNetASCIIInputStream 会把数据通道的 0x0D 0x0A 转义成 0x0A，
        //    二进制内容因此被破坏——实证 ASCII 模式会损坏二进制文件，生产必须显式 setFileType(BINARY)
        assertTrue(client.setFileType(FTP.ASCII_FILE_TYPE), "切回 ASCII 文件类型应成功");
        ByteArrayOutputStream asciiOut = new ByteArrayOutputStream();
        assertTrue(client.retrieveFile(binFile, asciiOut), "ASCII 模式下载应成功");
        byte[] asciiDownloaded = asciiOut.toByteArray();
        log.info("[FtpUsage] BINARY 往返长度={}, ASCII 下载长度={}", binaryPayload.length, asciiDownloaded.length);
        assertFalse(Arrays.equals(binaryPayload, asciiDownloaded),
                "ASCII 模式下载会把 0x0D 0x0A 转义为 0x0A，破坏二进制内容（证明必须显式 BINARY）");

        // 3) 恢复 BINARY，避免影响 tearDown 及后续断言
        assertTrue(client.setFileType(FTP.BINARY_FILE_TYPE), "恢复 BINARY 文件类型应成功");
    }

    /**
     * 技术点：超时配置三件套（Connect / Default / Data Timeout）防止连接与传输假死。
     *
     * <p>生产高可用规范：
     * 1. {@code setDefaultTimeout(int)}：底层 Socket 连接建立与等待超时；
     * 2. {@code setConnectTimeout(int)}：connect() 握手超时；
     * 3. {@code setDataTimeout(Duration)}：数据传输读写超时，防止对端假死导致线程永久阻塞。</p>
     */
    @Test
    void testTimeoutConfigurationsBestPractice() throws IOException {
        AutoCloseableFTPClient configured = new AutoCloseableFTPClient();
        try {
            configured.setDefaultTimeout(5000);
            configured.setConnectTimeout(5000);
            configured.setDataTimeout(Duration.ofSeconds(10));
            configured.setControlEncoding("UTF-8");

            try {
                configured.connect(FTP_HOST, FTP_PORT);
            } catch (IOException e) {
                Assumptions.assumeTrue(false, "FTP 服务不可用(127.0.0.1:21)，跳过测试: " + e.getMessage());
            }

            assertTrue(configured.login(FTP_USER, FTP_PASS), "登录应成功");
            configured.enterLocalPassiveMode();
            configured.setFileType(FTP.BINARY_FILE_TYPE);

            assertTrue(configured.isConnected(), "配置防御性超时后客户端应正常连通");
        } finally {
            configured.close();
        }
    }

    /**
     * 技术点：完整的连接生命周期。
     *
     * <p>connect → login → enterLocalPassiveMode → setFileType 已在 {@link #openClient()} 完成，
     * 此处校验连接状态、被动模式与列目录能力。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testConnectionLifecycleAndPassiveMode() throws IOException {
        assertTrue(client.isConnected(), "连接应处于建立状态");
        assertTrue(client.isAvailable(), "控制通道应可用");
        assertEquals(FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE, client.getDataConnectionMode(),
                "应处于本地被动模式");
        FTPFile[] files = client.listFiles("/");
        assertNotNull(files, "列根目录应返回非空数组");
        log.info("[FtpUsage] 根目录条目数: {}", files.length);
    }

    /**
     * 技术点：推荐使用 AutoCloseableFTPClient + try-with-resources。
     *
     * <p>close() 统一完成 logout + disconnect，避免散落 new FTPClient() 造成连接泄漏；
     * try-with-resources 退出后连接必须已断开。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testAutoCloseableFtpClientClosesConnection() throws IOException {
        AutoCloseableFTPClient leakedRef = null;
        try (AutoCloseableFTPClient c = openClient()) {
            Assumptions.assumeTrue(c != null, "FTP 服务不可用，跳过测试");
            assertTrue(c.isConnected(), "try 块内连接应可用");
            assertNotNull(c.listFiles("/"), "try 块内应能正常执行 FTP 命令");
            leakedRef = c;
        }
        assertNotNull(leakedRef, "引用应在块外可见以便验证关闭效果");
        assertFalse(leakedRef.isConnected(), "try-with-resources 退出后 close() 应已断开连接");
    }

    /**
     * 技术点：原子方法 storeFile/retrieveFile 与「下载成功≠内容完整」。
     *
     * <p>原子方法内部完整 copy 并等 226，返回 true 即已落盘；但 retrieveFile 返回 true
     * 不代表内容一定正确（可能是 0 字节占位/半截文件），必须校验实际字节数与大小。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testAtomicMethodsStoreRetrieveRoundTrip() throws IOException {
        String filePath = TEST_DIR + "/atomic.bin";
        byte[] payload = new byte[64 * 1024];
        new Random(42).nextBytes(payload);

        uploadBytes(client, filePath, payload);
        byte[] downloaded = downloadBytes(client, filePath);
        assertArrayEquals(payload, downloaded,
                "原子方法往返后内容必须逐字节一致（下载成功≠内容完整，必须校验实际字节）");

        assertEquals(payload.length, getRemoteFileSize(client, filePath),
                "SIZE/listFiles 返回的文件大小应与上传字节数一致");
    }

    /**
     * 技术点：元数据解析多策略对比（mlistFile / SIZE / listFiles 兜底）。
     *
     * <p>对应 FtpController 中获取视频/文件大小的双方案：
     * 1. {@code mlistFile(path)}（RFC 3659 MLST 标准指令）：返回格式化的结构化 {@link FTPFile} 元数据；
     * 2. {@code sendCommand("SIZE", path)}：优先从控制通道获取纯数字字节数；
     * 3. {@code listFiles(path)}：经典 LIST 命令兜底解析。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testMlistFileAndSizeMetadataResolution() throws IOException {
        String filePath = TEST_DIR + "/meta-test.bin";
        byte[] payload = new byte[32 * 1024];
        new Random(99).nextBytes(payload);
        uploadBytes(client, filePath, payload);

        // 策略 1: mlistFile 获取精确元数据
        FTPFile mlistResult = client.mlistFile(filePath);
        if (mlistResult != null) {
            assertEquals(payload.length, mlistResult.getSize(), "mlistFile 获取的文件大小应准确");
            assertTrue(mlistResult.isFile(), "mlistFile 应正确识别为普通文件");
            log.info("[FtpUsage] mlistFile 成功解析: name={}, size={}, rawListing={}",
                    mlistResult.getName(), mlistResult.getSize(), mlistResult.getRawListing());
        } else {
            log.info("[FtpUsage] 服务端未启用 MLST 特性(返回 null)，将依赖 SIZE / listFiles 策略");
        }

        // 策略 2: SIZE 命令与 listFiles 兜底
        long resolvedSize = getRemoteFileSize(client, filePath);
        assertEquals(payload.length, resolvedSize, "getRemoteFileSize(SIZE优先/listFiles兜底) 大小应一致");
    }

    /**
     * 技术点：流式上传 storeFileStream 必须完整收尾。
     *
     * <p>storeFileStream 只给裸流，写完后必须 ① 关流 ② completePendingCommand() 收 226；
     * 漏掉收尾是服务器上出现 0 字节/半截文件的常见来源。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testStreamUploadRequiresCompletePendingCommand() throws IOException {
        String filePath = TEST_DIR + "/stream-upload.bin";
        byte[] payload = "stream upload content".getBytes(StandardCharsets.UTF_8);

        try (OutputStream out = client.storeFileStream(filePath)) {
            assertNotNull(out, "storeFileStream 应成功打开数据流: " + ftpReplySummary(client));
            out.write(payload);
        }
        boolean complete = client.completePendingCommand();
        assertTrue(complete, "storeFileStream 后必须 completePendingCommand 收 226: " + ftpReplySummary(client));

        assertArrayEquals(payload, downloadBytes(client, filePath), "流式上传内容应完整落盘");
    }

    /**
     * 技术点：流式下载 retrieveFileStream 必须完整收尾。
     *
     * <p>retrieveFileStream 打开数据通道后，必须 ① 关闭流 ② completePendingCommand() 收 226；
     * 缺任一步连接就停在「传输未完成」，轻则不可复用，重则控制通道错位。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testStreamDownloadRequiresCompletePendingCommand() throws IOException {
        String filePath = TEST_DIR + "/stream-download.bin";
        byte[] payload = "stream download content".getBytes(StandardCharsets.UTF_8);
        uploadBytes(client, filePath, payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = client.retrieveFileStream(filePath)) {
            assertNotNull(in, "retrieveFileStream 应成功打开数据流: " + ftpReplySummary(client));
            IOUtils.copy(in, out);
        }
        boolean complete = client.completePendingCommand();
        assertTrue(complete, "retrieveFileStream 后必须 completePendingCommand 收 226: " + ftpReplySummary(client));
        assertArrayEquals(payload, out.toByteArray(), "流式下载内容应完整");
    }

    /**
     * 技术点：同一连接不可同时读写。
     *
     * <p>FTP 控制通道同一时刻只能服务一条数据连接：retrieveFileStream 挂起时，
     * 同一连接上 storeFileStream 会返回 null（而非报错）。必须先把读取流关闭并
     * completePendingCommand 收尾后，才能再开写流。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testSameConnectionCannotSimultaneousReadWrite() throws IOException {
        String srcFile = TEST_DIR + "/simultaneous-src.txt";
        String dstFile = TEST_DIR + "/simultaneous-dst.txt";
        uploadBytes(client, srcFile, "same connection".getBytes(StandardCharsets.UTF_8));

        InputStream in = client.retrieveFileStream(srcFile);
        assertNotNull(in, "retrieveFileStream 应成功");
        try {
            OutputStream out = client.storeFileStream(dstFile);
            assertNull(out, "同一连接 retrieveFileStream 挂起时，storeFileStream 必须返回 null");
        } finally {
            in.close();
            client.completePendingCommand();
        }

        try (OutputStream out = client.storeFileStream(dstFile)) {
            assertNotNull(out, "读取流关闭且 completePendingCommand 后，同一连接可再次打开写入流");
            out.write("after-complete".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(client.completePendingCommand(), "顺序写也应 completePendingCommand 收尾");
    }

    /**
     * 技术点：中断必须发 ABOR，让服务端释放文件。
     *
     * <p>客户端断开、读取异常、提前返回时，都要发 ABOR（不能只断开连接），否则服务端一直攥着
     * 文件锁，别的连接重试同一文件会确定性 450/550 busy。ABOR 后无论同一连接控制通道状态如何，
     * 重新连接后该文件必须能再次完整下载。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testAbortOnInterruptedDownloadReleasesFile() throws IOException {
        String filePath = TEST_DIR + "/abort-source.bin";
        byte[] payload = new byte[256 * 1024];
        new Random(7).nextBytes(payload);
        uploadBytes(client, filePath, payload);

        // 模拟客户端中断下载：只读前 4KB 就关流（不读完、不收尾）
        try (InputStream in = client.retrieveFileStream(filePath)) {
            assertNotNull(in, "retrieveFileStream 应成功");
            assertTrue(in.read(new byte[4096]) > 0, "应至少读到部分字节");
        }
        // 中断后必须发 ABOR；异常也应吞掉并继续（不能让文件一直被攥住）
        try {
            client.abort();
        } catch (IOException e) {
            log.warn("[FtpUsage] ABOR 发送失败: {}", e.getMessage());
        }

        // 附带验证同一连接控制通道通常可复用；个别服务端实现可能已断开，此处容忍异常
        try {
            client.listFiles(TEST_DIR);
        } catch (IOException e) {
            log.warn("[FtpUsage] ABOR 后同一连接 listFiles 失败(可接受，将用新连接验证): {}", e.getMessage());
        }

        // 关键断言：中断+ABOR 后，该文件必须未被服务端攥住，能再次完整下载
        AutoCloseableFTPClient verify = openClient();
        Assumptions.assumeTrue(verify != null, "FTP 服务不可用，跳过测试");
        try {
            assertArrayEquals(payload, downloadBytes(verify, filePath),
                    "中断+ABOR 后，同一文件应可再次完整下载（未被服务端攥住）");
        } finally {
            verify.close();
        }
    }

    /**
     * 技术点：失败按应答码分流。
     *
     * <p>retrieveFile 只返回 true/false，真实原因（不存在=550、权限=550 denied、忙=450、传输中断=426）
     * 只存在于控制通道应答码里。对不存在的文件，应答应为失败且不可重试。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testReplyCodeClassificationOnMissingFile() throws IOException {
        String missing = TEST_DIR + "/no-such-file.bin";
        boolean ok = client.retrieveFile(missing, new ByteArrayOutputStream());
        assertFalse(ok, "不存在的文件 retrieveFile 应返回 false");

        int code = client.getReplyCode();
        log.info("[FtpUsage] 取不存在文件应答: {}", ftpReplySummary(client));
        assertTrue(code >= 400, "失败应答码应在 4xx/5xx，实际: " + code);
        assertFalse(isFtpReplyRetryable(code), "不存在/无权限(550 族)不应被判定为可重试: " + code);
    }

    /**
     * 技术点：重试应答码分类——仅可恢复失败值得重试。
     *
     * <p>与 FtpController.isFtpReplyRetryable 一致：450/451/421/425/426（占用/忙/传输中断）
     * 是瞬时可恢复应答，550/553/530 是永久失败，重试无意义。</p>
     */
    @Test
    void testRetryableReplyCodeClassification() {
        for (int code : RETRYABLE_REPLY_CODES) {
            assertTrue(isFtpReplyRetryable(code), code + " 应为可重试应答");
        }
        assertFalse(isFtpReplyRetryable(550), "550 不存在/无权限是永久失败，重试无意义");
        assertFalse(isFtpReplyRetryable(553), "553 永久失败");
        assertFalse(isFtpReplyRetryable(530), "530 未登录，重试无意义");
    }

    /**
     * 技术点：永久失败（550）必须在第 1 次尝试后直接阻断，不浪费重试配额。
     *
     * <p>对不存在的文件 retrieveFile 返回 false 且应答码为 550（不可重试），
     * 重试循环应识别永久失败立即退出，而不是消耗完 3 次重试。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testRetryPolicyPermanentFailureStopsImmediately() throws IOException {
        String nonExistentFile = TEST_DIR + "/permanent-fail-550.bin";
        int maxAttempts = 3;
        int attempts = 0;
        boolean success = false;
        while (!success && attempts < maxAttempts) {
            attempts++;
            try {
                success = client.retrieveFile(nonExistentFile, new ByteArrayOutputStream());
                if (!success && !isFtpReplyRetryable(client.getReplyCode())) {
                    log.info("[FtpUsage] 检测到永久失败 code={}, 立即中断重试", client.getReplyCode());
                    break; // 关键分支：永久失败直接退出循环
                }
            } catch (IOException e) {
                log.warn("[FtpUsage] 遇到网络异常继续重试: ", e);
            }
        }
        assertEquals(1, attempts, "550 永久失败必须在第 1 次尝试后直接阻断，不消耗后续重试配额");
        assertFalse(success, "永久失败操作最终应为 false");
    }

    /**
     * 技术点：瞬时 IOException 必须计数重试并最终成功，而不是第一次失败就放弃。
     *
     * <p>FtpController 强调：任务抛异常应继续计数重试（瞬时 IOException 让「重试 N 次」
     * 第一次就结束是错误写法）。本用例注入前 2 次瞬时 IOException，第 3 次恢复正常下载。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testRetryPolicyRetriesTransientIOException() throws IOException {
        String retryFile = TEST_DIR + "/retry-target.bin";
        byte[] payload = "retry demo content".getBytes(StandardCharsets.UTF_8);
        uploadBytes(client, retryFile, payload);

        int maxAttempts = 3;
        int attempts = 0;
        boolean success = false;
        int injectedFailures = 0;
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        while (!success && attempts < maxAttempts) {
            attempts++;
            sink.reset();
            try {
                // 前两次模拟偶发瞬时 IOException 抖动
                if (injectedFailures < 2) {
                    injectedFailures++;
                    throw new IOException("模拟瞬时网络抖动 Connection reset");
                }
                success = client.retrieveFile(retryFile, sink);
                if (!success && !isFtpReplyRetryable(client.getReplyCode())) {
                    fail("永久失败(" + ftpReplySummary(client) + ")不应进入重试循环");
                }
            } catch (IOException e) {
                log.info("[FtpUsage] 第 {} 次尝试遭遇瞬时异常(正常触发计数重试): ", attempts, e);
            }
        }
        assertTrue(success, "瞬时异常重试后应最终成功");
        assertEquals(3, attempts, "应在第 3 次重试时成功");
        assertArrayEquals(payload, sink.toByteArray(), "重试下载后的内容应一致");
    }

    /**
     * 技术点：断点续传（Range 下载）。
     *
     * <p>setRestartOffset(offset) + retrieveFileStream 只从偏移处开始传输，
     * 用于视频/大文件 Range 播放；与 FtpController.streamMp4 的断点续传场景一致。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testRestartOffsetResumeDownload() throws IOException {
        String filePath = TEST_DIR + "/resume.bin";
        byte[] payload = new byte[128 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        uploadBytes(client, filePath, payload);

        int offset = 50 * 1024;
        client.setRestartOffset(offset);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = client.retrieveFileStream(filePath)) {
            assertNotNull(in, "retrieveFileStream 应成功");
            IOUtils.copy(in, out);
        }
        assertTrue(client.completePendingCommand(), "断点续传也应 completePendingCommand 收尾");

        byte[] resumed = out.toByteArray();
        assertEquals(payload.length - offset, resumed.length, "续传应只下载偏移之后的部分");
        assertArrayEquals(Arrays.copyOfRange(payload, offset, payload.length), resumed,
                "续传内容应与源文件偏移后完全一致");
    }

    /**
     * 技术点：appendFile 追加内容（原子方法）。
     *
     * <p>appendFile 在既有文件末尾追加，内部完整 copy 并等 226。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testAppendFileAppendsContent() throws IOException {
        String filePath = TEST_DIR + "/append.txt";
        uploadBytes(client, filePath, "part1|".getBytes(StandardCharsets.UTF_8));
        try (ByteArrayInputStream in = new ByteArrayInputStream("part2".getBytes(StandardCharsets.UTF_8))) {
            assertTrue(client.appendFile(filePath, in),
                    "appendFile 应成功: " + ftpReplySummary(client));
        }
        assertArrayEquals("part1|part2".getBytes(StandardCharsets.UTF_8),
                downloadBytes(client, filePath), "追加后内容应为 part1|part2");
    }

    /**
     * 技术点：目录与文件操作。
     *
     * <p>makeDirectory / rename / deleteFile / removeDirectory / listFiles 的完整组合，
     * 并校验每一步在列目录结果中的可见性。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testDirectoryRenameDeleteOps() throws IOException {
        String dir = TEST_DIR + "/ops_dir";
        assertTrue(client.makeDirectory(dir), "makeDirectory 应成功: " + ftpReplySummary(client));
        assertTrue(containsName(client.listFiles(TEST_DIR), "ops_dir"), "新建目录应出现在父目录列表中");

        String dirRenamed = TEST_DIR + "/ops_dir_renamed";
        assertTrue(client.rename(dir, dirRenamed), "rename 目录应成功: " + ftpReplySummary(client));
        assertTrue(containsName(client.listFiles(TEST_DIR), "ops_dir_renamed"), "重命名后新名应存在");
        assertFalse(containsName(client.listFiles(TEST_DIR), "ops_dir"), "重命名后旧名应消失");

        String file = dirRenamed + "/delete-me.txt";
        uploadBytes(client, file, "to delete".getBytes(StandardCharsets.UTF_8));
        assertTrue(client.deleteFile(file), "deleteFile 应成功: " + ftpReplySummary(client));
        assertFalse(containsName(client.listFiles(dirRenamed), "delete-me.txt"), "删除后文件应消失");

        assertTrue(client.removeDirectory(dirRenamed), "removeDirectory 应成功: " + ftpReplySummary(client));
        assertFalse(containsName(client.listFiles(TEST_DIR), "ops_dir_renamed"), "删除后目录应消失");
    }

    /**
     * 技术点：setControlEncoding("UTF-8") 支持中文文件名。
     *
     * <p>控制通道编码必须在 connect 之前设置（{@link #openClient()} 已做）；配合服务端
     * ftpserver.encoding=UTF-8，中文目录/文件名可正常建、传、取、列。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void testChineseFilenameWithUtf8Encoding() throws IOException {
        String dir = TEST_DIR + "/中文子目录";
        assertTrue(client.makeDirectory(dir), "makeDirectory 中文目录应成功: " + ftpReplySummary(client));

        String filePath = dir + "/中文文件.txt";
        byte[] payload = "中文内容测试".getBytes(StandardCharsets.UTF_8);
        uploadBytes(client, filePath, payload);
        assertArrayEquals(payload, downloadBytes(client, filePath), "中文文件内容往返应一致");
        assertTrue(containsName(client.listFiles(dir), "中文文件.txt"), "中文文件名应可列目录识别");
    }

    /**
     * 建立并登录一个被动模式的 FTP 连接。
     *
     * <p>控制通道编码在 connect 之前设置；被动模式在 connect 之后调用（connect 会重置回主动模式）。
     * FTP 服务不可用或登录失败时返回 null，由调用方 Assumptions 跳过测试。</p>
     *
     * @return 已登录且处于被动模式的客户端；服务不可用时返回 null
     */
    private AutoCloseableFTPClient openClient() {
        AutoCloseableFTPClient c = new AutoCloseableFTPClient();
        try {
            c.setDefaultTimeout(5000);
            c.setConnectTimeout(5000);
            c.setDataTimeout(Duration.ofSeconds(10));
            c.setControlEncoding("UTF-8");
            c.connect(FTP_HOST, FTP_PORT);
            if (!c.login(FTP_USER, FTP_PASS)) {
                log.warn("[FtpUsage] FTP 登录失败: {}", ftpReplySummary(c));
                c.close();
                return null;
            }
            c.enterLocalPassiveMode();
            c.setFileType(FTP.BINARY_FILE_TYPE);
            return c;
        } catch (IOException e) {
            log.warn("[FtpUsage] FTP 服务连接失败(127.0.0.1:21): {}", e.getMessage());
            try {
                c.close();
            } catch (Exception ignored) {
                // 连接未建立时 close 只做幂等断开
            }
            return null;
        }
    }

    /**
     * 原子上传：storeFile 写入全部字节并等 226。
     *
     * @param c    FTP 客户端
     * @param path 远程路径
     * @param data 待写入字节
     * @throws IOException FTP 命令失败
     */
    private void uploadBytes(AutoCloseableFTPClient c, String path, byte[] data) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            assertTrue(c.storeFile(path, in), "storeFile 应成功: " + path + ", " + ftpReplySummary(c));
        }
    }

    /**
     * 原子下载：retrieveFile 读出全部字节并等 226。
     *
     * @param c    FTP 客户端
     * @param path 远程路径
     * @return 文件全部字节
     * @throws IOException FTP 命令失败
     */
    private byte[] downloadBytes(AutoCloseableFTPClient c, String path) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(c.retrieveFile(path, out), "retrieveFile 应成功: " + path + ", " + ftpReplySummary(c));
        return out.toByteArray();
    }

    /**
     * 获取远程文件大小：优先 SIZE 命令（213 应答），失败则 listFiles 兜底。
     *
     * <p>与 FtpController.streamMp42 的取值策略一致：SIZE 失败不代表文件不存在，需 listFiles 兜底。</p>
     *
     * @param c    FTP 客户端
     * @param path 远程路径
     * @return 文件大小（字节）
     * @throws IOException FTP 命令失败
     */
    private long getRemoteFileSize(AutoCloseableFTPClient c, String path) throws IOException {
        c.sendCommand("SIZE", path);
        String reply = c.getReplyString();
        if (reply != null && reply.startsWith("213 ")) {
            try {
                return Long.parseLong(reply.substring(4).trim());
            } catch (NumberFormatException e) {
                log.warn("[FtpUsage] 解析 SIZE 应答失败: {}", reply.trim());
            }
        }
        FTPFile[] files = c.listFiles(path);
        if (files == null || files.length == 0) {
            fail("SIZE 与 listFiles 均无法获取文件大小: " + path + ", " + ftpReplySummary(c));
        }
        return files[0].getSize();
    }

    /**
     * 该 FTP 应答码是否值得重试。
     *
     * <p>与 FtpController.isFtpReplyRetryable 一致：450/451/421/425/426 是瞬时可恢复
     * （占用/忙/传输中断），550/553/530 是永久失败。</p>
     *
     * @param code FTP 应答码
     * @return true 表示可重试
     */
    static boolean isFtpReplyRetryable(int code) {
        return code == 450 || code == 451 || code == 421 || code == 425 || code == 426;
    }

    /**
     * 目录列表是否包含指定名称。
     *
     * @param files FTP 列目录结果
     * @param name  目标名称
     * @return true 表示包含
     */
    private boolean containsName(FTPFile[] files, String name) {
        if (files == null) {
            return false;
        }
        for (FTPFile file : files) {
            if (name.equals(file.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 递归删除远程目录（文件优先，目录自底向上）。
     *
     * @param c    FTP 客户端
     * @param path 待删除路径
     * @throws IOException FTP 命令失败
     */
    private void deleteRecursive(AutoCloseableFTPClient c, String path) throws IOException {
        c.changeWorkingDirectory("/");
        FTPFile[] files = c.listFiles(path);
        if (files == null) {
            return;
        }
        for (FTPFile file : files) {
            String name = file.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String subPath = path + "/" + name;
            if (file.isDirectory()) {
                deleteRecursive(c, subPath);
            } else {
                c.deleteFile(subPath);
            }
        }
        c.removeDirectory(path);
    }

    /**
     * 逐级创建远程目录（路径以 / 开头）。
     *
     * @param c    FTP 客户端
     * @param path 待创建路径
     * @throws IOException FTP 命令失败
     */
    private void makeDirectoryRecursive(AutoCloseableFTPClient c, String path) throws IOException {
        c.changeWorkingDirectory("/");
        String[] dirs = path.split("/");
        for (String dir : dirs) {
            if (dir.isEmpty()) {
                continue;
            }
            c.makeDirectory(dir);
            c.changeWorkingDirectory(dir);
        }
        c.changeWorkingDirectory("/");
    }

    /**
     * 生成 FTP 客户端最近一次服务器应答的摘要。
     *
     * <p>retrieveFile/retrieveFileStream 只返回 true/false 或 null，失败的真实原因
     * （被占用=450、不存在/无权限=550、传输中断=426 等）只存在于控制通道应答码里，
     * 失败分支务必把应答码一并记录，否则多种失败统一表现为「失败」，无法定案。</p>
     *
     * @param c FTP 客户端
     * @return 例如 replyCode=550, replyString=550 File unavailable. (permission)
     */
    private String ftpReplySummary(AutoCloseableFTPClient c) {
        if (c == null || !c.isConnected()) {
            return "replyCode=(none), replyString=(client not connected)";
        }
        int code = c.getReplyCode();
        String reply = c.getReplyString();
        String collapsed = reply == null ? "(null)" : reply.trim().replaceAll("\\s+", " ");
        return "replyCode=" + code + ", replyString=" + (collapsed.isEmpty() ? "(empty)" : collapsed);
    }
}
