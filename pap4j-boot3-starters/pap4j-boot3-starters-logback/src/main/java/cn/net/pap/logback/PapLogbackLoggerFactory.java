package cn.net.pap.logback;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 动态日志文件的写入，在spring bean 中使用如下命令创建 log 对象，之后调用 info error 等函数。
 * 效果： 把日志信息自定义的写入到指定的文件中，不用再更改 xml.
 * logback.xml 可参考 /test/resources 文件夹下的配置文件.
 * <p>
 * private static final Logger log = PapLogbackLoggerFactory.getLogger(TestController.class.getSimpleName());
 * log.info("");
 */
public class PapLogbackLoggerFactory {

    public static Logger getLogger(String loggerName) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        ch.qos.logback.classic.Logger logger = context.getLogger(loggerName);

        logger.detachAndStopAllAppenders();

        RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<>();
        rollingFileAppender.setContext(context);
        rollingFileAppender.setName("rollingFileAppender");
        rollingFileAppender.setFile("logs/" + loggerName + ".log");

        PatternLayoutEncoder fileEncoder = new PatternLayoutEncoder();
        fileEncoder.setContext(context);
        fileEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{20} - [%method,%line] - %msg%n");
        fileEncoder.start();

        rollingFileAppender.setEncoder(fileEncoder);

        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(rollingFileAppender);
        rollingPolicy.setFileNamePattern("logs/" + loggerName + ".%d{yyyy-MM-dd}.log");
        rollingPolicy.setMaxHistory(60);
        rollingPolicy.start();

        rollingFileAppender.setRollingPolicy(rollingPolicy);
        rollingFileAppender.start();

        // Console Appender
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setName("consoleAppender");

        PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
        consoleEncoder.setContext(context);
        consoleEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{20} - [%method,%line] - %msg%n");
        consoleEncoder.start();

        consoleAppender.setEncoder(consoleEncoder);
        consoleAppender.start();

        // AsyncAppender
        AsyncAppender asyncAppender = new AsyncAppender();
        asyncAppender.setContext(context);
        asyncAppender.setName("asyncAppender");
        asyncAppender.addAppender(rollingFileAppender);
        asyncAppender.setIncludeCallerData(true); // 设置 includeCallerData 为 true
        asyncAppender.start();

        // Adding appenders to logger
        logger.addAppender(asyncAppender);
        logger.addAppender(consoleAppender);
        logger.setAdditive(false);

        return logger;
    }

}
