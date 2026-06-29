package cn.net.pap.example.proguard;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        },
        properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
)
@TestPropertySource("classpath:application.properties")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class VariableDefinedInServiceTest {

    private static final Logger log = LoggerFactory.getLogger(VariableDefinedInServiceTest.class);

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
            log.info("{}", errorServiceConfig.getCount());
        });

    }
}
