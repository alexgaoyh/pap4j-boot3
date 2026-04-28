package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipDownloadStreamTest {
    private static final Logger log = LoggerFactory.getLogger(ZipDownloadStreamTest.class);

    @Test
    public void zipDownloadStream() throws IOException {
        log.info("zip download stream");
    }

//    public void downloadStreamingZip(HttpServletResponse response) throws IOException {
//        // 设置响应头
//        response.setContentType("application/zip");
//        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"streaming-download.zip\"");
//
//        try (OutputStream outputStream = response.getOutputStream();
//             ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
//
//            // 模拟添加多个文件到ZIP
//            for (int i = 1; i <= 10; i++) {
//                // 创建ZIP条目
//                ZipEntry zipEntry = new ZipEntry("file-" + i + ".txt");
//                zipOut.putNextEntry(zipEntry);
//
//                // 写入文件内容
//                String fileContent = "This is the content of file " + i;
//                zipOut.write(fileContent.getBytes());
//
//                // 关闭当前条目
//                zipOut.closeEntry();
//
//                // 刷新输出流，确保数据发送到客户端
//                zipOut.flush();
//            }
//        }
//    }

}