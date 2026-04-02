package cn.net.pap.logback.config;

import ch.qos.logback.classic.Level;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * Logback配置初始化器
 * <p>在应用启动时自动初始化Logback配置</p>
 */
@Component
public class LogbackInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final DataSource dataSource;

    public LogbackInitializer(ObjectProvider<DataSource> dataSourceProvider) {
        this.dataSource = dataSourceProvider.getIfAvailable();
    }

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