package cn.net.pap.example.bean.config;

import cn.net.pap.example.bean.config.dto.ExampleBeanDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ExampleBeanConfig {

    @Bean
    public ExampleBeanDTO exampleBeanDTO() {
        return new ExampleBeanDTO();
    }

}
