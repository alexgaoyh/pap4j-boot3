package cn.net.pap.common.datastructure.cron;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SimpleCronParserTest {

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
    }

}