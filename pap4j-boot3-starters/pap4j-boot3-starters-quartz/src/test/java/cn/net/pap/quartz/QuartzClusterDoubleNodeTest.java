package cn.net.pap.quartz;

import cn.net.pap.quartz.provider.QuartzSpringConnectionProvider;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {QuartzAutoConfiguration.class})
@TestPropertySource("classpath:application.properties")
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class QuartzClusterDoubleNodeTest {

    private static final String CRON = "0/1 * * * * ?"; // 每5秒执行一次

    private final DataSource dataSource;

    public QuartzClusterDoubleNodeTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // @Test
    public void testQuartzClusterTwoNodes() throws Exception {
        // 准备两套 scheduler 配置
        Scheduler scheduler1 = createScheduler("node1", dataSource);
        Scheduler scheduler2 = createScheduler("node2", dataSource);

        // 启动两个 scheduler
        scheduler1.start();
        scheduler2.start();

        // 定义持久化 job + trigger，仅注册一次即可
        JobDetail jobDetail = JobBuilder.newJob(DummyJob.class)
                .withIdentity("clusterJob", "clusterGroup")
                .storeDurably(true)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("clusterTrigger", "clusterGroup")
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(CRON))
                .build();

        scheduler1.scheduleJob(jobDetail, trigger);

        // 等待 15 秒，让任务跑几次
        Thread.sleep(15000);

        // 验证数据库状态
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // 验证两个节点注册
        List<Map<String, Object>> schedulers = jdbcTemplate.queryForList("SELECT * FROM QRTZ_SCHEDULER_STATE");
        assertThat(schedulers.size()).isEqualTo(2);
        System.out.println("集群节点状态：");
        schedulers.forEach(System.out::println);

        // 验证锁
        List<Map<String, Object>> locks = jdbcTemplate.queryForList("SELECT * FROM QRTZ_LOCKS");
        assertThat(locks.size()).isGreaterThan(0);
        System.out.println("锁记录：");
        locks.forEach(System.out::println);

        // 验证任务存在
        List<Map<String, Object>> jobs = jdbcTemplate.queryForList("SELECT * FROM QRTZ_JOB_DETAILS WHERE JOB_NAME = 'clusterJob'");
        assertThat(jobs.size()).isEqualTo(1);
        System.out.println("Job 数据：");
        jobs.forEach(System.out::println);

        // 停止两个 scheduler
        scheduler1.shutdown(true);
        scheduler2.shutdown(true);
    }

    private Scheduler createScheduler(String instanceId, DataSource dataSource) throws Exception {
        Properties props = new Properties();
        // 每个 scheduler 都有唯一的 instanceName（重点！！！）
        props.put("org.quartz.scheduler.instanceName", "ClusterTestScheduler_" + instanceId);
        props.put("org.quartz.scheduler.instanceId", instanceId);
        props.put("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        props.put("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        props.put("org.quartz.jobStore.dataSource", "myDS");
        props.put("org.quartz.jobStore.tablePrefix", "QRTZ_");
        props.put("org.quartz.jobStore.isClustered", "true");
        props.put("org.quartz.jobStore.clusterCheckinInterval", "5000");
        props.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.put("org.quartz.threadPool.threadCount", "3");
        props.put("org.quartz.dataSource.myDS.connectionProvider.class", QuartzSpringConnectionProvider.class.getName());

        StdSchedulerFactory factory = new StdSchedulerFactory();
        QuartzSpringConnectionProvider.setDataSource(dataSource);
        factory.initialize(props);

        return factory.getScheduler();
    }


    // Job 实现
    public static class DummyJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
            try {
                System.out.println("Job 执行于节点: " + context.getScheduler().getSchedulerInstanceId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

