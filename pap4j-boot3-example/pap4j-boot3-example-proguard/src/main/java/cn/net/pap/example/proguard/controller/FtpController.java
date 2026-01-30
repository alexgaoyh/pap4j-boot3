package cn.net.pap.example.proguard.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/ftp")
public class FtpController {

    private static final int BUFFER_SIZE = 1024 * 512;

    private static final String FTP_HOST = "127.0.0.1";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "bj";
    private static final String FTP_PASS = "123456";
    private static final String VIDEO_PATH = "test.mp4";

    /**
     * ftp mp4 stream range
     *
     * @param request
     * @param response
     * @throws IOException
     */
    @GetMapping("/streammp4")
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
                    metaClient.disconnect();
                } catch (IOException ignored) {
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

            client.completePendingCommand();

        } catch (Exception e) {
            if (!isClientAbort(e)) {
                e.printStackTrace(); // 可以捕获异常
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "FTP streaming failed");
            }
        } finally {
            if (in != null) try {
                in.close();
            } catch (IOException ignored) {
            }
            if (client.isConnected()) try {
                client.disconnect();
            } catch (IOException ignored) {
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


}
