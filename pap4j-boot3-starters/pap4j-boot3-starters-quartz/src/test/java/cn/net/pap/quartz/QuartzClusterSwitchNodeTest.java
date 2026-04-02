package cn.net.pap.quartz;

import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {QuartzAutoConfiguration.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource(properties = {
        "cn.net.pap.quartz.scheduler.multi=true"
})
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
public class QuartzClusterSwitchNodeTest {

    private static final String CRON = "0/1 * * * * ?"; // 每5秒执行一次

    private final DataSource dataSource;
    private final SchedulerFactoryBean scheduler1;
    private final SchedulerFactoryBean scheduler2;

    public QuartzClusterSwitchNodeTest(
            DataSource dataSource,
            @Qualifier("scheduler1") SchedulerFactoryBean scheduler1,
            @Qualifier("scheduler2") SchedulerFactoryBean scheduler2) {
        this.dataSource = dataSource;
        this.scheduler1 = scheduler1;
        this.scheduler2 = scheduler2;
    }

    // @Test
    public void testQuartzClusterTwoNodes() throws Exception {
        Scheduler sched1 = scheduler1.getScheduler();
        Scheduler sched2 = scheduler2.getScheduler();
        System.out.println(sched1);
        System.out.println(sched2);

        // 只由 scheduler1 注册持久化 job 和 trigger
        JobDetail jobDetail = JobBuilder.newJob(DummyJob.class)
                .withIdentity("clusterJob", "clusterGroup")
                .storeDurably(true)  // 持久化
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("clusterTrigger", "clusterGroup")
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(CRON))
                .build();

        if (!sched1.checkExists(jobDetail.getKey())) {
            sched1.scheduleJob(jobDetail, trigger);
        }

        // 等待任务执行几次，观察执行节点日志
        System.out.println("等待 scheduler1 执行任务...");
        Thread.sleep(8000);

        // 将 scheduler1 置为 standby，模拟故障或关闭
        System.out.println("将 scheduler1 置为 standby，模拟调度器关闭...");
        sched1.standby();

        // 等待 scheduler2 接管任务执行
        System.out.println("等待 scheduler2 接管任务执行...");
        Thread.sleep(25000);

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

