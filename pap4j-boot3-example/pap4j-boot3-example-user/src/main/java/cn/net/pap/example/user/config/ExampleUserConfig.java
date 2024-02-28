package cn.net.pap.example.user.config;

import cn.net.pap.example.user.config.dto.ExampleUserDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ExampleUserConfig {

    @Bean
    public ExampleUserDTO exampleUserDTO() {
        return new ExampleUserDTO();
    }

}
