package cn.net.pap.example.proguard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

import java.util.stream.IntStream;

@SpringBootTest(classes =
        {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class,
                VariableDefinedInServiceTest.TestConfig.class
        }
)
@TestPropertySource("classpath:application.properties")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
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

    private final ErrorServiceConfig errorServiceConfig;

    public VariableDefinedInServiceTest(@Qualifier("errorServiceConfig") ErrorServiceConfig errorServiceConfig) {
        this.errorServiceConfig = errorServiceConfig;
    }

    @Test
    public void testIncrement() {
        IntStream.range(10000, 11000).parallel().forEach(i -> {
            errorServiceConfig.increment();
            System.out.println(errorServiceConfig.getCount());
        });

    }
}
