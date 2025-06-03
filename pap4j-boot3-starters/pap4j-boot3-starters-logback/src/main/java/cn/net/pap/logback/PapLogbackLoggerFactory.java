package cn.net.pap.logback;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 动态日志文件的写入，在spring bean 中使用如下命令创建 log 对象，之后调用 info error 等函数。
 * 效果： 把日志信息自定义的写入到指定的文件中，不用再更改 xml.
 * logback.xml 可参考 /test/resources 文件夹下的配置文件.
 * <p>
 * private static final Logger log = PapLogbackLoggerFactory.getLogger(TestController.class.getSimpleName());
 * log.info("");
 */
public class PapLogbackLoggerFactory {

    // 缓存已经初始化过的 Logger
    private static final ConcurrentMap<String, Logger> loggerCache = new ConcurrentHashMap<>();

    public static Logger getLogger(String loggerName) {
        return loggerCache.computeIfAbsent(loggerName, PapLogbackLoggerFactory::createAndConfigureLogger);
    }

    private static Logger createAndConfigureLogger(String loggerName) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = context.getLogger(loggerName);

        // 检查是否已经配置过 Appender，避免重复初始化
        if (!hasAppenders(logger)) {
            // 配置 RollingFileAppender
            RollingFileAppender<ILoggingEvent> rollingFileAppender = buildRollingFileAppender(context, loggerName);
            // 配置 ConsoleAppender
            ConsoleAppender<ILoggingEvent> consoleAppender = buildConsoleAppender(context);
            // 配置 AsyncAppender
            AsyncAppender asyncAppender = buildAsyncAppender(context, rollingFileAppender);

            // 添加 Appender
            logger.addAppender(asyncAppender);
            logger.addAppender(consoleAppender);
            logger.setAdditive(false); // 避免继承父 Logger 的 Appender
        }

        return logger;
    }

    private static boolean hasAppenders(ch.qos.logback.classic.Logger logger) {
        return logger.iteratorForAppenders().hasNext();
    }

    private static RollingFileAppender<ILoggingEvent> buildRollingFileAppender(LoggerContext context, String loggerName) {
        RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<>();
        rollingFileAppender.setContext(context);
        rollingFileAppender.setName("rollingFile_" + loggerName);
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

        return rollingFileAppender;
    }

    private static ConsoleAppender<ILoggingEvent> buildConsoleAppender(LoggerContext context) {
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setName("console");

        PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
        consoleEncoder.setContext(context);
        consoleEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{20} - [%method,%line] - %msg%n");
        consoleEncoder.start();

        consoleAppender.setEncoder(consoleEncoder);
        consoleAppender.start();

        return consoleAppender;
    }

    private static AsyncAppender buildAsyncAppender(LoggerContext context, Appender<ILoggingEvent> appender) {
        AsyncAppender asyncAppender = new AsyncAppender();
        asyncAppender.setContext(context);
        asyncAppender.setName("async_" + appender.getName());
        asyncAppender.addAppender(appender);
        asyncAppender.setIncludeCallerData(true);
        asyncAppender.start();
        return asyncAppender;
    }
}