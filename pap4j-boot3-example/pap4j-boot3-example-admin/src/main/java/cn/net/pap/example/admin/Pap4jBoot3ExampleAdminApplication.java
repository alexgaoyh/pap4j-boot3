package cn.net.pap.example.admin;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@SpringBootApplication(scanBasePackages = "cn.net.pap.example")
public class Pap4jBoot3ExampleAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pap4jBoot3ExampleAdminApplication.class, args);
    }

    @Bean
    public CommandLineRunner shutdownAfterStartup() {
        return args -> {
            // 运行时获取所有类文件.
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resolver.getResources("classpath*:cn/net/pap/**/*.class");

                for (Resource resource : resources) {
                    try (InputStream inputStream = resource.getInputStream()) {
                        String md5 = calculateMD5(inputStream);
                        System.out.println(resource.getURL() + " -> MD5: " + md5);
                    } catch (Exception e) {
                        throw new RuntimeException("启动后校验失败,服务关闭：" + "Error processing file: " + resource.getFilename() + " - " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("启动后校验失败,服务关闭：" + e);
            }

        };
    }

    private static String calculateMD5(InputStream inputStream) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            md.update(buffer, 0, bytesRead);
        }
        return bytesToHex(md.digest());
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
