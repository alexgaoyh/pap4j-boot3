package cn.net.pap.quartz;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

@SpringBootTest(classes = {PropertiesTest.SemaphoreTestConfig.class})
@TestPropertySource("classpath:application.properties")
@EnableConfigurationProperties
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PropertiesTest {

    private static final Logger log = LoggerFactory.getLogger(PropertiesTest.class);

    private final Map<String, Semaphore> semaphoreMap;

    public PropertiesTest(Map<String, Semaphore> semaphoreMap) {
        this.semaphoreMap = semaphoreMap;
    }

    @Test
    public void test() {
        semaphoreMap.forEach((key, value) -> {
            log.info("{}:{}", key, value.availablePermits());
        });
    }

    @Configuration
    public static class SemaphoreTestConfig {

        @Bean
        @ConfigurationProperties(prefix = "quartz.semaphores")
        public Map<String, Integer> semaphoreConfigs() {
            return new HashMap<>();
        }

        @Bean
        public Map<String, Semaphore> semaphoreMap(Map<String, Integer> semaphoreConfigs) {
            Map<String, Semaphore> semaphoreMap = new HashMap<>();
            semaphoreConfigs.forEach((key, value) -> {
                semaphoreMap.put(key, new Semaphore(value));
            });
            return semaphoreMap;
        }
    }
}
