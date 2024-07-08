package cn.net.pap.quartz.execution;

import cn.net.pap.quartz.constants.QuartzConstants;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

/**
 * 禁止并行
 */
@DisallowConcurrentExecution
public class QuartzDisallowConcurrentExecution implements Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        Object dataObject = jobExecutionContext.getMergedJobDataMap().get(QuartzConstants.key);
        System.out.println(dataObject);
    }

}
