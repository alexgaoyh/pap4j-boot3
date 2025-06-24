package cn.net.pap.quartz;

import cn.net.pap.quartz.bean.QuartzService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {QuartzAutoConfiguration.class, QuartzService.class})
@TestPropertySource(properties = {
        "org.quartz.jobStore.isClustered=true",
        "org.quartz.jobStore.clusterCheckinInterval=1000",
        "org.quartz.scheduler.instanceId=AUTO"
})
public class QuartzFailoverTest {

    @Autowired
    private Scheduler scheduler;

    private static CountDownLatch failoverLatch = new CountDownLatch(1);
    private static boolean jobFailed = false;
    private static boolean jobRecovered = false;

    // 定义一个会失败的任务
    public static class FailoverTestJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            if (!jobFailed) {
                jobFailed = true;
                System.out.println("Job will fail now at: " + new Date());
                throw new JobExecutionException("Simulated job failure", true);
            } else {
                jobRecovered = true;
                System.out.println("Job recovered and executed successfully at: " + new Date());
                failoverLatch.countDown();
            }
        }
    }

    @Test
    public void testJobFailover() throws Exception {
        scheduler.clear();

        JobDetail job = JobBuilder.newJob(FailoverTestJob.class)
                .withIdentity("failoverTestJob", "testGroup")
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("failoverTestTrigger", "testGroup")
                .startNow()
                .build();

        scheduler.scheduleJob(job, trigger);

        boolean recovered = failoverLatch.await(10, TimeUnit.SECONDS);

        assertTrue(recovered, "Job should have been recovered and executed");
        assertTrue(jobFailed, "Job should have failed initially");
        assertTrue(jobRecovered, "Job should have been recovered and executed successfully");

        List<JobExecutionContext> currentlyExecutingJobs = scheduler.getCurrentlyExecutingJobs();
        assertTrue(currentlyExecutingJobs.isEmpty(), "No jobs should be currently executing");

    }
}
