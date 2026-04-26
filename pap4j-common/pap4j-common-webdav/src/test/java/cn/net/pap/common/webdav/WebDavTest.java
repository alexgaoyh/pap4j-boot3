package cn.net.pap.common.webdav;

import org.apache.jackrabbit.webdav.MultiStatusResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebDavTest {

    private static final Logger log = LoggerFactory.getLogger(WebDavTest.class);

    /**
     * WebDAV 的上传与下载综合测试方法。
     * 本地测试时，需要先启动 WebDAV 服务器程序。
     * server download in https://github.com/hacdias/webdav/releases
     * 
     * 业务流程：
     * 1. 建立与 WebDAV 服务器的连接。
     * 2. 将本地文件上传到 WebDAV 服务器。
     * 3. 使用 PROPFIND 请求查询 WebDAV 服务器上的文件状态和属性。
     * 4. 遍历查询结果，并将服务器上的文件下载到本地指定目录。
     * 
     * @throws Exception 抛出网络或 IO 等异常
     */
    @Test
    public void uploadAndDownloadTest() throws Exception {
        try {
            // 1. 定义连接信息（测试的基础 URL、用户名、密码）
            String url = "http://127.0.0.1:6065/test2.txt";
            String userName = "basic";
            String password = "basic";

            // 2. 初始化 WebDavUtil 工具类，配置 HTTP 客户端并完成 Basic 身份验证
            WebDavUtil webDavUtil = new WebDavUtil(url, userName, password);

            // 3. 上传操作：读取本地的 test2.txt 文件，并将其上传到服务器的 /test2.txt 路径
            webDavUtil.upload("http://127.0.0.1:6065/test2.txt", new FileInputStream(new File(TestResourceUtil.getFile("test2.txt").getAbsolutePath().toString())));

            // 4. 查询操作：调用 propfind 方法获取服务器上给定 url (此处为 /test.txt) 对应的资源属性
            // MultiStatusResponse 数组中包含了资源的路径、创建时间、文件大小等元数据信息
            MultiStatusResponse[] propfind = webDavUtil.propfind(url);

            // 5. 下载操作：遍历服务器返回的资源元数据列表
            Path tempDir = Files.createTempDirectory("uploadAndDownloadTest-");
            for (int i = 0; i < propfind.length; i++) {
                // 获取资源在服务器上的相对 URI 路径 (href)，如 /test.txt
                String href = propfind[i].getHref();

                // WebDAV 协议返回的路径通常是 URL 编码过的，使用 UTF-8 解码还原出真实的文件名（避免中文等特殊字符乱码）
                String path = URLDecoder.decode(href, "UTF-8");

                // 拼装出完整的下载 URL 链接，并将文件流读取并写入到本地桌面目录中
                webDavUtil.download("http://127.0.0.1:6065/" + href, path, tempDir.toAbsolutePath().toString());
            }
            if (tempDir != null && Files.exists(tempDir)) {
                // 使用 Stream API 倒序遍历（先删子文件，再删父目录）
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            if(e instanceof org.apache.http.conn.HttpHostConnectException) {
                log.info("{}", "请先启动 webdav");
            } else {
                log.error("{}", e);
            }
        }
    }

}
