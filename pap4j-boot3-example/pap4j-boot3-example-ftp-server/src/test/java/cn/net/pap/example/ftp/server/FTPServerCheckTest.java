//package cn.net.pap.example.ftp.server;
//
//import org.apache.commons.net.ftp.FTP;
//import org.apache.commons.net.ftp.FTPClient;
//import org.apache.commons.net.ftp.FTPFile;
//import org.apache.commons.net.ftp.FTPReply;
//import org.junit.After;
//import org.junit.Before;
//import org.junit.Test;
//import org.springframework.core.annotation.Order;
//
//import java.io.*;
//import java.nio.charset.StandardCharsets;
//
//public class FTPServerCheckTest {
//
//    private static final String FTP_SERVER = "127.0.0.1";
//    private static final int FTP_PORT = 2121;
//    private static final String FTP_USER = "admin";
//    private static final String FTP_PASSWORD = "123456";
//    private static final String TEST_DIR = "中文";
//    private static final String TEST_FILE = "中文文件.txt";
//    private static final String TEST_FILE_CONTENT = "This is a test file content.";
//
//    private static FTPClient ftpClient;
//
//    @Before
//    public void setUp() throws Exception {
//        try {
//            ftpClient = new FTPClient();
//            // 设置控制连接编码为UTF-8
//            ftpClient.setControlEncoding("UTF-8");
//            // 启用被动模式，有助于解决防火墙后的问题
//            ftpClient.enterLocalPassiveMode();
//
//            ftpClient.connect(FTP_SERVER, FTP_PORT);
//            boolean success = ftpClient.login(FTP_USER, FTP_PASSWORD);
//            if (!success) {
//                throw new RuntimeException("FTP登录失败");
//            }
//
//            // 验证服务器是否支持UTF-8
//            if (FTPReply.isPositiveCompletion(ftpClient.sendCommand("OPTS UTF8", "ON"))) {
//                System.out.println("FTP服务器支持UTF-8编码");
//            } else {
//                System.out.println("FTP服务器不支持UTF-8编码，可能遇到中文问题");
//            }
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @After
//    public void tearDown() throws Exception {
//        if (ftpClient.isConnected()) {
//            ftpClient.logout();
//            ftpClient.disconnect();
//        }
//    }
//
//    @Test
//    @Order(1)
//    public void testCreateDirectory() throws IOException {
//        // 创建目录
//        boolean success = ftpClient.makeDirectory(TEST_DIR);
//
//        // 验证目录是否存在
//        FTPFile[] files = ftpClient.listDirectories();
//        boolean dirExists = false;
//        for (FTPFile file : files) {
//            if (file.getName().equals(TEST_DIR) && file.isDirectory()) {
//                dirExists = true;
//                break;
//            }
//        }
//    }
//
//    @Test
//    @Order(2)
//    public void testUploadFile() throws IOException {
//        // 切换到测试目录
//        boolean changed = ftpClient.changeWorkingDirectory(TEST_DIR);
//
//        // 设置二进制传输模式
//        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
//
//        // 创建测试文件内容
//        InputStream inputStream = new ByteArrayInputStream(TEST_FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
//
//        // 上传文件
//        boolean success = ftpClient.storeFile(TEST_FILE, inputStream);
//        inputStream.close();
//
//        // 验证文件是否存在
//        FTPFile[] files = ftpClient.listFiles();
//        boolean fileExists = false;
//        for (FTPFile file : files) {
//            if (file.getName().equals(TEST_FILE)) {
//                fileExists = true;
//                break;
//            }
//        }
//        // 返回根目录
//        ftpClient.changeToParentDirectory();
//    }
//
//    @Test
//    @Order(3)
//    public void testDownloadFile() throws IOException {
//        // 切换到测试目录
//        boolean changed = ftpClient.changeWorkingDirectory(TEST_DIR);
//
//        // 下载文件
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//        boolean success = ftpClient.retrieveFile(TEST_FILE, outputStream);
//
//        // 验证文件内容
//        String downloadedContent = outputStream.toString(StandardCharsets.UTF_8.name());
//        outputStream.close();
//
//        // 返回根目录
//        ftpClient.changeToParentDirectory();
//    }
//
//    @Test
//    @Order(4)
//    public void testListFiles() throws IOException {
//        // 切换到测试目录
//        boolean changed = ftpClient.changeWorkingDirectory(TEST_DIR);
//
//        // 获取文件列表
//        FTPFile[] files = ftpClient.listFiles();
//
//        // 验证测试文件在列表中
//        boolean fileFound = false;
//        for (FTPFile file : files) {
//            if (file.getName().equals(TEST_FILE)) {
//                fileFound = true;
//                break;
//            }
//        }
//        // 返回根目录
//        ftpClient.changeToParentDirectory();
//    }
//
//    @Test
//    @Order(5)
//    public void testDeleteFile() throws IOException {
//        // 切换到测试目录
//        boolean changed = ftpClient.changeWorkingDirectory(TEST_DIR);
//
//        // 删除文件
//        boolean success = ftpClient.deleteFile(TEST_FILE);
//
//        // 验证文件是否被删除
//        FTPFile[] files = ftpClient.listFiles();
//        boolean fileExists = false;
//        for (FTPFile file : files) {
//            if (file.getName().equals(TEST_FILE)) {
//                fileExists = true;
//                break;
//            }
//        }
//        // 返回根目录
//        ftpClient.changeToParentDirectory();
//    }
//
//    @Test
//    @Order(6)
//    public void testRemoveDirectory() throws IOException {
//        // 删除目录
//        boolean success = ftpClient.removeDirectory(TEST_DIR);
//
//        // 验证目录是否被删除
//        FTPFile[] directories = ftpClient.listDirectories();
//        boolean dirExists = false;
//        for (FTPFile dir : directories) {
//            if (dir.getName().equals(TEST_DIR)) {
//                dirExists = true;
//                break;
//            }
//        }
//    }
//
//}