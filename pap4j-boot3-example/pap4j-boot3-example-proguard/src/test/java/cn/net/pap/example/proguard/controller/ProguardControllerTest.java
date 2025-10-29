package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.properties.DemoProperties;
import cn.net.pap.example.proguard.service.IProguardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 专注于 Web MVC 组件的切片测试
 * Controller 层单元测试
 */
@WebMvcTest(ProguardController.class)
@Import({ProguardController.class, ProguardControllerTest.TestConfig.class})
public class ProguardControllerTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    static class TestConfig {
        // 空配置，主要用于排除自动配置
    }

    @Autowired
    private MockMvc mvcClient;

    @MockBean
    private IProguardService proguardService;

    @MockBean
    private DemoProperties demoProperties;

    @Test
    public void getProguardByProguardIdTest() throws Exception {
        // 准备测试数据
        Proguard mockProguard = new Proguard();
        mockProguard.setProguardId(1L);
        mockProguard.setProguardName("alexgaoyh123");

        // 配置 Mock 行为
        when(proguardService.getProguardByProguardId(1L)).thenReturn(mockProguard);

        // 执行并验证
        mvcClient.perform(get("/getProguardByProguardId?proguardId=1").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andExpect(jsonPath("$.proguardId").value(1L)).andExpect(jsonPath("$.proguardName").value("alexgaoyh123"));
    }

}