package cn.net.pap.quartz.execution;

import cn.net.pap.quartz.constants.QuartzConstants;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 禁止并行
 */
@DisallowConcurrentExecution
public class QuartzDisallowConcurrentExecution implements Job {

    private static final Logger log = LoggerFactory.getLogger(QuartzDisallowConcurrentExecution.class);

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        Object dataObject = jobExecutionContext.getMergedJobDataMap().get(QuartzConstants.key);
        log.info("{}", dataObject);
    }

}
