package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.properties.DemoProperties;
import cn.net.pap.example.proguard.service.IProguardService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProguardController.class)
@Import(ProguardControllerTest.TestConfig.class)
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
            ProguardController controller = new ProguardController();
            // 反射注入依赖或者改 Controller 支持构造函数注入
            ReflectionTestUtils.setField(controller, "proguardService", proguardService);
            ReflectionTestUtils.setField(controller, "demoProperties", demoProperties);
            return controller;
        }

    }

    @Autowired
    private MockMvc mvcClient;

    @Autowired
    private IProguardService proguardService;

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
