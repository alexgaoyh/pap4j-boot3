package cn.net.pap.quartz;

import cn.net.pap.quartz.bean.QuartzService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {QuartzAutoConfiguration.class, QuartzService.class})
@TestPropertySource("classpath:application.properties")
public class QuartzShutdownPropTest {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private QuartzService quartzService;

    public static class TestBeanJob implements Job {
        private static final Logger logger = LoggerFactory.getLogger(TestBeanJob.class);
        @Override
        public void execute(JobExecutionContext context) {
            try {
                QuartzService quartzService = (QuartzService) context.getScheduler()
                        .getContext()
                        .get("quartzService");

                System.out.println(quartzService.print());
            } catch (SchedulerException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // @Test
    public void testJobInjection() throws Exception {
        scheduler.getContext().put("quartzService", quartzService);
        JobDetail job = JobBuilder.newJob(TestBeanJob.class).build();
        scheduler.scheduleJob(job, TriggerBuilder.newTrigger().startNow().build());
        Thread.sleep(1000);
    }
}