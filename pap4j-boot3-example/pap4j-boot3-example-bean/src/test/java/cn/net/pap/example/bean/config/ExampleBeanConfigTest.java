package cn.net.pap.example.bean.config;

import cn.net.pap.example.bean.config.dto.ExampleBeanDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes={ExampleBeanConfig.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class ExampleBeanConfigTest {

    private final ExampleBeanDTO exampleBeanDTO;

    public ExampleBeanConfigTest(ExampleBeanDTO exampleBeanDTO) {
        this.exampleBeanDTO = exampleBeanDTO;
    }

    @Test
    public void exampleBeanDTOTest() {
        assertTrue(exampleBeanDTO != null);
    }

}
