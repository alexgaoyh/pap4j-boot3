package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.autoclose.AutoCloseableFTPClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
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
        FTPClient metaClient = new FTPClient();
        try {
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
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        } finally {
            if (metaClient.isConnected()) {
                try {
                    metaClient.logout();
                } catch (IOException ignored) {
                    log.warn("FTP logout failed", ignored);
                }
                try {
                    metaClient.disconnect();
                } catch (IOException ignored) {
                    log.warn("FTP disconnect failed", ignored);
                }
            }
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
        FTPClient client = new FTPClient();
        InputStream in = null;

        try {
            client.connect(FTP_HOST, FTP_PORT);
            client.login(FTP_USER, FTP_PASS);
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);

            client.setRestartOffset(start);
            in = client.retrieveFileStream(VIDEO_PATH);

            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = contentLength;
            int read;

            while (remaining > 0 && (read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                response.getOutputStream().write(buffer, 0, read);
                remaining -= read;
            }
            response.flushBuffer();

        } catch (Exception e) {
            if (!isClientAbort(e)) {
                log.error("FTP streaming failed: ", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "FTP streaming failed");
            }
        } finally {
            // 必须先关闭数据输入流 (InputStream)
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    log.warn("Failed to close FTP InputStream", ignored);
                }
            }

            // 流关闭后，再调用 completePendingCommand 接收 226 响应
            if (client.isConnected()) {
                try {
                    client.completePendingCommand();
                } catch (IOException ignored) {
                    log.warn("Failed to complete pending command", ignored);
                }

                // 安全登出
                try {
                    client.logout();
                } catch (IOException ignored) {
                    log.warn("FTP logout failed", ignored);
                }

                // 安全断开 Socket 连接
                try {
                    client.disconnect();
                } catch (IOException ignored) {
                    log.warn("FTP disconnect failed", ignored);
                }
            }
        }
    }

    @Operation(summary = "流式读取播放 MP4 版本2 (推荐)", description = "改进版 FTP mp4 视频流式读取播放，采用更健壮的连接关闭和分块读取机制。")
    @GetMapping("/streammp42")
    public void streamMp42(HttpServletRequest request, HttpServletResponse response) throws IOException {

        FTPClient client = new FTPClient();
        InputStream in = null;
        boolean isNormalCompletion = false;

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
                log.warn("FTP 服务器不支持 SIZE 命令或文件不存在: {}", VIDEO_PATH);

                FTPFile[] files = client.listFiles(VIDEO_PATH);
                if (files == null || files.length == 0) {
                    log.warn("通过 listFiles 亦未找到文件: {}", VIDEO_PATH);
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
            in = client.retrieveFileStream(VIDEO_PATH);

            if (in == null) {
                log.warn("从 FTP 获取文件流失败: {}", VIDEO_PATH);
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
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

        } catch (Exception e) {
            if (!isClientAbort(e)) {
                log.error("FTP mp4 streaming failed: ", e);
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            }
        } finally {
            // 7. 安全资源释放逻辑 (修复关闭浏览器后文件被占用的问题)
            try {
                if (isNormalCompletion) {
                    if (in != null) {
                        try { in.close(); } catch (Exception ignored) {}
                    }
                    if (client.isConnected()) {
                        try { client.completePendingCommand(); } catch (Exception ignored) {}
                    }
                } else {
                    // 【核心修复】：如果是用户取消请求，必须先 close 切断数据链路，再 abort
                    if (in != null) {
                        try {
                            // 1. 先强行关闭数据流。这会导致底层 Socket 立即关闭。
                            // FTP 服务器试图继续发送数据时会触发 Broken Pipe 异常，从而释放文件锁。
                            in.close();
                        } catch (Exception ignored) {
                            log.debug("强制关闭 FTP 数据流: {}", ignored.getMessage());
                        }
                    }
                    if (client.isConnected()) {
                        try {
                            // 2. 数据链路切断后，服务器的主线程恢复，此时再发 ABOR 命令就不会被阻塞了。
                            client.abort();
                        } catch (Exception ignored) {
                            log.debug("发送 FTP ABOR 命令: {}", ignored.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("处理 FTP 流关闭时发生异常", e);
            } finally {
                // 无论发生什么情况，确保最终彻底断开连接 (直接断开 TCP 连接，这是释放远端文件锁的最终保底手段)
                if (client != null && client.isConnected()) {
                    try {
                        client.logout();
                    } catch (IOException ignored) {}
                    try {
                        client.disconnect();
                    } catch (IOException ignored) {}
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

    @Operation(summary = "流式读取并显示 JPG 图片")
    @GetMapping("/streamjpg")
    public void streamJpg(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        AutoCloseableFTPClient client = new AutoCloseableFTPClient();
        InputStream in = null;
        try {
            client.connect(FTP_HOST, FTP_PORT);
            client.login(FTP_USER, FTP_PASS);
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);
            in = client.retrieveImgSendFileStream(JPG_PATH);
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
                IOUtils.copy(in, response.getOutputStream());
                response.flushBuffer();
            } else {
                // 如果在 FTP 上找不到图片，返回 404
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }

        } catch (Exception e) {
            if (!isClientAbort(e)) {
                log.error("FTP streaming failed: ", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "FTP streaming failed");
            }
        } finally {
            // 先关闭 InputStream
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    log.warn("Failed to close FTP InputStream", ignored);
                }
            }

            if (client.isConnected()) {
                // 流关闭后，调用 completePendingCommand 接收 226 响应
                try {
                    client.completePendingCommand();
                } catch (IOException ignored) {
                    log.warn("Failed to complete pending command", ignored);
                }

                // 安全登出
                try {
                    client.logout();
                } catch (IOException ignored) {
                    log.warn("FTP logout failed", ignored);
                }

                // 安全断开 Socket 连接
                try {
                    client.disconnect();
                } catch (IOException ignored) {
                    log.warn("FTP disconnect failed", ignored);
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
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        AutoCloseableFTPClient client = new AutoCloseableFTPClient();
        InputStream in = null;
        try {
            client.setControlEncoding("UTF-8");
            client.connect(FTP_HOST, FTP_PORT);
            client.login(FTP_USER, FTP_PASS);
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);
            in = client.retrieveFileStream(picPath);
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
                        ImageIO.write(rgbImage, "jpg", response.getOutputStream());
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
                    }
                }
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            if (!isClientAbort(e)) {
                log.error("FTP streaming failed: ", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "FTP streaming failed");
            }
        } finally {
            // 先关闭 InputStream
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    log.warn("Failed to close FTP InputStream", ignored);
                }
            }
            if (client.isConnected()) {
                // 流关闭后，调用 completePendingCommand 接收 226 响应
                try {
                    client.completePendingCommand();
                } catch (IOException ignored) {
                    log.warn("Failed to complete pending command", ignored);
                }

                // 安全登出
                try {
                    client.logout();
                } catch (IOException ignored) {
                    log.warn("FTP logout failed", ignored);
                }
                // 安全断开 Socket 连接
                try {
                    client.disconnect();
                } catch (IOException ignored) {
                    log.warn("FTP disconnect failed", ignored);
                }
            }
        }
    }


}
