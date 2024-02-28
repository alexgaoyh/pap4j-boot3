package cn.net.pap.example.user.config;

import cn.net.pap.example.user.config.dto.ExampleUserDTO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes={ExampleUserConfig.class})
public class ExampleUserConfigTest {

    @Autowired
    private ExampleUserDTO exampleUserDTO;

    @Test
    public void exampleUserDTOTest() {
        assertTrue(exampleUserDTO != null);
    }

}
