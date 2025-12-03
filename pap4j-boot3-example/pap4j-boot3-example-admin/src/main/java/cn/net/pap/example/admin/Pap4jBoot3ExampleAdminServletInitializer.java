package cn.net.pap.example.admin;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * WEB 容器
 */
public class Pap4jBoot3ExampleAdminServletInitializer extends SpringBootServletInitializer
{
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application)
    {
        return application.sources(Pap4jBoot3ExampleAdminApplication.class);
    }
}
