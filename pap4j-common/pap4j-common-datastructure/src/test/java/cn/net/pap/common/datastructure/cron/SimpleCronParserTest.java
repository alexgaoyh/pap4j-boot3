package cn.net.pap.common.datastructure.cron;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SimpleCronParserTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleCronParserTest.class);

    @Test
    public void shown5() throws Exception {
        String cronExpression = "0 15 * * *";
        SimpleCronParser parser = new SimpleCronParser(cronExpression);

        // 打印解析结果
        parser.printCronDetails();

        // 计算并打印下一次执行时间
        LocalDateTime nextTime = parser.getNextExecutionTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println("\n下一次执行时间: " + nextTime.format(formatter));

        // 打印接下来5次执行时间
        System.out.println("\n接下来5次执行时间:");
        for (int i = 0; i < 5; i++) {
            nextTime = parser.getNextExecutionTime();
            System.out.println(nextTime.format(formatter));
            parser.setCurrentTime(nextTime);
        }
    }

    @Test
    public void shown6() throws Exception {
        String cronExpression = "*/10 * * * * *";
        SimpleCronParser parser = new SimpleCronParser(cronExpression);

        // 打印解析结果
        parser.printCronDetails();

        // 计算并打印下一次执行时间
        LocalDateTime nextTime = parser.getNextExecutionTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println("\n下一次执行时间: " + nextTime.format(formatter));

        // 打印接下来5次执行时间
        System.out.println("\n接下来5次执行时间:");
        for (int i = 0; i < 5; i++) {
            nextTime = parser.getNextExecutionTime();
            System.out.println(nextTime.format(formatter));
            parser.setCurrentTime(nextTime);
        }
    }

    @Test
    public void shown7() throws Exception {
        try {
            String cronExpression = "0 15 10 ? * 6L 2020-2025";
            SimpleCronParser parser = new SimpleCronParser(cronExpression);

            // 打印解析结果
            parser.printCronDetails();

            // 计算并打印下一次执行时间
            LocalDateTime nextTime = parser.getNextExecutionTime();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            System.out.println("\n下一次执行时间: " + nextTime.format(formatter));

            // 打印接下来5次执行时间
            System.out.println("\n接下来5次执行时间:");
            for (int i = 0; i < 5; i++) {
                nextTime = parser.getNextExecutionTime();
                System.out.println(nextTime.format(formatter));
                parser.setCurrentTime(nextTime);
            }
        } catch (Exception e) {
            if(e instanceof java.lang.IllegalStateException && e.getMessage().equals("无法找到下一次执行时间，Cron 表达式可能已过期或逻辑不可达")) {
                log.warn("{}", e.getMessage());
            } else {
                log.error("{}", e.getMessage(), e);
            }
        }
    }

}