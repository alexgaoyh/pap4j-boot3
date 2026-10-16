package cn.net.pap.example.nginx;

import cn.net.pap.example.nginx.config.NginxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * <p>基于 Spring WebFlux 的进程内 Nginx 替代应用入口。</p>
 * <p>提供静态资源托管（含 SPA 回退）与反向代理能力，用于替代存在漏洞修复滞后问题的 Nginx 组件。</p>
 *
 * @author alexgaoyh
 */
@SpringBootApplication
@EnableConfigurationProperties(NginxProperties.class)
public class NginxApplication {

    /**
     * 应用启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NginxApplication.class, args);
    }

}
