package cn.net.pap.quartz.job;

import cn.net.pap.quartz.constants.QuartzConstants;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LongTimeJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(LongTimeJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        // 可以增加信号量的控制
        if (true || QuartzConstants.semaphoreONE.tryAcquire()) {
            logger.info("Job {} is running on thread {}", context.getJobDetail().getKey().getName(), Thread.currentThread().getName());
            try {
                Thread.sleep(9999);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                QuartzConstants.semaphoreONE.release();
                logger.info("Task completed: LongTimeJob");
            }
        } else {
            logger.error("No available permits, task LongTimeJob is skipped.");
        }

    }

}