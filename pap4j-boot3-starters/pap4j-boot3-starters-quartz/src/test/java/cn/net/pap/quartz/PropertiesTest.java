package cn.net.pap.quartz;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {PropertiesTest.SemaphoreTestConfig.class})
@TestPropertySource("classpath:application.properties")
@EnableConfigurationProperties
public class PropertiesTest {

    @Autowired
    private Map<String, Semaphore> semaphoreMap;

    @Test
    public void test() {
        semaphoreMap.forEach((key, value) -> {
            System.out.println(key + ":" + value.availablePermits());
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
