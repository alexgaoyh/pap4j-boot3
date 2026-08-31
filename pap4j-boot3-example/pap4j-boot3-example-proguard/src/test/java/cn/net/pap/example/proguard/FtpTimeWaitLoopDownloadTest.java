package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.autoclose.AutoCloseableFTPClient;
import org.apache.commons.net.ftp.FTP;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Windows 下观察「循环 FTP 下载 → TCP TIME_WAIT 大量堆积」的实验测试。
 *
 * <p><b>流程</b>：初始化时（@BeforeAll）上传一次测试文件 → 循环「新建连接 → 下载同一文件 → 关闭」→
 * 打印循环结束后的 TIME_WAIT 统计。初始化与结束后各打印一次 netstat 的 TIME_WAIT 信息，
 * 便于直接看到本次循环产生了多少 TIME_WAIT。</p>
 *
 * <p><b>已实测结论（本机 127.0.0.1:21 为 FileZilla Server）</b>：TIME_WAIT 由「先发 FIN 的一方」承担。
 * 线程数=2 时 FileZilla 总是先关连接，TIME_WAIT 落在服务端、客户端端口被复用；线程数调大后客户端
 * disconnect 能抢到先发 FIN，客户端本地端口也开始进入 TIME_WAIT。循环足够快/端口池被 TIME_WAIT 占用时，
 * 新连接失败（客户端端口耗尽为 SocketException，服务端侧问题多为 Read timed out）。</p>
 *
 * <p><b>与「动态端口数量」的对比方法</b>（改端口需管理员）：
 * <pre>
 *   netsh int ipv4 show dynamicport tcp                     // 查看当前动态端口数量
 *   netsh int ipv4 set dynamicport tcp start=1024 numports=500      // 收紧端口跑一轮
 *   netsh int ipv4 set dynamicport tcp start=1024 numports=13977    // 放宽后再跑一轮对比
 *   netsh int ipv4 set dynamicport tcp start=49152 numports=16384   // 恢复 Windows 默认（或你改动前的值）
 *   netstat -ano | findstr TIME_WAIT                         // 手动观察 TIME_WAIT 连接
 * </pre>
 * 端口数量越小，循环在越早的迭代即告失败；端口数量足够大时整个循环全部成功。注意：本测试 JVM 与
 * FileZilla 同机、共用动态端口池，且本机还有其它进程在消耗端口，numports 别设太小以免影响其它程序。</p>
 *
 * <p><b>运行前提</b>：需配套启动 FTP 服务（127.0.0.1:21，用户 bj/123456，home d:/knowledge），
 * 服务不可用时本测试通过 Assumptions 跳过。</p>
 *
 * <p><b>参数</b>：{@code -Dftp.timewait.loops=2000} 可覆盖迭代次数（默认 7400）；
 * {@code -Dftp.timewait.rst=true} 开启 SO_LINGER=0（控制连接与下载数据连接 close 发 RST，双方不产生 TIME_WAIT）；
 * {@code -Dftp.timewait.reuse=false} 关闭控制连接复用（默认 true：复用单条控制连接跑完全部迭代，高频下载主形态）；
 * {@code -Dftp.timewait.drainWaitMs=0} 跳过压测前的 TIME_WAIT 释放等待（默认等待 120000ms，保证多轮压测基线一致）。</p>
 *
 * @see cn.net.pap.example.proguard.autoclose.AutoCloseableFTPClient
 * @see FtpClientUsageExampleTest
 */
public class FtpTimeWaitLoopDownloadTest {

    private static final Logger log = LoggerFactory.getLogger(FtpTimeWaitLoopDownloadTest.class);

    private static final String FTP_HOST = "127.0.0.1";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "bj";
    private static final String FTP_PASS = "123456";

    /** 循环下载的同一远程文件（挂在 FTP home 根下）。 */
    private static final String REMOTE_FILE = "/ftp_timewait_demo.dat";

    /** 下载文件的字节数（足够承载一次真实的数据连接传输即可）。 */
    private static final int PAYLOAD_SIZE = 32 * 1024;

    /** 循环迭代次数，可用 -Dftp.timewait.loops=N 覆盖。 */
    private static final int LOOPS = Math.max(1, Integer.getInteger("ftp.timewait.loops", 60000));

    /** 是否开启 SO_LINGER=0（close 时发 RST，双方均不产生 TIME_WAIT），可用 -Dftp.timewait.rst=true 覆盖。 */
    private static final boolean RST_ON_CLOSE = Boolean.getBoolean("ftp.timewait.rst");

    /** 是否复用单条控制连接跑完全部迭代。默认 true（高频下载主形态：控制连接只建一次，逐次仅新开数据连接）；
     *  传 -Dftp.timewait.reuse=false 回退到原始的「每轮新建连接→下载→关闭」实验形态。 */
    private static final boolean REUSE_CONNECTION = Boolean.parseBoolean(System.getProperty("ftp.timewait.reuse", "true"));

    /** 压测前等待 TIME_WAIT 释放的时长（毫秒），保证多轮压测在相同背景下对比，避免基准不一致；
     *  可用 -Dftp.timewait.drainWaitMs=0 跳过。默认 120000（需求要求每次代码修改后等 2 分钟）。 */
    private static final long DRAIN_WAIT_MS = Math.max(0, Long.getLong("ftp.timewait.drainWaitMs", 120_000L));

    /** 初始化时上传的测试文件内容，循环下载时用于校验完整性。 */
    private static byte[] payload;

    /**
     * 初始化（整类只执行一次）：探测 FTP 可用 → 上传测试文件 → 释放 TIME_WAIT → 打印 TIME_WAIT 基线。
     *
     * @throws IOException FTP 命令失败
     */
    @BeforeAll
    static void setUpOnce() throws IOException {
        Assumptions.assumeTrue(ftpServerReachable(), "FTP 服务不可用(127.0.0.1:21)，跳过测试");

        logTcpDynamicPortRange();
        log.info("[FtpTimeWait] 参数: loops={}, SO_LINGER=0(RST 关闭)={}, 复用单连接={}, drainWaitMs={}",
                LOOPS, RST_ON_CLOSE, REUSE_CONNECTION, DRAIN_WAIT_MS);

        payload = new byte[PAYLOAD_SIZE];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        uploadRemoteFile(payload);

        // 关键时序：在上传测试文件后执行 TIME_WAIT 释放等待，
        // 彻底排空历史遗留及本次上传产生的 TCP 连接，使循环压测在绝对纯净的一致基线下启动
        drainTimeWait();

        logTimeWait("初始化完成（已上传测试文件并完成 TIME_WAIT 释放等待）");
    }

    /**
     * 循环「新建连接 → 下载同一文件 → 正常关闭」，随后打印循环结束后的 TIME_WAIT 统计。
     *
     * <p>首次失败即中断循环并输出汇总；失败为 {@code SocketException: Cannot assign requested address}
     * 表示客户端端口被 TIME_WAIT 占满，为 {@code Read timed out} 多为服务端侧问题。</p>
     *
     * @throws IOException FTP 命令失败
     */
    @Test
    void loopDownloadSameFileToAccumulateTimeWait() throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream(PAYLOAD_SIZE);
        long startNanos = System.nanoTime();

        LoopResult result = REUSE_CONNECTION
                ? runReusedConnectionLoop(sink)
                : runPerConnectionLoop(sink);

        long costMs = (System.nanoTime() - startNanos) / 1_000_000;
        printSummary(result, costMs);
        logTimeWait("循环下载结束后");
    }

    /**
     * 复用单条控制连接跑完全部迭代（高频下载主形态）。
     */
    private LoopResult runReusedConnectionLoop(ByteArrayOutputStream sink) throws IOException {
        log.info("[FtpTimeWait] 模式: 复用单条控制连接, RST={}", RST_ON_CLOSE);
        try (AutoCloseableFTPClient c = openConnected()) {
            int success = 0;
            for (int i = 1; i <= LOOPS; i++) {
                try {
                    downloadOnce(c, payload.length, sink);
                    success++;
                    if (success == 1) {
                        assertArrayEquals(payload, sink.toByteArray(), "首轮下载内容应与上传文件逐字节一致");
                    }
                    if (i % 50 == 0) {
                        log.info("[FtpTimeWait] 第 {} 次迭代下载成功，累计成功 {} 次", i, success);
                    }
                } catch (SocketException e) {
                    log.error("[FtpTimeWait] 第 {} 次迭代失败【客户端端口耗尽(成功次数应≈动态端口数量)】: ", i, e);
                    return new LoopResult(success, i, e.toString());
                } catch (IOException e) {
                    log.error("[FtpTimeWait] 第 {} 次迭代失败【其他异常(常见为 Read timed out)】: ", i, e);
                    return new LoopResult(success, i, e.toString());
                }
            }
            return new LoopResult(success, -1, null);
        }
    }

    /**
     * 每轮新建连接并关闭（原始实验形态）。
     */
    private LoopResult runPerConnectionLoop(ByteArrayOutputStream sink) {
        log.info("[FtpTimeWait] 模式: 每次新建连接, RST={}", RST_ON_CLOSE);
        int success = 0;
        for (int i = 1; i <= LOOPS; i++) {
            try {
                downloadOnce(payload.length, sink);
                success++;
                if (success == 1) {
                    assertArrayEquals(payload, sink.toByteArray(), "首轮下载内容应与上传文件逐字节一致");
                }
                if (i % 50 == 0) {
                    log.info("[FtpTimeWait] 第 {} 次迭代下载成功，累计成功 {} 次", i, success);
                }
            } catch (SocketException e) {
                log.error("[FtpTimeWait] 第 {} 次迭代失败【客户端端口耗尽(成功次数应≈动态端口数量)】: ", i, e);
                return new LoopResult(success, i, e.toString());
            } catch (IOException e) {
                log.error("[FtpTimeWait] 第 {} 次迭代失败【其他异常(常见为 Read timed out)】: ", i, e);
                return new LoopResult(success, i, e.toString());
            }
        }
        return new LoopResult(success, -1, null);
    }

    /**
     * 打印压测汇总与速率指标。
     */
    private void printSummary(LoopResult r, long costMs) {
        log.info("[FtpTimeWait] ============================================================");
        // 中断发生在 firstFailIndex 那次迭代：实际失败仅 1 次，其余 firstFailIndex+1..LOOPS 从未执行，
        // 不能把「未执行」计入「失败」误导基准对比
        int actualFails = r.firstFailIndex() == -1 ? 0 : 1;
        int notRun = r.firstFailIndex() == -1 ? 0 : LOOPS - r.firstFailIndex();
        log.info("[FtpTimeWait] 迭代目标 {} 次，成功 {} 次，实际失败 {} 次，中断后未执行 {} 次，耗时 {} ms",
                LOOPS, r.success(), actualFails, notRun, costMs);
        log.info("[FtpTimeWait] 下载速率: {} 次/秒（{} ms/次）",
                r.success() == 0 ? 0.0 : r.success() * 1000.0 / costMs,
                r.success() == 0 ? 0 : costMs / r.success());
        if (r.firstFailIndex() != -1) {
            log.info("[FtpTimeWait] 首次失败发生在第 {} 次迭代（成功 {} 次后端口耗尽）: {}",
                    r.firstFailIndex(), r.success(), r.firstFailReason());
        } else {
            log.info("[FtpTimeWait] 全部成功 → 动态端口数量足够，未触发端口耗尽；可收紧动态端口后重跑对比");
        }
        log.info("[FtpTimeWait] ============================================================");
    }

    /** 迭代执行结果载体。 */
    private record LoopResult(int success, int firstFailIndex, String firstFailReason) {}

    /**
     * 压测前等待，使上一轮压测残留的 TIME_WAIT 释放，保证各轮压测基线一致。
     * 时长由 {@code -Dftp.timewait.drainWaitMs} 控制（默认 120000ms，设 0 跳过）。
     * <b>注意：已实测该等待只保证基线一致，不能解除 FileZilla 对单 IP 的连接处理吞吐墙（Read timed out 仍在）。</b>
     *
     * @throws IOException 等待被中断
     */
    private static void drainTimeWait() throws IOException {
        if (DRAIN_WAIT_MS <= 0) {
            return;
        }
        log.info("[FtpTimeWait] 等待 {} ms 使 TIME_WAIT 释放（保证多轮压测基线一致）...", DRAIN_WAIT_MS);
        try {
            Thread.sleep(DRAIN_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待 TIME_WAIT 释放被打断", e);
        }
    }

    /**
     * 探测 FTP 服务是否可用（连接并登录，随即关闭）。
     *
     * @return true 表示 127.0.0.1:21 可用
     */
    private static boolean ftpServerReachable() {
        try (AutoCloseableFTPClient c = openConnected()) {
            return c.isConnected() && c.isAvailable();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 上传固定内容的测试文件（每次运行覆盖，保证自包含）。
     *
     * @param data 文件内容
     * @throws IOException FTP 命令失败
     */
    private static void uploadRemoteFile(byte[] data) throws IOException {
        try (AutoCloseableFTPClient c = openConnected()) {
            try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
                if (!c.storeFile(REMOTE_FILE, in)) {
                    throw new IOException("storeFile 失败: " + c.getReplyString());
                }
            }
        }
    }

    /**
     * 新建一条连接下载 {@link #REMOTE_FILE} 到 sink，并正常关闭连接。
     *
     * @param expectedSize 期望字节数，用于校验下载完整性
     * @param sink         下载输出（进入前被重置）
     * @throws IOException FTP 命令失败 / 下载内容不符
     */
    private static void downloadOnce(int expectedSize, ByteArrayOutputStream sink) throws IOException {
        // 每次新开一条连接（原始实验形态），复用模式下走带客户端参数的重载
        try (AutoCloseableFTPClient c = openConnected()) {
            downloadOnce(c, expectedSize, sink);
        }
    }

    /**
     * 在已连接/已登录的客户端上执行一次下载（复用模式：控制连接只建一次，每次下载仅新开数据连接）。
     *
     * @param c            已连接并处于被动+二进制的客户端
     * @param expectedSize 期望字节数，用于校验下载完整性
     * @param sink         下载输出（进入前被重置）
     * @throws IOException FTP 命令失败 / 下载内容不符
     */
    private static void downloadOnce(AutoCloseableFTPClient c, int expectedSize, ByteArrayOutputStream sink) throws IOException {
        sink.reset();
        if (!c.retrieveFile(REMOTE_FILE, sink)) {
            throw new IOException("retrieveFile 失败: " + c.getReplyString());
        }
        if (sink.size() != expectedSize) {
            throw new IOException("下载字节数与源不符: " + sink.size() + " != " + expectedSize);
        }
    }

    /**
     * 建连并登录，返回被动模式 + BINARY 的客户端；连接/登录失败时抛出 IOException。
     *
     * @return 已连接且可用的客户端
     * @throws IOException 连接或登录失败
     */
    private static AutoCloseableFTPClient openConnected() throws IOException {
        AutoCloseableFTPClient c = RST_ON_CLOSE ? new AutoCloseableFTPClient(true) : new AutoCloseableFTPClient();
        try {
            c.setControlEncoding("UTF-8");
            c.setConnectTimeout(5000);
            c.setDefaultTimeout(5000);
            // 数据连接超时三件套之一：dataTimeout 默认 -1 时 _openDataConnection_ 不会设置数据 socket 的
            // SO_TIMEOUT（无限超时），服务端数据通道中途停滞会永久挂起而非超时失败，必须显式设置
            // （与 FtpClientUsageExampleTest 的三件套最佳实践一致）
            c.setDataTimeout(Duration.ofSeconds(5));
            // 高频下载 I/O 缓冲区优化：由默认 1KB 扩至 64KB，显著降低系统调用与 I/O 轮询开销
            c.setBufferSize(64 * 1024);
            c.connect(FTP_HOST, FTP_PORT);
            if (!c.login(FTP_USER, FTP_PASS)) {
                throw new IOException("FTP 登录失败: " + c.getReplyString());
            }
            c.enterLocalPassiveMode();
            c.setFileType(FTP.BINARY_FILE_TYPE);
            return c;
        } catch (IOException e) {
            try {
                c.close();
            } catch (Exception ignored) {
                // 关闭失败可忽略
            }
            throw e;
        }
    }

    /**
     * 打印当前 TCP 动态端口范围（netsh 输出按 GBK 解码；即使中文乱码，起始端口/端口数两个数字仍可解析），
     * 使每次运行的日志自带端口配置，便于不同设置下对比。
     */
    private static void logTcpDynamicPortRange() {
        try {
            Process p = new ProcessBuilder("netsh", "int", "ipv4", "show", "dynamicport", "tcp").start();
            String out = new String(p.getInputStream().readAllBytes(), Charset.forName("GBK"));
            p.waitFor();
            log.info("[FtpTimeWait] netsh int ipv4 show dynamicport tcp:\n{}", out.trim());
            Matcher m = Pattern.compile("\\d+").matcher(out);
            if (m.find()) {
                String start = m.group();
                String count = m.find() ? m.group() : "?";
                log.info("[FtpTimeWait] 当前 TCP 动态端口: start={}, count={}", start, count);
            }
        } catch (Exception e) {
            log.warn("[FtpTimeWait] 读取 TCP 动态端口范围失败(不影响测试): ", e);
        }
    }

    /**
     * 打印当前 TIME_WAIT 信息：运行 {@code netstat -ano} 统计总数、与 FTP :21 相关的数量并打印少量示例。
     *
     * @param phase 统计阶段的描述（如「初始化完成」「循环下载结束后」）
     */
    private static void logTimeWait(String phase) {
        try {
            Process p = new ProcessBuilder("netstat", "-ano").start();
            String out = new String(p.getInputStream().readAllBytes(), Charset.forName("GBK"));
            p.waitFor();
            List<String> all = new ArrayList<>();
            List<String> ftp = new ArrayList<>();
            for (String line : out.split("\\r?\\n")) {
                if (!line.contains("TIME_WAIT")) {
                    continue;
                }
                all.add(line);
                if (line.contains("127.0.0.1:21")) {
                    ftp.add(line);
                }
            }
            log.info("[FtpTimeWait] ==== TIME_WAIT 统计（阶段：{}）总数={}，与 FTP:21 相关={} ====",
                    phase, all.size(), ftp.size());
            for (String line : ftp.subList(0, Math.min(3, ftp.size()))) {
                log.info("[FtpTimeWait]   示例 {}", line.trim());
            }
            if (ftp.size() > 3) {
                log.info("[FtpTimeWait]   其余 {} 条略", ftp.size() - 3);
            }
        } catch (Exception e) {
            log.warn("[FtpTimeWait] 统计 TIME_WAIT 失败(不影响测试): ", e);
        }
    }
}
