package cn.net.pap.example.proguard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 这是一个 Spring Boot 单元测试示例类，用于演示如何在 **不依赖 @SpringBootApplication 启动类** 的情况下，启动一个最小化的 Spring Web
 * 上下文并开启嵌入式 Tomcat（RANDOM_PORT）。
 * 使用场景：
 * 1、只想测试部分 Web Controller 或接口，不想加载完整应用上下文；
 * 2、可以在测试中模拟 HTTP 并发请求或接口调用；
 * <p>
 * 注意事项：
 * 1、如果不声明 ServletWebServerFactory Bean，RANDOM_PORT 将无法启动，报 MissingWebServerFactoryBeanException；
 * 2、可以在 TestConfig 中进一步添加 Controller 或其他需要的 Bean
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NoSpringBootApplicationTest.TestConfig.class)
public class NoSpringBootApplicationTest {

    @Configuration
    static class TestConfig {

        @Bean
        public org.springframework.boot.web.servlet.server.ServletWebServerFactory servletWebServerFactory() {
            return new org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory();
        }

    }

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    public void test1() throws Exception {
        System.out.println(port);
    }

}
