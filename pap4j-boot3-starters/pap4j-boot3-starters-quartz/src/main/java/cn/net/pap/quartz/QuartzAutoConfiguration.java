package cn.net.pap.quartz;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * AutoConfiguration
 */
@Configuration
@ConditionalOnClass(HikariDataSource.class)
public class QuartzAutoConfiguration {

    /**
     * jdbc url
     */
    @Value("${spring.datasource.url}")
    private String url;

    /**
     * jdbc driver
     */
    @Value("${spring.datasource.driverClassName}")
    private String driver;

    /**
     * jdbc username
     */
    @Value("${spring.datasource.username}")
    private String username;

    /**
     * jdbc password
     */
    @Value("${spring.datasource.password}")
    private String password;

    /**
     * 启动过程执行 schema
     */
    // TODO 这里根据实际情况做不同的处理.
    @Value("classpath:tables_h2.sql")
    private org.springframework.core.io.Resource schemaSql;

    /**
     * quartzDataSource
     * @return
     * @throws SQLException
     */
    @Bean("quartzDataSource")
    public DataSource quartzDataSource() throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName(driver);
        hikariConfig.setJdbcUrl(url);
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        return dataSource;
    }

    /**
     * quartzDataSourceInitializer
     * @param dataSource
     * @return
     */
    @Bean("quartzDataSourceInitializer")
    public DataSourceInitializer quartzDataSourceInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(databasePopulator());
        // TODO 这里根据实际情况做不同的处理. true表示需要执行，false表示不需要初始化
        initializer.setEnabled(true);
        return initializer;
    }

    /**
     * 私有 databasePopulator
     * @return
     */
    private DatabasePopulator databasePopulator() {
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScripts(schemaSql);
        populator.setSeparator(";");
        return populator;
    }

    /**
     * schedulerFactoryBean
     * @return
     * @throws Exception
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() throws Exception {
        SchedulerFactoryBean schedulerFactoryBean = new SchedulerFactoryBean();
        schedulerFactoryBean.setDataSource(quartzDataSource());
        schedulerFactoryBean.setTaskExecutor(schedulerThreadPool());
        schedulerFactoryBean.setAutoStartup(true);
        schedulerFactoryBean.setOverwriteExistingJobs(true);
        schedulerFactoryBean.setQuartzProperties(quartzProperties());
        return schedulerFactoryBean;
    }

    /**
     * schedulerThreadPool
     * @return
     */
    @Bean
    public ThreadPoolTaskExecutor schedulerThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("pap4j-boot3-quartz-thread-");
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1024);
        executor.initialize();
        return executor;
    }

    /**
     * 私有 quartzProperties
     * @return
     */
    private Properties quartzProperties() {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceId", "pap4j-boot3-starters-quartz");
        properties.setProperty("spring.quartz.job-store-type", "jdbc");
        properties.setProperty("spring.quartz.jdbc.initialize-schema", "always");
        return properties;
    }

    @Bean
    public ApplicationListener<ContextClosedEvent> contextClosedHandler(ThreadPoolTaskExecutor schedulerThreadPool) {
        return event -> {
            System.out.println("Spring Context 正在关闭，先关闭线程池...");
            // 线程池会进入关闭状态。在这种状态下，无法再向线程池提交新的任务。如果尝试在调用 shutdown() 后提交新任务，将会抛出 RejectedExecutionException 异常。
            schedulerThreadPool.shutdown();
            try {
                if (!schedulerThreadPool.getThreadPoolExecutor().awaitTermination(3600, TimeUnit.SECONDS)) {
                    System.out.println("线程池未能在规定时间内关闭，强制关闭");
                    schedulerThreadPool.getThreadPoolExecutor().shutdownNow();
                } else {
                    System.out.println("线程池优雅关闭成功");
                }
            } catch (InterruptedException e) {
                schedulerThreadPool.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        };
    }

}
