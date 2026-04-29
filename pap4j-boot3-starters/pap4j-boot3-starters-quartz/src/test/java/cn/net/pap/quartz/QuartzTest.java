package cn.net.pap.quartz;

import cn.net.pap.quartz.util.QuartzUtils;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;

@SpringBootTest(classes = {QuartzAutoConfiguration.class})
@TestPropertySource("classpath:application.properties")
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class QuartzTest {

    private final Scheduler scheduler;
    private final DataSource dataSource;

    public QuartzTest(Scheduler scheduler, DataSource dataSource) {
        this.scheduler = scheduler;
        this.dataSource = dataSource;
    }

    /**
     * 单元测试 执行定时任务 ， 已执行信息获取， 当前数据库已创建表名
     * @throws Exception
     */
    @Test
    public void test1() throws Exception {

        QuartzUtils.createScheduleJob(scheduler, "pap", "pap", "* * * * * ?", "bean.method(1,2,3)", true);

        Thread.sleep(5000);
        System.out.println();

        org.quartz.impl.matchers.GroupMatcher<JobKey> matcher = org.quartz.impl.matchers.GroupMatcher.anyJobGroup();
        Set<JobKey> jobKeys = scheduler.getJobKeys(matcher);
        for (JobKey jobKey : jobKeys) {
            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
            for (Trigger trigger : triggers) {
                System.out.println(trigger.toString());
            }
        }

        Thread.sleep(6000);

        QuartzUtils.pauseScheduleJob(scheduler, "pap", "pap");
        QuartzUtils.resumeScheduleJob(scheduler, "pap", "pap");
        QuartzUtils.deleteScheduleJob(scheduler, "pap", "pap");

        Thread.sleep(6000);

        ResultSet resultSet = dataSource.getConnection().prepareStatement("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES where TABLE_SCHEMA = 'PUBLIC'").executeQuery();
        while (resultSet.next()) {
            System.out.println(resultSet.getString("TABLE_NAME"));
        }
    }

}
