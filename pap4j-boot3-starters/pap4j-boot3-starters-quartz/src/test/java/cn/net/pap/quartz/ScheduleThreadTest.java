package cn.net.pap.quartz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.text.SimpleDateFormat;
import java.util.Date;

@SpringBootTest(classes = ScheduleThreadTest.ScheduleConfig.class)
@EnableScheduling
public class ScheduleThreadTest {

    @Test
    void testScheduledThreadInfo() throws Exception {
        System.out.println("main thread: " + Thread.currentThread().getName());
        Thread.sleep(20000);
    }

    @Configuration
    @EnableScheduling
    static class ScheduleConfig {

        private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        /**
         * 所有 @Scheduled 方法，在默认情况下共享同一个调度线程，不会并发执行，永远串行，不会创建多个调度线程
         */
        @Scheduled(fixedDelay = 1000)
        public void scheduledTask() {
            System.out.println("Scheduled thread: " + Thread.currentThread().getName() + ", time:" + sdf.format(new Date()));
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {

            }
        }

    }

}
