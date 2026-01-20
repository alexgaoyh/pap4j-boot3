# [杂谈]ImgSendCommand 应用

## 浏览器渲染图像

&ensp;&ensp; 如下方法是调用 IMGSEND 处理完图像之后，浏览器直接展示图像信息，需要修改的地方是 ImgSendCommand.java 需要调用 convertOrigin， 而不是 convert， 直接返回图像，而不是 base64

```java
package cn.net.pap.example.admin.config;

import org.apache.commons.net.ftp.FTPClient;

import java.io.IOException;
import java.io.InputStream;

/**
 * 可自动关闭的FTP客户端
 * 继承FTPClient并实现AutoCloseable接口
 * 使用方式：try (AutoCloseableFTPClient ftp = new AutoCloseableFTPClient()) { ... }
 */
public class AutoCloseableFTPClient extends FTPClient implements AutoCloseable {

    public InputStream retrieveImgSendFileStream(String remote) throws IOException {
        return this._retrieveFileStream("SITE_IMGSEND", remote);
    }

    /**
     * 实现AutoCloseable接口，自动关闭连接 在try-with-resources结束时自动调用
     */
    @Override
    public void close() {
        try {
            if (super.isConnected() && super.isAvailable()) {
                logout();
            }
        } catch (IOException e) {
            // todo 忽略登出异常
        } finally {
            try {
                disconnect();
            } catch (IOException e) {
            }
        }
    }
}

```

```java
package cn.net.pap.example.admin.controller;

import cn.net.pap.example.admin.config.AutoCloseableFTPClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;

import static org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE;

@Controller
public class FtpController {

    @RequestMapping("view")
    public StreamingResponseBody view(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);

        AutoCloseableFTPClient ftp = null;

        try {
            ftp = new AutoCloseableFTPClient();
            ftp.setControlEncoding("UTF-8");
            ftp.enterLocalPassiveMode();
            ftp.connect("192.168.1.66", 21);
            boolean success = ftp.login("bj", "123456");

            if (FTPReply.isPositiveCompletion(ftp.sendCommand("OPTS UTF8", "ON"))) {
                System.out.println("FTP服务器支持UTF-8编码");
            } else {
                System.out.println("FTP服务器不支持UTF-8编码，可能遇到中文问题");
            }
            ftp.setFileType(BINARY_FILE_TYPE);

            InputStream rawStream = ftp.retrieveImgSendFileStream("600-jpg-plane-no.jpg");
            boolean completed = ftp.completePendingCommand();

            final InputStream finalStream = rawStream;

            return outputStream -> {
                try (InputStream is = rawStream; OutputStream os = outputStream) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        os.write(buffer, 0, n);
                    }
                    os.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                    // 异常时返回空图像
                    writeEmptyImage(outputStream);
                } finally {
                }
            };

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            // 异常情况下返回空图像
            return outputStream -> writeEmptyImage(outputStream);
        } finally {
            if (ftp != null) {
                try {
                    ftp.logout();
                } catch (Exception ignored) {
                }
                try {
                    ftp.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // 生成 1x1 空白图像
    private void writeEmptyImage(OutputStream outputStream) {
        try {
            BufferedImage empty = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = empty.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1, 1);
            g.dispose();
            ImageIO.write(empty, "jpg", outputStream);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


}

```