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

import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <h3>动态日志文件工厂</h3>
 * <p>
 * 该工厂类允许在 Spring Bean 或业务逻辑中动态创建独立的日志文件，无需修改 logback.xml。
 * 核心优化特性：
 * <ul>
 *   <li><b>句柄上限隔离 (Safety Cap):</b> 内置 1024 个 Logger 缓存上限。
 *       <br>目的：防止开发者错误地将动态变量（如 UserId/OrderNo）作为 LoggerName 传入导致系统打开过多文件句柄（Too many open files）而宕机。</li>
 *   <li><b>极致性能 (Async-First):</b> 默认启用高性能异步队列配置。
 *       <br>优化：<code>includeCallerData</code> 已设为 <code>false</code>，避免了高昂的堆栈追踪（类名/行号）开销，大幅提升吞吐。</li>
 *   <li><b>智能回退机制:</b> 超过上限的新请求将回退到 <code>ROOT_LOGGER</code>，保证业务不中断且系统不奔溃。</li>
 *   <li><b>磁盘配额保护:</b> 内置 <code>10MB</code> 单文件滚动及 <code>1GB</code> 总容量限制。</li>
 * </ul>
 * </p>
 * 
 * 使用示例：
 * <pre>{@code
 *      private static final Logger log = PapLogbackLoggerFactory.getLogger(TestController.class.getSimpleName());
 *      log.info("Message");
 * }</pre>
 */
public class PapLogbackLoggerFactory {

    // 缓存上限 1024 以防止由于外部恶意动态传参导致的句柄泄露
    private static final int MAX_LOGGER_COUNT = 1024;
    private static final ConcurrentMap<String, Logger> loggerCache = new ConcurrentHashMap<>();

    public static Logger getLogger(String loggerName) {
        if (loggerCache.size() >= MAX_LOGGER_COUNT && !loggerCache.containsKey(loggerName)) {
            // 达到上限，回退到默认日志，防止 OOM
            return LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        }
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

        // 使用 SizeAndTimeBasedRollingPolicy
        SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(rollingFileAppender);
        rollingPolicy.setFileNamePattern("logs/" + loggerName + ".%d{yyyy-MM-dd}.%i.log");
        rollingPolicy.setMaxFileSize(FileSize.valueOf("10MB")); // 限制单个文件大小
        rollingPolicy.setMaxHistory(60);
        rollingPolicy.setTotalSizeCap(FileSize.valueOf("1GB"));
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
        asyncAppender.setIncludeCallerData(false); // 生产环境通常不需要，提高性能
        asyncAppender.setQueueSize(512);
        asyncAppender.start();
        return asyncAppender;
    }
}