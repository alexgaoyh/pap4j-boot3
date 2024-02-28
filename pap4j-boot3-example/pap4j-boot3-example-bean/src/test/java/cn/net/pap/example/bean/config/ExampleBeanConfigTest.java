package cn.net.pap.example.bean.config;

import cn.net.pap.example.bean.config.dto.ExampleBeanDTO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes={ExampleBeanConfig.class})
public class ExampleBeanConfigTest {

    @Autowired
    private ExampleBeanDTO exampleBeanDTO;

    @Test
    public void exampleBeanDTOTest() {
        assertTrue(exampleBeanDTO != null);
    }

}
