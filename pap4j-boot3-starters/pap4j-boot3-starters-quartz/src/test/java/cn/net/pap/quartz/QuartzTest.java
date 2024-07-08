package cn.net.pap.quartz;

import cn.net.pap.quartz.util.QuartzUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {QuartzAutoConfiguration.class})
@TestPropertySource("classpath:application.properties")
public class QuartzTest {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private DataSource dataSource;

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

        ResultSet resultSet = dataSource.getConnection().prepareStatement("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES where TABLE_SCHEMA = 'PUBLIC'").executeQuery();
        while (resultSet.next()) {
            System.out.println(resultSet.getString("TABLE_NAME"));
        }
    }

}
