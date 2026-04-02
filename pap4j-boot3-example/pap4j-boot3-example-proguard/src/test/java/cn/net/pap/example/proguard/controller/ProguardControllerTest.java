package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.properties.DemoProperties;
import cn.net.pap.example.proguard.service.IProguardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProguardController.class)
@Import(ProguardControllerTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class ProguardControllerTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    static class TestConfig {

        @Bean
        public IProguardService proguardService() {
            return Mockito.mock(IProguardService.class);
        }

        @Bean
        public DemoProperties demoProperties() {
            return Mockito.mock(DemoProperties.class);
        }

        @Bean
        public ProguardController proguardController(IProguardService proguardService, DemoProperties demoProperties) {
            // 使用构造函数注入，不再使用 ReflectionTestUtils
            // 注意：ProguardController 的构造函数需要 many parameters，我们需要在 TestConfig 中提供 Mock 或 null
            return new ProguardController(proguardService, demoProperties, new HashMap<>(), null, null, new ObjectMapper());
        }

    }

    private final MockMvc mvcClient;
    private final IProguardService proguardService;

    public ProguardControllerTest(MockMvc mvcClient, IProguardService proguardService) {
        this.mvcClient = mvcClient;
        this.proguardService = proguardService;
    }

    @Test
    public void getProguardByProguardIdTest() throws Exception {
        // 准备测试数据
        Proguard mockProguard = new Proguard();
        mockProguard.setProguardId(123L);
        mockProguard.setProguardName("alexgaoyh123");

        // 配置 Mock 行为
        when(proguardService.getProguardByProguardId(1L)).thenReturn(mockProguard);

        // 执行并验证
        mvcClient.perform(get("/getProguardByProguardId?proguardId=1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proguardId").value(123L))
                .andExpect(jsonPath("$.proguardName").value("alexgaoyh123"));
    }

}
