package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.autoclose.AutoCloseableFTPClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * FTP 流传输测试控制器，集中演示 Apache Commons Net {@code FTPClient} 的正确用法。
 *
 * <p><b>背景</b>：生产环境「成品导出图片报未找到」的根因，是下载失败被压成了一个布尔值——真实原因
 * （文件被占用 450、不存在/无权限 550、传输中断 426）都在控制通道的应答码里，却被一律翻译成「未找到 / 404」。
 * 下面的规则围绕一件事：<b>把一次 FTP 传输做完整，把失败原因说清楚</b>。</p>
 *
 * <h2>一、传输收尾：一次传输怎么算做完</h2>
 * <ol>
 *   <li><b>取流必须完整收尾</b>：{@code retrieveFileStream} 打开数据通道后，必须 ① 关闭流 ② 调用
 *       {@code completePendingCommand()} 收 226 应答。缺任一步，连接就停在「传输未完成」：轻则不可复用，
 *       重则控制通道错位（下一条命令读到旧应答）、数据 socket 变孤儿、服务器端一直攥着文件。</li>
 *   <li><b>中断必须发 ABOR，不能只断开</b>：客户端断开、读取异常、解码失败提前返回时，都要发 ABOR 让服务器立即释放文件
 *       （本类用 {@code needsAbort} + finally 实现）。典型触发是 HTTP 预览时浏览器取消/提前停读。若泄漏连接一直攥着锁，
 *       别的连接重试同一文件会<b>确定性</b>地 450/550 busy——不是瞬时抖动，重试救不回。</li>
 * </ol>
 *
 * <h2>二、方法选择与失败处理</h2>
 * <ol start="3">
 *   <li><b>区分原子方法与流式方法</b>：{@code storeFile / retrieveFile / appendFile}（配 InputStream/OutputStream）是原子的——
 *       内部完整 copy 并等 226，返回 true 即已落盘；{@code storeFileStream / retrieveFileStream} 只给裸流，关流和收尾必须自己管。
 *       用流式方法却漏收尾，是服务器上出现 0 字节/半截文件的常见来源。</li>
 *   <li><b>失败按应答码分流，并透出重试信息</b>：应答码是一对多（550 既可能不存在也可能权限不足），一个状态码承载不了。
 *       本类用 {@link #ftpFailureHttpStatus} 映射（550/551/553→404 或 403、450/451/421→503、425/426/530→502），
 *       用 {@link #markFtpFailureHeaders} 透出 {@code X-Ftp-Reply} / {@code X-Ftp-Retryable}，
 *       可重试判定见 {@link #isFtpReplyRetryable}（仅 450/451/421/425/426 可重试，550/553 永久失败）。</li>
 *   <li><b>下载成功 ≠ 内容完整</b>：{@code retrieveFile} 返回 true 且收 226，本地也可能是 0 字节占位或半截文件——它不校验内容，
 *       坏文件照常返回 true。生产下载要校验实际字节数；「事后能打开」不等于「导出那一刻文件是好的」。</li>
 * </ol>
 *
 * <h2>三、资源与重试</h2>
 * <ol start="6">
 *   <li><b>异常路径也要释放连接与流</b>：连接要放 finally（{@code destory/disconnect}），只放 try 尾部会泄漏；
 *       中途异常要关流并复位（ABOR），否则脏控制通道污染共享连接；不读完也不收尾就断开，数据 socket 会变孤儿。
 *       优先用 {@code AutoCloseableFTPClient}（close() 统一登出断开），别散落 {@code new FTPClient()}。</li>
 *   <li><b>重试只对「返回 false」生效，永久失败不重试</b>：任务抛异常应继续计数重试而非直接放弃（否则瞬时 IOException
 *       让「重试 N 次」第一次就结束）；550 这类永久失败重试无意义，只对可恢复应答（见第 4 条）重试。</li>
 * </ol>
 *
 * @see cn.net.pap.example.proguard.autoclose.AutoCloseableFTPClient
 */
@RestController
@RequestMapping("/ftp")
@Tag(name = "FTP 流传输测试接口", description = "演示通过 FTP 协议进行视频断点续传流和图片流式读取与展示的接口")
public class FtpController {

    private static final Logger log = LoggerFactory.getLogger(FtpController.class);

    private static final int BUFFER_SIZE = 1024 * 512;

    private static final String FTP_HOST = "127.0.0.1";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "bj";
    private static final String FTP_PASS = "123456";
    private static final String VIDEO_PATH = "test.mp4";
    private static final String JPG_PATH = "big-plane-yes1.jpg";

    /**
     * ftp mp4 stream range
     *
     * @param request
     * @param response
     * @throws IOException
     */
    @Operation(summary = "流式读取播放 MP4 (断点续传/Range)", description = "通过读取 FTP 上的 mp4 视频文件，并支持 Range 请求实现流式播放与断点续传。已弃用，请使用 streammp42。")
    @GetMapping("/streammp4")
    @Deprecated
    public void streamMp4(HttpServletRequest request, HttpServletResponse response) throws IOException {

        long fileSize;

        // 1. 获取文件大小
        try (AutoCloseableFTPClient metaClient = new AutoCloseableFTPClient()) {
            metaClient.connect(FTP_HOST, FTP_PORT);
            metaClient.login(FTP_USER, FTP_PASS);
            metaClient.enterLocalPassiveMode();
            metaClient.setFileType(FTP.BINARY_FILE_TYPE);

            FTPFile file = metaClient.mlistFile(VIDEO_PATH);
            if (file == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            fileSize = file.getSize();

        } catch (Exception e) {
            log.error("获取视频元信息失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        // 2. 解析 Range
        String range = request.getHeader("Range");
        boolean hasRange = range != null && range.startsWith("bytes=");

        long start = 0;
        long end = fileSize - 1;

        if (hasRange) {
            String[] parts = range.substring(6).split("-");
            start = Long.parseLong(parts[0]);
            if (parts.length > 1 && !parts[1].isEmpty()) {
                end = Long.parseLong(parts[1]);
            }
        }

        if (end >= fileSize) end = fileSize - 1;

        long contentLength = end - start + 1;

        // 3. 设置响应头
        response.setContentType("video/mp4");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Length", String.valueOf(contentLength));
        if (hasRange) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize));
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }

        // 4. 同步读取 FTP 文件并写入输出流
        try (AutoCloseableFTPClient client = new AutoCloseableFTPClient()) {
            client.connect(FTP_HOST, FTP_PORT);
            client.login(FTP_USER, FTP_PASS);
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);

            // 是否需要向服务端补发 ABOR（若流正常读完并完成 226/426 收尾，则置为 false）
            boolean needsAbort = true;
            try {
                client.setRestartOffset(start);
                try (InputStream in = client.retrieveFileStream(VIDEO_PATH)) {
                    if (in != null) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        long remaining = contentLength;
                        int read;

                        while (remaining > 0 && (read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                            response.getOutputStream().write(buffer, 0, read);
                            remaining -= read;
                        }
                        response.flushBuffer();
                    } else {
                        log.warn("从 FTP 获取文件流失败(空流): {}, {}", VIDEO_PATH, ftpReplySummary(client));
                        markFtpFailureHeaders(response, client);
                        return;
                    }
                }

                // 流关闭后，再调用 completePendingCommand 接收 226 响应（close() 不会自动做这一步）
                if (client.isConnected()) {
                    try {
                        if (!client.completePendingCommand()) {
                            log.warn("FTP 数据传输未收到 226 完成应答(可能传输中断): {}", ftpReplySummary(client));
                        }
                    } catch (IOException e) {
                        log.error("FTP completePendingCommand 异常: {}", ftpReplySummary(client), e);
                    }
                }
                // 控制通道已完成应答消费（无论 226 还是 426），服务端均已释放文件，无需再发 ABOR
                needsAbort = false;
            } finally {
                // 传输未正常收尾（如客户端中断）：发 ABOR 让服务器释放文件，避免被攥住
                if (needsAbort && client.isConnected()) {
                    try {
                        client.abort();
                    } catch (Exception ignored) {
                        log.debug("发送 FTP ABOR 命令失败: {}", ignored.getMessage());
                    }
                }
            }
            // 登出 + 断开由 AutoCloseableFTPClient.close() 统一完成
        } catch (Exception e) {
            if (!isClientAbort(e)) {
                log.error("FTP streaming failed: ", e);
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "FTP streaming failed");
                }
            }
        }
    }

    @Operation(summary = "流式读取播放 MP4 版本2 (推荐)", description = "改进版 FTP mp4 视频流式读取播放，采用更健壮的连接关闭和分块读取机制。")
    @GetMapping("/streammp42")
    public void streamMp42(HttpServletRequest request, HttpServletResponse response) throws IOException {

        boolean isNormalCompletion = false;

        try (AutoCloseableFTPClient client = new AutoCloseableFTPClient()) {
            try {
                // 1. 建立连接并登录 (使用写死的配置)
                client.connect(FTP_HOST, FTP_PORT);
                client.login(FTP_USER, FTP_PASS);
                client.enterLocalPassiveMode();
                client.setFileType(FTP.BINARY_FILE_TYPE);

                // 2. 获取文件大小 (采用方法1思路：优先 SIZE 命令，listFiles 兜底)
                long fileSize = 0;
                client.sendCommand("SIZE", VIDEO_PATH);
                String reply = client.getReplyString();

                if (reply == null || !reply.startsWith("213 ")) {
                    log.warn("FTP SIZE 命令未返回 213 应答: {}, serverReply={}", VIDEO_PATH,
                            reply == null ? "(null)" : reply.trim());

                    FTPFile[] files = client.listFiles(VIDEO_PATH);
                    if (files == null || files.length == 0) {
                        log.warn("通过 listFiles 亦未找到文件: {}, {}", VIDEO_PATH, ftpReplySummary(client));
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    fileSize = files[0].getSize();
                } else {
                    try {
                        fileSize = Long.parseLong(reply.substring(4).trim());
                    } catch (NumberFormatException e) {
                        log.error("解析 SIZE 响应失败, reply: {}", reply, e);
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    }
                }

                // 3. 解析 Range
                String range = request.getHeader("Range");
                boolean hasRange = range != null && range.startsWith("bytes=");

                long start = 0;
                long end = fileSize - 1;

                if (hasRange) {
                    String[] parts = range.substring(6).split("-");
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                }

                if (end >= fileSize) {
                    end = fileSize - 1;
                }
                long contentLength = end - start + 1;

                // 4. 设置响应头
                response.setContentType("video/mp4");
                response.setHeader("Accept-Ranges", "bytes");
                response.setHeader("Content-Length", String.valueOf(contentLength));

                if (hasRange) {
                    response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                    response.setHeader("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize));
                } else {
                    response.setStatus(HttpServletResponse.SC_OK);
                }

                // 5. 设置起始偏移量并获取数据流
                client.setRestartOffset(start);
                try (InputStream in = client.retrieveFileStream(VIDEO_PATH)) {
                    if (in == null) {
                        log.warn("从 FTP 获取文件流失败: {}, {}", VIDEO_PATH, ftpReplySummary(client));
                        markFtpFailureHeaders(response, client);
                        return;
                    }

                    // 6. 流式输出到客户端
                    byte[] buffer = new byte[1024 * 64]; // 使用 64KB 缓冲区 (或替换为你的 BUFFER_SIZE)
                    long remaining = contentLength;
                    int read;
                    java.io.OutputStream out = response.getOutputStream();

                    while (remaining > 0 && (read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                        out.write(buffer, 0, read);
                        remaining -= read;
                    }
                    response.flushBuffer();

                    isNormalCompletion = true;
                }
            } finally {
                // 7. 安全资源释放逻辑 (修复关闭浏览器后文件被占用的问题)
                // 先处理 completePendingCommand / abort（close() 不会自动做这一步），随后 close() 负责登出与断开
                try {
                    if (isNormalCompletion) {
                        if (client.isConnected()) {
                            try { client.completePendingCommand(); } catch (Exception ignored) {}
                        }
                    } else {
                        if (client.isConnected()) {
                            try {
                                // 数据链路切断后，服务器的主线程恢复，此时再发 ABOR 命令就不会被阻塞了。
                                client.abort();
                            } catch (Exception ignored) {
                                log.debug("发送 FTP ABOR 命令: {}", ignored.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("处理 FTP 流关闭时发生异常", e);
                }
            }
        } catch (Exception e) {
            if (!isClientAbort(e)) {
                log.error("FTP mp4 streaming failed: ", e);
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            }
        }
    }

    private boolean isClientAbort(Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause.getClass().getName().equals("org.apache.catalina.connector.ClientAbortException")) return true;
            if (cause.getClass().getName().equals("org.springframework.web.context.request.async.AsyncRequestNotUsableException"))
                return true;
            if (cause instanceof IOException) {
                String msg = cause.getMessage();
                if (msg != null && (msg.contains("broken pipe") || msg.contains("connection reset") || msg.contains("abort"))) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 生成 FTP 客户端最近一次服务器应答的摘要。
     *
     * <p>retrieveFile / retrieveFileStream 只返回 true/false 或 null，失败的真实原因（文件被占用=450、
     * 服务器认为不存在或权限不足=550、传输中断=426 等）只存在于控制通道的应答码里。失败分支务必把应答码
     * 一并记录，否则多种失败会统一表现为「未找到」，无法定案。</p>
     *
     * @param client FTP 客户端
     * @return 例如 replyCode=550, replyString=550 File unavailable. (permission)
     */
    private String ftpReplySummary(AutoCloseableFTPClient client) {
        if (client == null || !client.isConnected()) {
            return "replyCode=(none), replyString=(client not connected)";
        }
        int code = client.getReplyCode();
        String reply = client.getReplyString();
        String collapsed = reply == null ? "(null)" : reply.trim().replaceAll("\\s+", " ");
        return "replyCode=" + code + ", replyString=" + (collapsed.isEmpty() ? "(empty)" : collapsed);
    }

    /**
     * 取流失败(流未打开)时，按 FTP 应答码映射恰当的 HTTP 状态。
     * 550/551/553 才是「不存在」（含权限不足=403），450/451/421 是忙=503，425/426/530 等是上游传输失败=502。
     * 一律 404 会把「图在但拿不出来」伪装成「图不存在」，重蹈「未找到」误报的覆辙。
     */
    private int ftpFailureHttpStatus(AutoCloseableFTPClient client) {
        if (client == null || !client.isConnected()) {
            return HttpServletResponse.SC_BAD_GATEWAY;
        }
        int code = client.getReplyCode();
        if (code == 550 || code == 551 || code == 553) {
            String reply = client.getReplyString();
            if (reply != null && reply.toLowerCase(Locale.ROOT).contains("denied")) {
                return HttpServletResponse.SC_FORBIDDEN;
            }
            return HttpServletResponse.SC_NOT_FOUND;
        }
        if (code == 450 || code == 451 || code == 421) {
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        }
        // 425/426/530/未知 → 上游 FTP 传输失败
        return HttpServletResponse.SC_BAD_GATEWAY;
    }

    /**
     * 该 FTP 应答是否值得重试：450/451/421/425/426 是瞬时可恢复（占用/忙/传输中断），550/553 是永久失败。
     */
    private boolean isFtpReplyRetryable(AutoCloseableFTPClient client) {
        if (client == null || !client.isConnected()) {
            return false;
        }
        int code = client.getReplyCode();
        return code == 450 || code == 451 || code == 421 || code == 425 || code == 426;
    }

    /**
     * 取流失败时：透出 FTP 原始应答与「是否可重试」到响应头，并设置按应答码分流的 HTTP 状态。
     * FTP 应答码是一对多，只回一个状态码会丢失重试决策所需的信息。
     */
    private void markFtpFailureHeaders(HttpServletResponse response, AutoCloseableFTPClient client) {
        if (response.isCommitted() || client == null || !client.isConnected()) {
            return;
        }
        // 清除先前可能设置的过期响应头（如预设的 Content-Type/Content-Length/Content-Range/200/206 状态），避免协议体长度失配
        response.reset();
        response.setHeader("X-Ftp-Reply", ftpReplySummary(client));
        response.setHeader("X-Ftp-Retryable", String.valueOf(isFtpReplyRetryable(client)));
        response.setStatus(ftpFailureHttpStatus(client));
    }

    @Operation(summary = "流式读取并显示 JPG 图片")
    @GetMapping("/streamjpg")
    public void streamJpg(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (AutoCloseableFTPClient client = new AutoCloseableFTPClient()) {
            client.connect(FTP_HOST, FTP_PORT);
            client.login(FTP_USER, FTP_PASS);
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);
            // 是否需要向服务端补发 ABOR（若流正常读完并完成 226/426 收尾，则置为 false）
            boolean needsAbort = true;
            try {
                try (InputStream in = client.retrieveImgSendFileStream(JPG_PATH)) {
                    if (in != null) {
                        String replyString = client.getReplyString();
                        // 解析宽高信息
                        int width = 0;
                        int height = 0;
                        if (replyString != null && replyString.startsWith("150")) {
                            // 解析格式: "150 width:height"
                            String[] parts = replyString.split(" ");
                            if (parts.length > 1) {
                                String metadata = parts[1];
                                String[] dimensions = metadata.split(":");
                                if (dimensions.length == 2) {
                                    try {
                                        width = Integer.parseInt(dimensions[0]);
                                        height = Integer.parseInt(dimensions[1]);
                                    } catch (NumberFormatException ignored) {
                                        // 解析失败，保持默认值
                                    }
                                }
                            }
                        }
                        response.setHeader("X-Image-Width", String.valueOf(width));
                        response.setHeader("X-Image-Height", String.valueOf(height));

                        // 将图片流写入响应
                        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
                        IOUtils.copy(in, response.getOutputStream());
                        response.flushBuffer();
                    } else {
                        log.warn("从 FTP 获取图片流失败(SITE_IMGSEND 未返回 150): {}, {}", JPG_PATH, ftpReplySummary(client));
                        markFtpFailureHeaders(response, client);
                        return;
                    }
                }

                if (client.isConnected()) {
                    // 流关闭后，调用 completePendingCommand 接收 226 响应
                    try {
                        if (!client.completePendingCommand()) {
                            log.warn("FTP 数据传输未收到 226 完成应答(可能传输中断): {}", ftpReplySummary(client));
                        }
                    } catch (IOException e) {
                        log.error("FTP completePendingCommand 异常: {}", ftpReplySummary(client), e);
                    }
                }
                // 控制通道已完成应答消费（无论 226 还是 426），服务端均已释放文件，无需再发 ABOR
                needsAbort = false;
            } finally {
                // 传输未正常收尾（如客户端中断）：发 ABOR 让服务器释放文件，避免被攥住
                if (needsAbort && client.isConnected()) {
                    try {
                        client.abort();
                    } catch (Exception ignored) {
                        log.debug("发送 FTP ABOR 命令失败: {}", ignored.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (!isClientAbort(e)) {
                log.error("FTP streaming failed: ", e);
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "FTP streaming failed");
                }
            }
        }
    }

    /**
     *
     * @param request
     * @param response
     * @param picPath
     * @throws IOException
     */
    @Operation(summary = "流式读取指定路径的 JPG 图片", description = "从 FTP 动态读取指定路径的图片资源流，并作为 jpeg 格式写回客户端响应。")
    @GetMapping("/streamdefaultjpg")
    public void streamDefaultJpg(HttpServletRequest request, HttpServletResponse response, @Parameter(description = "图片相对路径") @RequestParam String picPath) throws IOException {
        // 入参校验：拦截空/空白/路径穿越，避免任意 FTP 路径注入
        if (picPath == null || picPath.isBlank() || picPath.contains("..")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try (AutoCloseableFTPClient client = new AutoCloseableFTPClient()) {
            client.setControlEncoding("UTF-8");
            client.connect(FTP_HOST, FTP_PORT);
            client.login(FTP_USER, FTP_PASS);
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);
            // 是否需要向服务端补发 ABOR（若流正常读完并完成 226/426 收尾，则置为 false）
            boolean needsAbort = true;
            try {
                try (InputStream in = client.retrieveFileStream(picPath)) {
                    if (in != null) {
                        // 不要再转成 byte[]，利用包装流防止大图 OOM，同时让 ImageIO 自动判断格式
                        try (BufferedInputStream bis = new BufferedInputStream(in)) {
                            BufferedImage bufferedImage = ImageIO.read(bis);

                            if (bufferedImage != null) {
                                // 解决部分 CMYK 或 Alpha 通道写出 JPG 报错/变色的万能画板法
                                BufferedImage rgbImage = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);

                                Graphics2D g = rgbImage.createGraphics();
                                // 设置白色底色，防止带有透明通道的图片变成背景全黑
                                g.setColor(Color.WHITE);
                                g.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
                                // 绘制原图
                                g.drawImage(bufferedImage, 0, 0, null);
                                g.dispose();

                                // 输出为标准 JPG
                                response.setContentType(MediaType.IMAGE_JPEG_VALUE);
                                ImageIO.write(rgbImage, "jpg", response.getOutputStream());
                            } else {
                                // 下载成功但 ImageIO 解不出 → 上游内容问题，502 而非 415（415 是请求媒体类型问题）
                                // 必须 return：ImageIO.read 未读尽数据流，交由 finally 发 ABOR 释放，避免连接/文件状态残留
                                log.warn("从 FTP 下载成功但 ImageIO 无法解码: {}", picPath);
                                if (!response.isCommitted()) {
                                    response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                                }
                                return;
                            }
                        }
                    } else {
                        log.warn("从 FTP 获取图片流失败: {}, {}", picPath, ftpReplySummary(client));
                        markFtpFailureHeaders(response, client);
                        return;
                    }
                }

                if (client.isConnected()) {
                    // 流关闭后，调用 completePendingCommand 接收 226 响应
                    try {
                        if (!client.completePendingCommand()) {
                            log.warn("FTP 数据传输未收到 226 完成应答(可能传输中断): {}", ftpReplySummary(client));
                        }
                    } catch (IOException e) {
                        log.error("FTP completePendingCommand 异常: {}", ftpReplySummary(client), e);
                    }
                }
                // 控制通道已完成应答消费（无论 226 还是 426），服务端均已释放文件，无需再发 ABOR
                needsAbort = false;
            } finally {
                // 传输未正常收尾（如客户端中断）：发 ABOR 让服务器释放文件，避免被攥住
                if (needsAbort && client.isConnected()) {
                    try {
                        client.abort();
                    } catch (Exception ignored) {
                        log.debug("发送 FTP ABOR 命令失败: {}", ignored.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (!isClientAbort(e)) {
                log.error("FTP streaming failed: ", e);
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "FTP streaming failed");
                }
            }
        }
    }


}
