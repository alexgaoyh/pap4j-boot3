package cn.net.pap.quartz;

import cn.net.pap.quartz.bean.QuartzService;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Random;

@SpringBootTest(classes = {QuartzAutoConfiguration.class, QuartzService.class})
@TestPropertySource("classpath:application.properties")
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class QuartzShutdownPropTest {

    private final Scheduler scheduler;
    private final QuartzService quartzService;
    private final ThreadPoolTaskExecutor schedulerThreadPool;

    public QuartzShutdownPropTest(Scheduler scheduler, QuartzService quartzService, ThreadPoolTaskExecutor schedulerThreadPool) {
        this.scheduler = scheduler;
        this.quartzService = quartzService;
        this.schedulerThreadPool = schedulerThreadPool;
    }

    public static class TestBeanJob implements Job {
        private static final Logger logger = LoggerFactory.getLogger(TestBeanJob.class);
        @Override
        public void execute(JobExecutionContext context) {
            try {
                QuartzService quartzService = (QuartzService) context.getScheduler()
                        .getContext()
                        .get("quartzService");

                ThreadPoolTaskExecutor schedulerThreadPool = (ThreadPoolTaskExecutor) context.getScheduler()
                        .getContext()
                        .get("schedulerThreadPool");

                schedulerThreadPool.submit(() -> {
                    try {
                        calculatePiUsingMonteCarlo(10_000_000_00L);
                        quartzService.print();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                    }
                    return true;
                });

                System.out.println(quartzService.print());
            } catch (SchedulerException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 长时间 计算
     * @param iterations
     */
    private static void calculatePiUsingMonteCarlo(long iterations) {
        Random random = new Random();
        long insideCircle = 0;

        for (long i = 0; i < iterations; i++) {
            double x = random.nextDouble();
            double y = random.nextDouble();
            if (x * x + y * y <= 1) {
                insideCircle++;
            }
        }

        double pi = 4.0 * insideCircle / iterations;
        System.out.println("Estimated value of Pi: " + pi);
    }

    /**
     * 业务代码可以将当前单元测试引入并变为api形式，配合 server.shutdown=graceful 等一起验证优雅关闭
     * @throws Exception
     */
    // @Test
    public void testJobInjection() throws Exception {
        scheduler.getContext().put("quartzService", quartzService);
        scheduler.getContext().put("schedulerThreadPool", schedulerThreadPool);
        JobDetail job = JobBuilder.newJob(TestBeanJob.class).build();
        scheduler.scheduleJob(job, TriggerBuilder.newTrigger().startNow().build());
        Thread.sleep(1000);
    }
}