package cn.net.pap.common.minio.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class MinioUtilTest {

    private static final Logger log = LoggerFactory.getLogger(MinioUtilTest.class);

    @Test
    public void initTest() {
        File file = null;
        String targetFileStr = null;
        try {
            file = TestResourceUtil.getFile("jpg.jpg");
            String objectName = "upload.jpg";
            targetFileStr = Files.createTempFile("initTest", ".jpg").toAbsolutePath().toString();
            if(file.exists()){
                // 上传
                try (FileInputStream fis = new FileInputStream(file);){
                    MinioUtil.upload(MinioUtil.DEFAULT_BUCKET, objectName, fis, file.length(), "image/jpeg");
                }
                //下载
                try(InputStream is = MinioUtil.download(MinioUtil.DEFAULT_BUCKET, objectName);) {
                    Files.copy(is, Paths.get(targetFileStr), StandardCopyOption.REPLACE_EXISTING);
                }
                // 删除
                MinioUtil.delete(MinioUtil.DEFAULT_BUCKET, objectName);
            }

        } catch (Exception e) {
            if(e instanceof java.net.ConnectException) {
                log.warn("{}", e);
            } else {
                log.error("{}", e);
            }
        } finally {
            if (file != null && file.exists()) {
                file.delete();
            }
            if (targetFileStr != null) {
                try {
                    Files.deleteIfExists(Paths.get(targetFileStr));
                } catch (Exception e) {
                    log.warn("Failed to delete temp file: " + targetFileStr, e);
                }
            }
        }
    }
}
