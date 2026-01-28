package cn.net.pap.example.admin;

import cn.net.pap.example.admin.util.DigestUtils;
import cn.net.pap.example.admin.util.IntegrityVerifierUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;

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
                        String md5 = DigestUtils.calculateMD5(inputStream);
                        System.out.println(resource.getURL() + " -> MD5: " + md5);
                    } catch (Exception e) {
                        throw new RuntimeException("启动后校验失败,服务关闭：" + "Error processing file: " + resource.getFilename() + " - " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("启动后校验失败,服务关闭：" + e);
            }

            System.out.println("====================================================");
            new IntegrityVerifierUtil().verify();

        };
    }

}
