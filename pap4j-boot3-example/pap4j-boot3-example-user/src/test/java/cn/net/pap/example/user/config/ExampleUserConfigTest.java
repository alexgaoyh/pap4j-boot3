package cn.net.pap.example.user.config;

import cn.net.pap.example.user.config.dto.ExampleUserDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes={ExampleUserConfig.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class ExampleUserConfigTest {

    private final ExampleUserDTO exampleUserDTO;

    public ExampleUserConfigTest(ExampleUserDTO exampleUserDTO) {
        this.exampleUserDTO = exampleUserDTO;
    }

    @Test
    public void exampleUserDTOTest() {
        assertTrue(exampleUserDTO != null);
    }

}
