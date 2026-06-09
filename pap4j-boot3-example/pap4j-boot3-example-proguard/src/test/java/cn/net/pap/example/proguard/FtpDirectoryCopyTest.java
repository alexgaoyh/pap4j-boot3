package cn.net.pap.example.proguard;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FTP Directory Copy Unit Test.
 */
public class FtpDirectoryCopyTest {

    private static final Logger log = LoggerFactory.getLogger(FtpDirectoryCopyTest.class);

    private static final String FTP_HOST = "127.0.0.1";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "bj";
    private static final String FTP_PASS = "123456";

    private FTPClient createFtpClient() throws IOException {
        FTPClient client = new FTPClient();
        try {
            client.setControlEncoding("UTF-8");
            client.connect(FTP_HOST, FTP_PORT);
            boolean success = client.login(FTP_USER, FTP_PASS);
            if (!success) {
                client.disconnect();
                log.error("ftp server not running!");
            }
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);
        } catch (IOException e) {
            log.error("ftp server not running!");
            return null;
        }
        return client;
    }

    private void disconnectQuietly(FTPClient client) {
        if (client != null && client.isConnected()) {
            try {
                client.logout();
            } catch (IOException ignored) {
                log.warn("FTP logout failed");
            }
            try {
                client.disconnect();
            } catch (IOException ignored) {
                log.warn("FTP disconnect failed");
            }
        }
    }

    private void makeDirectoryRecursive(FTPClient client, String path) throws IOException {
        client.changeWorkingDirectory("/");
        String[] dirs = path.split("/");
        StringBuilder current = new StringBuilder();
        for (String dir : dirs) {
            if (dir.isEmpty()) {
                continue;
            }
            current.append("/").append(dir);
            client.makeDirectory(current.toString());
        }
    }

    private void deleteDirectoryRecursive(FTPClient client, String path) throws IOException {
        if (client == null) {
            return;
        }
        client.changeWorkingDirectory("/");
        FTPFile[] files = client.listFiles(path);
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
                deleteDirectoryRecursive(client, subPath);
            } else {
                client.deleteFile(subPath);
            }
        }
        client.removeDirectory(path);
    }

    private void uploadFile(FTPClient client, String path, String content) throws IOException {
        client.changeWorkingDirectory("/");
        try (OutputStream out = client.storeFileStream(path)) {
            if (out != null) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
        }
        client.completePendingCommand();
    }

    @BeforeEach
    public void setUp() throws Exception {
        FTPClient client = createFtpClient();
        if (client == null) {
            return;
        }
        try {
            deleteDirectoryRecursive(client, "/copy_test_src");
            deleteDirectoryRecursive(client, "/copy_test_dst_single");
            deleteDirectoryRecursive(client, "/copy_test_dst_two_clients");

            makeDirectoryRecursive(client, "/copy_test_src/sub1");
            makeDirectoryRecursive(client, "/copy_test_src/sub2/sub2_1");

            uploadFile(client, "/copy_test_src/fileRoot.txt", "root file content");
            uploadFile(client, "/copy_test_src/sub1/fileSub1.txt", "sub1 file content");
            uploadFile(client, "/copy_test_src/sub2/sub2_1/fileSub2.txt", "sub2 file content");
        } finally {
            disconnectQuietly(client);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        FTPClient client = createFtpClient();
        if (client == null) {
            return;
        }
        try {
            deleteDirectoryRecursive(client, "/copy_test_src");
            deleteDirectoryRecursive(client, "/copy_test_dst_single");
            deleteDirectoryRecursive(client, "/copy_test_dst_two_clients");
        } finally {
            disconnectQuietly(client);
        }
    }

    private void copyRecursiveSingleClient(FTPClient client, String srcPath, String destPath) throws IOException {
        client.changeWorkingDirectory("/");
        FTPFile[] files = client.listFiles(srcPath);
        if (files == null) {
            return;
        }
        for (FTPFile file : files) {
            String name = file.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String subSrcPath = srcPath + "/" + name;
            String subDestPath = destPath + "/" + name;
            if (file.isDirectory()) {
                client.makeDirectory(subDestPath);
                copyRecursiveSingleClient(client, subSrcPath, subDestPath);
            } else if (file.isFile()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                boolean retrieveSuccess = client.retrieveFile(subSrcPath, bos);
                if (retrieveSuccess) {
                    byte[] bytes = bos.toByteArray();
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
                        client.storeFile(subDestPath, bis);
                    }
                }
            }
        }
    }

    @Test
    public void testCopyUsingSingleClient() throws IOException {
        FTPClient client = createFtpClient();
        try {
            if (client == null) {
                return;
            }
            client.makeDirectory("/copy_test_dst_single");
            copyRecursiveSingleClient(client, "/copy_test_src", "/copy_test_dst_single");

            assertTrue(verifyDirectoriesEqual(client, "/copy_test_src", "/copy_test_dst_single"),
                    "Copied structure using single client should be equivalent to source");
        } finally {
            disconnectQuietly(client);
        }
    }

    private void copyRecursiveTwoClients(FTPClient srcClient, FTPClient destClient, String srcPath, String destPath) throws IOException {
        srcClient.changeWorkingDirectory("/");
        destClient.changeWorkingDirectory("/");
        FTPFile[] files = srcClient.listFiles(srcPath);
        if (files == null) {
            return;
        }
        for (FTPFile file : files) {
            String name = file.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String subSrcPath = srcPath + "/" + name;
            String subDestPath = destPath + "/" + name;
            if (file.isDirectory()) {
                destClient.makeDirectory(subDestPath);
                copyRecursiveTwoClients(srcClient, destClient, subSrcPath, subDestPath);
            } else if (file.isFile()) {
                try (InputStream in = srcClient.retrieveFileStream(subSrcPath);
                     OutputStream out = destClient.storeFileStream(subDestPath)) {
                    if (in != null && out != null) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
                srcClient.completePendingCommand();
                destClient.completePendingCommand();
            }
        }
    }

    @Test
    public void testCopyUsingTwoClients() throws IOException {
        FTPClient srcClient = createFtpClient();
        FTPClient destClient = createFtpClient();
        if (srcClient == null || destClient == null) {
            return;
        }
        try {
            destClient.makeDirectory("/copy_test_dst_two_clients");
            copyRecursiveTwoClients(srcClient, destClient, "/copy_test_src", "/copy_test_dst_two_clients");

            assertTrue(verifyDirectoriesEqual(srcClient, "/copy_test_src", "/copy_test_dst_two_clients"),
                    "Copied structure using two clients should be equivalent to source");
        } finally {
            disconnectQuietly(srcClient);
            disconnectQuietly(destClient);
        }
    }

    @Test
    public void testSameClientSimultaneousCopyFails() throws IOException {
        FTPClient client = createFtpClient();
        if (client == null) {
            return;
        }
        try {
            String srcFile = "/copy_test_src/fileRoot.txt";
            String destFile = "/copy_test_dst_single/fileRoot_failed.txt";

            client.makeDirectory("/copy_test_dst_single");

            InputStream in = null;
            OutputStream out = null;
            try {
                in = client.retrieveFileStream(srcFile);
                assertNotNull(in, "retrieveFileStream should successfully return a stream");

                out = client.storeFileStream(destFile);

                assertNull(out, "storeFileStream must return null on the same client before completing the pending retrieve command");
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                    client.completePendingCommand();
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                    client.completePendingCommand();
                }
            }
        } finally {
            disconnectQuietly(client);
        }
    }

    private void copyRecursiveSingleClientStreams(FTPClient client, String srcPath, String destPath) throws IOException {
        client.changeWorkingDirectory("/");
        FTPFile[] files = client.listFiles(srcPath);
        if (files == null) {
            return;
        }
        for (FTPFile file : files) {
            String name = file.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String subSrcPath = srcPath + "/" + name;
            String subDestPath = destPath + "/" + name;
            if (file.isDirectory()) {
                client.makeDirectory(subDestPath);
                copyRecursiveSingleClientStreams(client, subSrcPath, subDestPath);
            } else if (file.isFile()) {
                byte[] fileContent;
                // 1. 打开并直接读取源文件全部字节，然后关闭流
                try (InputStream in = client.retrieveFileStream(subSrcPath)) {
                    assertNotNull(in, "retrieveFileStream 应该成功返回输入流");
                    fileContent = in.readAllBytes();
                }
                // 2. 必须接收 226 完成状态以复位控制通道
                boolean retrieveComplete = client.completePendingCommand();
                assertTrue(retrieveComplete, "读取文件的挂起命令应当执行完成且成功");

                // 3. 在同一客户端控制连接空闲后，安全地打开写入流进行存储
                try (OutputStream out = client.storeFileStream(subDestPath)) {
                    assertNotNull(out, "在读取流关闭并完成挂起命令后，同一客户端的 storeFileStream 应该能成功打开");
                    out.write(fileContent);
                }
                // 4. 必须接收 226 完成状态以复位控制通道
                boolean storeComplete = client.completePendingCommand();
                assertTrue(storeComplete, "写入文件的挂起命令应当执行完成且成功");
            }
        }
    }

    @Test
    public void testSameClientSequentialCopySucceeds() throws IOException {
        FTPClient client = createFtpClient();
        if (client == null) {
            return;
        }
        try {
            makeDirectoryRecursive(client, "/copy_test_dst_single/sequential");
            copyRecursiveSingleClientStreams(client, "/copy_test_src", "/copy_test_dst_single/sequential");

            assertTrue(verifyDirectoriesEqual(client, "/copy_test_src", "/copy_test_dst_single/sequential"),
                    "顺序流式拷贝后的目录结构与内容应该与源目录一致");
        } finally {
            disconnectQuietly(client);
        }
    }

    private boolean verifyDirectoriesEqual(FTPClient client, String path1, String path2) throws IOException {
        client.changeWorkingDirectory("/");
        FTPFile[] files1 = client.listFiles(path1);
        FTPFile[] files2 = client.listFiles(path2);
        if (files1 == null || files2 == null) {
            return files1 == files2;
        }

        List<FTPFile> list1 = filterFiles(files1);
        List<FTPFile> list2 = filterFiles(files2);
        if (list1.size() != list2.size()) {
            return false;
        }

        list1.sort(Comparator.comparing(FTPFile::getName));
        list2.sort(Comparator.comparing(FTPFile::getName));
        for (int i = 0; i < list1.size(); i++) {
            FTPFile f1 = list1.get(i);
            FTPFile f2 = list2.get(i);
            if (f1.isDirectory() != f2.isDirectory()) {
                return false;
            }
            if (!f1.getName().equals(f2.getName())) {
                return false;
            }
            String sub1 = path1 + "/" + f1.getName();
            String sub2 = path2 + "/" + f2.getName();
            if (f1.isDirectory()) {
                if (!verifyDirectoriesEqual(client, sub1, sub2)) {
                    return false;
                }
            } else {
                if (f1.getSize() != f2.getSize()) {
                    return false;
                }
                byte[] bytes1 = downloadBytes(client, sub1);
                byte[] bytes2 = downloadBytes(client, sub2);
                if (!Arrays.equals(bytes1, bytes2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<FTPFile> filterFiles(FTPFile[] files) {
        List<FTPFile> list = new ArrayList<>();
        for (FTPFile file : files) {
            String name = file.getName();
            if (!".".equals(name) && !"..".equals(name)) {
                list.add(file);
            }
        }
        return list;
    }

    private byte[] downloadBytes(FTPClient client, String path) throws IOException {
        client.changeWorkingDirectory("/");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        client.retrieveFile(path, bos);
        return bos.toByteArray();
    }
}
