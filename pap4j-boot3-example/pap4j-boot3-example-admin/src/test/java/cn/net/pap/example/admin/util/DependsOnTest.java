package cn.net.pap.example.admin.util;

import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * <h3>Spring @DependsOn 注解驱动测试类</h3>
 * * <p><b>核心原理：</b><br>
 * 1. 默认情况下，Spring 根据 Bean 之间的显式依赖（如构造函数注入）决定初始化顺序。<br>
 * 2. {@link DependsOn} 用于处理“隐式依赖”：即 A 并不直接持有 B 的引用，但 A 的运行必须依赖 B 已就绪（如监控组件依赖数据库驱动）。<br>
 * 3. <b>生命周期规则：</b>若 A 依赖于 B，则初始化时 B 先于 A；销毁时 A 先于 B。</p>
 *
 * <p><b>本测试目的：</b>验证在手动管理 {@link AnnotationConfigApplicationContext} 生命周期时，
 * Bean 的创建 (Constructor) 与 销毁 (PreDestroy) 的拓扑排序是否符合预期。</p>
 */
public class DependsOnTest {

    // 1. 定义测试用的 Bean
    static class DatabaseConnection {
        public DatabaseConnection() {
            System.out.println("【1】DatabaseConnection 初始化");
        }

        @PreDestroy
        public void destroy() {
            System.out.println("【4】DatabaseConnection 销毁 (应该最后销毁)");
        }
    }

    static class UserService {
        public UserService() {
            System.out.println("【2】UserService 初始化");
        }

        @PreDestroy
        public void destroy() {
            System.out.println("【3】UserService 销毁 (应该先销毁)");
        }
    }

    // 2. 配置类
    @Configuration
    static class TestConfig {
        @Bean
        public DatabaseConnection databaseConnection() {
            return new DatabaseConnection();
        }

        @Bean
        @DependsOn("databaseConnection") // 强制依赖关系
        public UserService userService() {
            return new UserService();
        }
    }

    @Test
    public void testDependsOnOrder() {
        System.out.println("--- 容器启动中 ---");
        // 使用 try-with-resources 确保容器正常关闭，触发 @PreDestroy
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            System.out.println("--- 容器已就绪 ---");
            context.getBean(UserService.class);
        }
        System.out.println("--- 容器已关闭 ---");
    }

}