package cn.net.pap.quartz.util;

import cn.net.pap.quartz.constants.QuartzConstants;
import cn.net.pap.quartz.execution.QuartzDisallowConcurrentExecution;
import org.quartz.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quartz 工具类
 */
public class QuartzUtils {

    /**
     * 创建作业
     * @param scheduler scheduler
     * @param jobId jobId
     * @param jobGroup  jobGroup
     * @param cronExpression    cronExpression
     * @param callMethod    callMethod
     * @param concurrentBool    concurrentBool
     * @throws SchedulerException 异常
     */
    public static void createScheduleJob(Scheduler scheduler,
                                         String jobId,
                                         String jobGroup,
                                         String cronExpression,
                                         String callMethod,
                                         Boolean concurrentBool) throws SchedulerException {
        // 构建job信息
        JobDetail jobDetail = JobBuilder.newJob(QuartzDisallowConcurrentExecution.class).withIdentity(JobKey.jobKey(jobId, jobGroup)).build();
        // 表达式调度构建器
        CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression);

        // 按新的cronExpression表达式构建一个新的trigger
        CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(TriggerKey.triggerKey(jobId, jobGroup))
                .withSchedule(cronScheduleBuilder).build();

        // 放入参数，运行时的方法可以获取
        Map<String, Object> jobMap = new LinkedHashMap<>();
        jobMap.put("jobId", jobId);
        jobMap.put("jobGroup", jobGroup);
        jobMap.put("cronExpression", cronExpression);
        jobMap.put("callMethod", callMethod);
        jobMap.put("concurrentBool", concurrentBool);
        jobDetail.getJobDataMap().put(QuartzConstants.key, jobMap);

        scheduler.scheduleJob(jobDetail, trigger);
    }

    /**
     * 删除
     * @param scheduler
     * @param jobId
     * @param jobGroup
     * @throws SchedulerException
     */
    public static void deleteScheduleJob(Scheduler scheduler,
                                         String jobId,
                                         String jobGroup) throws SchedulerException {
        scheduler.deleteJob(JobKey.jobKey(jobId, jobGroup));
    }

    /**
     * 暂停
     * @param scheduler
     * @param jobId
     * @param jobGroup
     * @throws SchedulerException
     */
    public static void pauseScheduleJob(Scheduler scheduler,
                                         String jobId,
                                         String jobGroup) throws SchedulerException {
        scheduler.pauseJob(JobKey.jobKey(jobId, jobGroup));
    }

    /**
     * 恢复
     * @param scheduler
     * @param jobId
     * @param jobGroup
     * @throws SchedulerException
     */
    public static void resumeScheduleJob(Scheduler scheduler,
                                         String jobId,
                                         String jobGroup) throws SchedulerException {
        scheduler.resumeJob(JobKey.jobKey(jobId, jobGroup));
    }
}
