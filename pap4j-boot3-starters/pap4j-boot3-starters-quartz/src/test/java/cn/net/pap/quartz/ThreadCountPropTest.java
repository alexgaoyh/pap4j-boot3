package cn.net.pap.quartz;

import cn.net.pap.quartz.job.LongTimeJob;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Properties;

public class ThreadCountPropTest {

    // @Test
    public void test() throws Exception {
        Properties props = new Properties();
        props.setProperty("org.quartz.threadPool.threadCount", "3");
        StdSchedulerFactory factory = new StdSchedulerFactory(props);
        Scheduler scheduler = factory.getScheduler();
        scheduler.start();

        JobDetail job = JobBuilder.newJob(LongTimeJob.class).withIdentity("longTimeJob").build();
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity("longTimeJobTrigger")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInMilliseconds(100).repeatForever())
                .build();

        scheduler.scheduleJob(job, trigger);

        Thread.sleep(99999);

        scheduler.shutdown(true);
    }

}
