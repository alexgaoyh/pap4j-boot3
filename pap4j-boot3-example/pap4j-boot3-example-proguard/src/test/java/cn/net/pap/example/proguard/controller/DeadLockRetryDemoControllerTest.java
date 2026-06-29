package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import cn.net.pap.example.proguard.service.IDeadlockRetryDemoService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1. 注册与激活过滤器: 在 Spring 配置类或 Spring Boot 自动配置中注册此 Filter，使其在本地联调和单测调试阶段生效。
 * 2. 异常接口自动录制: 当 resStatus >= 400 或捕获到未处理业务异常时，
 * 自动将请求的关键上下文（Method, URI, Query, Payload 等） 输出为高亮报错日志，或以结构化 JSON 文件快照写出到本地临时目录。
 * 3. 集成 AI 单测回放链路: 制定契约格式，由 AI 解析该报错快照并自动生成 MockMvc 的 JUnit 单元测试用例，
 * 从而在本地以最小上下文 100% 重现并离线解决前后端联调中的业务异常与格式对不上等 Bug (即录制与回放测试驱动模式)。
 */
@WebMvcTest(DeadLockRetryDemoController.class)
@Import({DeadLockRetryDemoControllerTest.TestConfig.class, cn.net.pap.example.proguard.config.LogbackConfig.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class DeadLockRetryDemoControllerTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    static class TestConfig {

        @Bean
        public IDeadlockRetryDemoService deadlockRetryDemoService() {
            return Mockito.mock(IDeadlockRetryDemoService.class);
        }

        @Bean
        public IAutoIncrePreKeyService autoIncrePreKeyService() {
            return Mockito.mock(IAutoIncrePreKeyService.class);
        }

        @Bean
        public DeadLockRetryDemoController deadLockRetryDemoController(
                IDeadlockRetryDemoService deadlockRetryDemoService,
                IAutoIncrePreKeyService autoIncrePreKeyService) {
            return new DeadLockRetryDemoController(deadlockRetryDemoService, autoIncrePreKeyService);
        }
    }

    private final MockMvc mvcClient;

    public DeadLockRetryDemoControllerTest(MockMvc mvcClient) {
        this.mvcClient = mvcClient;
    }

    @Test
    public void testError400Replay() throws Exception {
        // From bug_1782626248793_109.json:
        // GET /retry/error400
        // Expected status: 400
        // Expected body: {"error": "Invalid parameter", "code": "BAD_REQUEST"}
        mvcClient.perform(get("/retry/error400")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\": \"Invalid parameter\", \"code\": \"BAD_REQUEST\"}"));
    }

    @Test
    public void testError500Replay() throws Exception {
        // From bug_1782626255848_429.json:
        // GET /retry/error500
        // Throws RuntimeException with message "Simulated 500 internal server error for deadlock test"
        // Expected status: 500 (wrapped in ServletException)
        try {
            mvcClient.perform(get("/retry/error500")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        } catch (jakarta.servlet.ServletException e) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    e.getMessage().contains("Simulated 500 internal server error for deadlock test")
            );
        }
    }
}
