package cn.net.pap.quartz.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LongTimeJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(LongTimeJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        logger.info("Job {} is running on thread {}", context.getJobDetail().getKey().getName(), Thread.currentThread().getName());
        try {
            // 模拟长时间任务
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}