package cn.net.pap.quartz;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.annotation.DirtiesContext;

import java.text.SimpleDateFormat;
import java.util.Date;

@SpringBootTest(classes = ScheduleThreadTest.ScheduleConfig.class)
@EnableScheduling
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ScheduleThreadTest {

    private static final Logger log = LoggerFactory.getLogger(ScheduleThreadTest.class);

    @Test
    void testScheduledThreadInfo() throws Exception {
        log.info("main thread: {}", Thread.currentThread().getName());
        Thread.sleep(20000);
    }

    @Configuration
    @EnableScheduling
    static class ScheduleConfig {

        private static final Logger logger = LoggerFactory.getLogger(ScheduleConfig.class);
        private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        /**
         * 所有 @Scheduled 方法，在默认情况下共享同一个调度线程，不会并发执行，永远串行，不会创建多个调度线程
         */
        @Scheduled(fixedDelay = 1000)
        public void scheduledTask() {
            logger.info("Scheduled thread: {}, time:{}", Thread.currentThread().getName(), sdf.format(new Date()));
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Scheduled task interrupted", e);
            }
        }

    }

}
