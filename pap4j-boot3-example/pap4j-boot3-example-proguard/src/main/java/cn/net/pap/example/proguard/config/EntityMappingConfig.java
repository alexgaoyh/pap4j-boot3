package cn.net.pap.example.proguard.config;

import cn.net.pap.example.proguard.entity.Proguard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 手动注册实体类对象。可以应用于json转实体对象
 */
@Configuration
public class EntityMappingConfig {

    @Bean
    public Map<String, Class<?>> entityMappings() {
        Map<String, Class<?>> mappings = new HashMap<>();

        // todo 手动注册
        mappings.put("proguard", Proguard.class);

        return mappings;
    }
}
