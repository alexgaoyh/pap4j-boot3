package cn.net.pap.example.ftp.server.command;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.DataConnection;
import org.apache.ftpserver.ftplet.DataConnectionFactory;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;
import org.apache.ftpserver.impl.IODataConnectionFactory;
import org.apache.ftpserver.impl.LocalizedDataTransferFtpReply;
import org.apache.ftpserver.impl.LocalizedFtpReply;
import org.apache.ftpserver.impl.ServerFtpStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * 单元测试详见： pap-bean-ftp-starter ->  com.pap.ftp.ftp.AutoCloseableFTPClientTest
 *
 *  public class AutoCloseableFTPClient extends FTPClient implements AutoCloseable {
 *     public InputStream retrieveImgSendFileStream(String remote) throws IOException {
 *         return this._retrieveFileStream("SITE_IMGSEND", remote);
 *     }
 *     @Override
 *     public void close() {
 *         try {
 *             if (super.isConnected() && super.isAvailable()) {
 *                 logout();
 *             }
 *         } catch (IOException e) {
 *         } finally {
 *             try {
 *                 disconnect();
 *             } catch (IOException e) {
 *             }
 *         }
 *     }
 * }
 *
 *     @Test
 *     @Order(9)
 *     public void testImgSend() throws Exception {
 *         AutoCloseableFTPClient ftpClient = new AutoCloseableFTPClient();
 *         try {
 *             ftpClient.setControlEncoding("UTF-8");
 *             ftpClient.enterLocalPassiveMode();
 *             ftpClient.connect(FTP_SERVER, FTP_PORT);
 *             ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
 *             boolean success = ftpClient.login(FTP_USER, FTP_PASSWORD);
 *
 *             try (InputStream inputStream = ftpClient.retrieveImgSendFileStream("big-plane-no.jpg")) {
 *                 ByteArrayOutputStream baos = new ByteArrayOutputStream();
 *                 byte[] buffer = new byte[4096];
 *                 int bytesRead;
 *                 while ((bytesRead = inputStream.read(buffer)) != -1) {
 *                     baos.write(buffer, 0, bytesRead);
 *                 }
 *
 *                 byte[] imageData = baos.toByteArray();
 *                 System.out.println("Received image size: " + imageData.length + " bytes");
 *
 *                 try (ByteArrayInputStream bais = new ByteArrayInputStream( Base64.getDecoder().decode(imageData))) {
 *                     BufferedImage image = ImageIO.read(bais);
 *                     System.out.println(image);
 *                 }
 *             }
 *
 *             boolean completed = ftpClient.completePendingCommand();
 *             if (!completed) {
 *                 throw new RuntimeException("Failed to complete pending command");
 *             }
 *
 *         } finally {
 *             if (ftpClient.isConnected()) {
 *                 ftpClient.disconnect();
 *             }
 *         }
 *     }
 */

public class ImgSendCommand extends AbstractCommand {
    private final Logger LOG = LoggerFactory.getLogger(ImgSendCommand.class);

    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) throws IOException, FtpException {
        try {
            String fileName = request.getArgument();
            if (fileName == null) {
                session.write(LocalizedDataTransferFtpReply.translate(session, request, context, 501, "IMGSEND", (String)null, (FtpFile)null));
                return;
            }

            FtpFile file = null;

            try {
                file = session.getFileSystemView().getFile(fileName);
            } catch (Exception var39) {
                Exception ex = var39;
                this.LOG.debug("Exception getting file object", ex);
            }

            if (file == null) {
                session.write(LocalizedDataTransferFtpReply.translate(session, request, context, 550, "IMGSEND.missing", fileName, file));
                return;
            }

            fileName = file.getAbsolutePath();
            if (!file.doesExist()) {
                session.write(LocalizedDataTransferFtpReply.translate(session, request, context, 550, "IMGSEND.missing", fileName, file));
                return;
            }

            if (!file.isFile()) {
                session.write(LocalizedDataTransferFtpReply.translate(session, request, context, 550, "IMGSEND.invalid", fileName, file));
                return;
            }

            if (!file.isReadable()) {
                session.write(LocalizedDataTransferFtpReply.translate(session, request, context, 550, "IMGSEND.permission", fileName, file));
                return;
            }

            DataConnectionFactory connFactory = session.getDataConnection();
            if (connFactory instanceof IODataConnectionFactory) {
                InetAddress address = ((IODataConnectionFactory)connFactory).getInetAddress();
                if (address == null) {
                    session.write(new DefaultFtpReply(503, "PORT or PASV must be issued first"));
                    return;
                }
            }

            session.write(LocalizedFtpReply.translate(session, request, context, 150, "IMGSEND", (String)null));
            boolean failure = false;

            DataConnection dataConnection;
            try {
                dataConnection = session.getDataConnection().openConnection();
            } catch (Exception var43) {
                Exception e = var43;
                this.LOG.debug("Exception getting the output data stream", e);
                session.write(LocalizedDataTransferFtpReply.translate(session, request, context, 425, "IMGSEND", (String)null, file));
                return;
            }

            long transSz = 0L;

            try {
                InputStream is = this.convert(session.getUser().getHomeDirectory() + File.separator + fileName, 1080);
                Throwable var14 = null;

                try {
                    transSz = dataConnection.transferToClient(session.getFtpletSession(), is);
                    if (is != null) {
                        is.close();
                    }

                    this.LOG.info("File downloaded {}", fileName);
                    ServerFtpStatistics ftpStat = (ServerFtpStatistics)context.getFtpStatistics();
                    if (ftpStat != null) {
                        ftpStat.setDownload(session, file, transSz);
                    }
                } catch (Throwable var38) {
                    var14 = var38;
                    throw var38;
                } finally {
                    if (is != null) {
                        if (var14 != null) {
                            try {
                                is.close();
                            } catch (Throwable var37) {
                                var14.addSuppressed(var37);
                            }
                        } else {
                            is.close();
                        }
                    }

                }
            } catch (SocketException var41) {
                SocketException ex = var41;
                this.LOG.debug("Socket exception during data transfer", ex);
                failure = true;
                session.write(LocalizedDataTransferFtpReply.translate(session, request, context, 426, "IMGSEND", fileName, file, transSz));
            } catch (IOException var42) {
                IOException ex = var42;
                this.LOG.debug("IOException during data transfer", ex);
                failure = true;
                session.write(LocalizedDataTransferFtpReply.translate(session, request, context, 551, "IMGSEND", fileName, file, transSz));
            }

            if (!failure) {
                session.write(LocalizedDataTransferFtpReply.translate(session, request, context, 226, "IMGSEND", fileName, file, transSz));
            }
        } finally {
            session.resetState();
            session.getDataConnection().closeDataConnection();
        }

    }

    public static InputStream convert(String inputFileStr, int targetWidth) throws IOException {
        BufferedImage image = getLowMemoryThumbnail(inputFileStr, targetWidth);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, getFormatName(inputFileStr), baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return new ByteArrayInputStream(base64.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 相比于前面的方法，不再返回 base64，因为 base64 会放大，所以下面这个方法，是直接返回流，然后同样可以解析使用。
     * 单元测试方法如下，直接子采样图像之后返回。
     * @Test
     *     @Order(9)
     *     public void testImgSend() throws Exception {
     *         AutoCloseableFTPClient ftpClient = new AutoCloseableFTPClient();
     *         try {
     *             ftpClient.setControlEncoding("UTF-8");
     *             ftpClient.enterLocalPassiveMode();
     *             ftpClient.connect(FTP_SERVER, FTP_PORT);
     *             ftpClient.enterLocalPassiveMode(); // 关键
     *             boolean success = ftpClient.login(FTP_USER, FTP_PASSWORD);
     *             // IMPORTANT , need after login method
     *             ftpClient.setFileType(BINARY_FILE_TYPE);
     *
     *             try (InputStream rawStream = ftpClient.retrieveImgSendFileStream("/00035_00.jpg")) {
     *                 if (rawStream == null) {
     *                     throw new IOException("Cannot open input stream. Check FTP path or permissions.");
     *                 }
     *                 String desktop = System.getProperty("user.home") + File.separator + "Desktop";
     *                 java.nio.file.Path targetPath = java.nio.file.Paths.get(desktop, "aaaaaaaaaa.jpg");
     *                 java.nio.file.Files.createDirectories(targetPath.getParent());
     *                 try (java.io.OutputStream outputStream = java.nio.file.Files.newOutputStream(targetPath)) {
     *                     byte[] buffer = new byte[4096];
     *                     int bytesRead;
     *                     while ((bytesRead = rawStream.read(buffer)) != -1) {
     *                         outputStream.write(buffer, 0, bytesRead);
     *                     }
     *                 }
     *                 boolean completed = ftpClient.completePendingCommand();
     *                 if (!completed) {
     *                     throw new IOException("File transfer failed, incomplete command.");
     *                 }
     *             }
     *
     *         } finally {
     *             if (ftpClient.isConnected()) {
     *                 ftpClient.disconnect();
     *             }
     *         }
     *     }
     *
     * @param inputFileStr
     * @param targetWidth
     * @return
     * @throws IOException
     */
    public static InputStream convertOrigin(String inputFileStr, int targetWidth) throws IOException {
        BufferedImage image = getLowMemoryThumbnail(inputFileStr, targetWidth);

        String formatName = getFormatName(inputFileStr);
        if (formatName == null || formatName.isEmpty()) {
            throw new IllegalArgumentException("Invalid image format for file: " + inputFileStr);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, formatName, baos);
        // 直接返回原始二进制流
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private static String getFormatName(String inputFileStr) {
        int idx = inputFileStr.lastIndexOf('.');
        if (idx < 0 || idx == inputFileStr.length() - 1) {
            throw new IllegalArgumentException("Invalid image file name: " + inputFileStr);
        }
        return inputFileStr.substring(idx + 1).toLowerCase();
    }

    public static BufferedImage getLowMemoryThumbnail(String inputFileStr, int targetWidth) {
        File file = new File(inputFileStr);
        if (file == null || !file.exists()) {
            return null;
        }

        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            if (iis == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int actualWidth = reader.getWidth(0);
                int sampling = Math.max(actualWidth / targetWidth, 1);

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(sampling, sampling, 0, 0);

                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

}
