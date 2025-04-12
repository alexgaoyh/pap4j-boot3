package cn.net.pap.example.proguard;

import jakarta.annotation.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.stream.IntStream;

@RunWith(SpringRunner.class)
@SpringBootTest(classes =
        {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class,
                VariableDefinedInServiceTest.TestConfig.class
        }
)
@TestPropertySource("classpath:application.properties")
public class VariableDefinedInServiceTest {
    static class ErrorServiceConfig {
        private int count = 0;
        public void increment() {
            count++;
        }
        public int getCount() {
            return count;
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean("errorServiceConfig")
        public ErrorServiceConfig errorServiceConfig() {
            return new ErrorServiceConfig();
        }
    }

    @Resource(name = "errorServiceConfig")
    private ErrorServiceConfig errorServiceConfig;

    @Test
    public void testIncrement() {
        IntStream.range(0, 1000).parallel().forEach(i -> {
            errorServiceConfig.increment();
            System.out.println(errorServiceConfig.getCount());
        });

    }
}
