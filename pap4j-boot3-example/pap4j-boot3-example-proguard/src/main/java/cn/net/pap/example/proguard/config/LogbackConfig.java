package cn.net.pap.example.proguard.config;

import cn.net.pap.logback.filter.ReqResLoggerHttpFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class LogbackConfig {

    @Bean
    @ConditionalOnClass(ReqResLoggerHttpFilter.class)
    public FilterRegistrationBean<ReqResLoggerHttpFilter> requestLogFilter() {
        FilterRegistrationBean<ReqResLoggerHttpFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ReqResLoggerHttpFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

}
