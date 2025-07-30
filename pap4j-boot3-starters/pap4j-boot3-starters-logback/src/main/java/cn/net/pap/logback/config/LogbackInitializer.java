package cn.net.pap.logback.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Logback配置初始化器
 * <p>在应用启动时自动初始化Logback配置</p>
 */
@Component
public class LogbackInitializer implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired(required = false)
    private DataSource dataSource;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        initLogbackConfiguration(dataSource);
    }

    /**
     * 初始化Logback配置
     */
    public static void initLogbackConfiguration(DataSource dataSource) {
        // todo 这里根据实际情况进行调整，仿照如下调用，可以做到将不同包下的日志写到一起.
        // LogbackConfigurationUtil.initSharedLogConfiguration(Arrays.asList("cn.net.pap", "org.apache", "dbLogger"), "pap-apache", Level.INFO, dataSource);
    }
}