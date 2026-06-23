package cn.net.pap.example.proguard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@Tag(name = "Zip下载测试接口", description = "提供流式生成并下载 Zip 压缩包的接口")
public class ZipDownloadController {

    @Operation(summary = "流式下载包含多个文本文件的 ZIP 包", description = "动态在内存中创建一个 ZIP 文件流，向其中添加 10 个文本文件并流式写入到客户端 Response 中。")
    @GetMapping("/download/streaming-zip")
    public void downloadStreamingZip(HttpServletResponse response) throws IOException {
        // 设置响应头
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"streaming-download.zip\"");

        try (OutputStream outputStream = response.getOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

            // 模拟添加多个文件到ZIP
            for (int i = 1; i <= 10; i++) {
                // 创建ZIP条目
                ZipEntry zipEntry = new ZipEntry("file-" + i + ".txt");
                zipOut.putNextEntry(zipEntry);

                // 写入文件内容
                String fileContent = "This is the content of file " + i;
                zipOut.write(fileContent.getBytes());

                // 关闭当前条目
                zipOut.closeEntry();

                // 刷新输出流，确保数据发送到客户端
                zipOut.flush();
            }
        }
    }
}