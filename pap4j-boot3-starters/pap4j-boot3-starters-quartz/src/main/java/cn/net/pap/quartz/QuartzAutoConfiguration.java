package cn.net.pap.quartz;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AutoConfiguration
 */
@Configuration
@ConditionalOnClass(HikariDataSource.class)
public class QuartzAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QuartzAutoConfiguration.class);

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
        // 不调用 setDataSource，因为数据源配置交给 Quartz 自己管理
        // schedulerFactoryBean.setDataSource(quartzDataSource());
        schedulerFactoryBean.setTaskExecutor(schedulerThreadPool());
        schedulerFactoryBean.setAutoStartup(true);
        schedulerFactoryBean.setOverwriteExistingJobs(true);
        schedulerFactoryBean.setQuartzProperties(quartzProperties());
        return schedulerFactoryBean;
    }

    @ConditionalOnProperty(name = "cn.net.pap.quartz.scheduler.multi", havingValue = "true")
    @Bean(name = "scheduler1")
    public SchedulerFactoryBean scheduler1() throws Exception {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setTaskExecutor(schedulerThreadPool());  // 复用同一个线程池bean也可以
        factory.setQuartzProperties(quartzProperties());
        factory.setAutoStartup(true);
        factory.setOverwriteExistingJobs(true);
        return factory;
    }

    @ConditionalOnProperty(name = "cn.net.pap.quartz.scheduler.multi", havingValue = "true")
    @Bean(name = "scheduler2")
    public SchedulerFactoryBean scheduler2() throws Exception {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setTaskExecutor(schedulerThreadPool());
        factory.setQuartzProperties(quartzProperties());
        factory.setAutoStartup(true);
        factory.setOverwriteExistingJobs(true);
        return factory;
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
        properties.setProperty("org.quartz.scheduler.instanceName", "pap4j-boot3-starters-quartz");
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        properties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        properties.setProperty("org.quartz.jobStore.isClustered", "true");
        properties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        properties.setProperty("org.quartz.jobStore.clusterCheckinInterval", "2000");
        properties.setProperty("org.quartz.jobStore.dataSource", "quartzDs");

        properties.setProperty("org.quartz.dataSource.quartzDs.provider", "hikaricp");
        properties.setProperty("org.quartz.dataSource.quartzDs.driver", driver);
        properties.setProperty("org.quartz.dataSource.quartzDs.URL", url);
        properties.setProperty("org.quartz.dataSource.quartzDs.user", username);
        properties.setProperty("org.quartz.dataSource.quartzDs.password", password);
        properties.setProperty("org.quartz.dataSource.quartzDs.maxConnections", "10");

        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", "10");

        return properties;
    }

    @Bean
    public ApplicationListener<ContextClosedEvent> contextClosedHandler(ThreadPoolTaskExecutor schedulerThreadPool) {
        return event -> {
            logger.info("Spring Context 正在关闭，先关闭线程池...");
            // 线程池会进入关闭状态。在这种状态下，无法再向线程池提交新的任务。如果尝试在调用 shutdown() 后提交新任务，将会抛出 RejectedExecutionException 异常。
            schedulerThreadPool.shutdown();

            ThreadPoolExecutor executor = schedulerThreadPool.getThreadPoolExecutor();
            logger.info("线程池活跃线程数: {}", executor.getActiveCount());
            logger.info("线程池当前线程数: {}", executor.getPoolSize());
            logger.info("队列等待任务数: {}", executor.getQueue().size());
            logger.info("\n===== 线程池活跃线程堆栈信息 =====");

            Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
            allThreads.forEach((thread, stackTrace) -> {
                if (thread.getName().startsWith(schedulerThreadPool.getThreadNamePrefix())) {
                    logger.info("线程名称: {}, ID: {}, 状态: {}",
                            thread.getName(),
                            thread.getId(),
                            thread.getState());
                    logger.info("  堆栈跟踪:");
                    for (StackTraceElement ste : stackTrace) {
                        logger.info("    {}", ste);
                    }
                    logger.info("------------------------");
                }
            });

            try {
                if (!schedulerThreadPool.getThreadPoolExecutor().awaitTermination(3600, TimeUnit.SECONDS)) {
                    logger.warn("线程池未能在规定时间内关闭，强制关闭");
                    schedulerThreadPool.getThreadPoolExecutor().shutdownNow();
                } else {
                    logger.info("线程池优雅关闭成功");
                }
            } catch (InterruptedException e) {
                schedulerThreadPool.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
                logger.error("线程池关闭被中断", e);
            }
        };
    }
}