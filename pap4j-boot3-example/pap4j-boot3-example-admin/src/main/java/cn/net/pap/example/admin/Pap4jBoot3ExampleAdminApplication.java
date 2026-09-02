package cn.net.pap.example.admin;

import cn.net.pap.example.admin.util.DigestUtils;
import cn.net.pap.example.admin.util.IntegrityVerifierUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;

@SpringBootApplication(scanBasePackages = "cn.net.pap.example")
public class Pap4jBoot3ExampleAdminApplication {

    private static final Logger log = LoggerFactory.getLogger(Pap4jBoot3ExampleAdminApplication.class);

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
                        log.info("{} -> MD5: {}", resource.getURL(), md5);
                    } catch (Exception e) {
                        log.error("启动后校验失败,服务关闭,error processing file: {}", resource.getFilename(), e);
                        throw new RuntimeException("启动后校验失败,服务关闭：" + "Error processing file: " + resource.getFilename() + " - " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("启动后校验失败,服务关闭", e);
                throw new RuntimeException("启动后校验失败,服务关闭：" + e);
            }

            log.info("====================================================");
            new IntegrityVerifierUtil().verify();

        };
    }

}
