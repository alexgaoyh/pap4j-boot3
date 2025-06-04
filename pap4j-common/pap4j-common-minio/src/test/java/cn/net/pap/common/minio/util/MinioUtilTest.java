package cn.net.pap.common.minio.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class MinioUtilTest {

    // @Test
    public void initTest() {
        try {

            File file = new File("C:\\Users\\86181\\Desktop\\upload.jpg");
            if(file.exists()){
                // 上传
                try (FileInputStream fis = new FileInputStream(file);){
                    MinioUtil.upload(MinioUtil.DEFAULT_BUCKET, "upload.jpg", fis, file.length(), "image/jpeg");
                }
                //下载
                try(InputStream is = MinioUtil.download(MinioUtil.DEFAULT_BUCKET, "upload.jpg");) {
                    Files.copy(is, Paths.get("C:\\Users\\86181\\Desktop\\download.jpg"), StandardCopyOption.REPLACE_EXISTING);
                }
                // 删除
                MinioUtil.delete(MinioUtil.DEFAULT_BUCKET, "upload.jpg");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
