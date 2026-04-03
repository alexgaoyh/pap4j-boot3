package cn.net.pap.logback.util;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.classic.AsyncAppender;
import cn.net.pap.logback.appender.PapDBAppender;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;

/**
 * <h3>Logback 编程式配置底层工具类</h3>
 * <p>
 * 该工具类封装了直接通过 Java 代码动态配置 Logback 核心组件（Appender, Policy, Encoder）的逻辑。
 * 它负责构建整个日志系统的“骨架”，主要功能包括：
 * <ul>
 *   <li><b>标准化 Appender 构建:</b> 统一创建 Console、RollingFile 及无锁 DB Appender。</li>
 *   <li><b>多重异步封装:</b> 默认为所有 Appender 封装异步处理，并优化 <code>queueSize</code> 和 <code>discardingThreshold</code> 策略。</li>
 *   <li><b>全局策略控制:</b> 统一控制日志留存周期（30天）、文件大小阈值（10MB）及总配额限制。</li>
 *   <li><b>根日志接管:</b> 动态初始化 <code>ROOT_LOGGER</code>，确保应用全周期的日志可控。</li>
 * </ul>
 * </p>
 */
public class LogbackConfigurationUtil {

    /**
     * 默认日志格式
     */
    private static final String DEFAULT_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

    private static final Integer maxHistory = 30;

    private static final String maxFileSize = "10MB";

    private static final String totalSizeCap = "1GB";

    /**
     * 默认日志文件路径
     */
    private static final String DEFAULT_LOG_DIR = "logs/";

    /**
     * 初始化共享日志配置
     *
     * @param sharedPackages 需要共享日志文件的包路径列表
     * @param sharedLogName  共享日志文件名(不含扩展名)
     * @param level          日志级别
     */
    public static void initSharedLogConfiguration(List<String> sharedPackages, String sharedLogName, Level level, DataSource dataSource) {
        LoggerContext context = getLoggerContext();

        // 配置根日志
        configureRootLogger(context, Level.WARN);

        // 创建共享文件Appender
        RollingFileAppender<ILoggingEvent> sharedFileAppender = createFileAppender(context, "pap-" + sharedLogName, DEFAULT_LOG_DIR + sharedLogName + ".log", DEFAULT_PATTERN, DEFAULT_LOG_DIR + sharedLogName + ".%d{yyyy-MM-dd}.%i.log", maxHistory, maxFileSize, totalSizeCap);

        // 创建控制台Appender
        ConsoleAppender<ILoggingEvent> consoleAppender = createConsoleAppender(context, "CONSOLE", DEFAULT_PATTERN);

        PapDBAppender dbAppender = createDBAppender(context, "DB", dataSource);
        // 异步包装 (默认关闭 includeCallerData 以大幅提高性能)
        AsyncAppender asyncFile = createAsyncAppender(context, "ASYNC-FILE", sharedFileAppender, 2048, false);
        AsyncAppender asyncConsole = createAsyncAppender(context, "ASYNC-CONSOLE", consoleAppender, 1024, false);
        AsyncAppender asyncDb = createAsyncAppender(context, "ASYNC-DB", dbAppender, 4096, false);

        // 为每个包配置相同的Appender
        sharedPackages.forEach(pkg -> configureLogger(context, pkg, level, asyncFile, asyncConsole, asyncDb, false));
    }

    /**
     * 获取LoggerContext
     *
     * @return LoggerContext实例
     */
    public static LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    /**
     * 配置根日志
     *
     * @param context LoggerContext
     * @param level   根日志级别
     */
    public static void configureRootLogger(LoggerContext context, Level level) {
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAndStopAllAppenders();

        ConsoleAppender<ILoggingEvent> consoleAppender = createConsoleAppender(context, "ROOT-CONSOLE", DEFAULT_PATTERN);
        AsyncAppender asyncConsole = createAsyncAppender(context, "ASYNC-ROOT", consoleAppender, 1024, false);

        rootLogger.addAppender(asyncConsole);
        rootLogger.setLevel(level);
    }

    /**
     * 配置指定Logger
     *
     * @param context         LoggerContext
     * @param loggerName      Logger名称(通常是包路径)
     * @param level           日志级别
     * @param fileAppender    文件Appender
     * @param consoleAppender 控制台Appender
     * @param additive        是否传递给父Logger
     */
    public static void configureLogger(LoggerContext context, String loggerName, Level level, Appender<ILoggingEvent> fileAppender, Appender<ILoggingEvent> consoleAppender, Appender<ILoggingEvent> dbAppender, boolean additive) {
        Logger logger = context.getLogger(loggerName);
        logger.detachAndStopAllAppenders();

        if (fileAppender != null) {
            logger.addAppender(fileAppender);
        }

        if (consoleAppender != null) {
            logger.addAppender(consoleAppender);
        }

        if (loggerName != null && !"".equals(loggerName) && loggerName.equals("dbLogger") && dbAppender != null) {
            logger.addAppender(dbAppender);
        }

        logger.setLevel(level);
        logger.setAdditive(additive);
    }

    /**
     * 创建文件Appender
     *
     * @param context         LoggerContext
     * @param appenderName    Appender名称
     * @param filePath        日志文件路径
     * @param pattern         日志格式
     * @param fileNamePattern 滚动日志文件名模式
     * @param maxHistory      最大保留天数
     * @param maxFileSize     单个文件最大大小
     * @param totalSizeCap    所有日志文件总大小限制
     * @return 配置好的RollingFileAppender
     */
    public static RollingFileAppender<ILoggingEvent> createFileAppender(LoggerContext context, String appenderName, String filePath, String pattern, String fileNamePattern, int maxHistory, String maxFileSize, String totalSizeCap) {
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setName(appenderName);
        appender.setFile(filePath);

        // 设置编码器
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.start();
        appender.setEncoder(encoder);

        // 设置滚动策略
        SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(appender);
        rollingPolicy.setFileNamePattern(fileNamePattern);
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setMaxFileSize(FileSize.valueOf(maxFileSize));
        rollingPolicy.setTotalSizeCap(FileSize.valueOf(totalSizeCap));
        rollingPolicy.start();

        appender.setRollingPolicy(rollingPolicy);
        appender.start();

        return appender;
    }

    /**
     * 创建控制台Appender
     *
     * @param context      LoggerContext
     * @param appenderName Appender名称
     * @param pattern      日志格式
     * @return 配置好的ConsoleAppender
     */
    public static ConsoleAppender<ILoggingEvent> createConsoleAppender(LoggerContext context, String appenderName, String pattern) {
        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setName(appenderName);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.start();

        appender.setEncoder(encoder);
        appender.start();

        return appender;
    }

    /**
     * createDBAppender
     * @param context
     * @param appenderName
     * @param dataSource
     * @return
     */
    public static PapDBAppender createDBAppender(LoggerContext context, String appenderName, DataSource dataSource) {
        PapDBAppender appender = new PapDBAppender(dataSource);
        appender.setContext(context);
        appender.setName(appenderName);
        appender.start();
        return appender;
    }

    /**
     * 异步Appender封装器
     */
    public static AsyncAppender createAsyncAppender(LoggerContext context,
                                                    String asyncName,
                                                    Appender<ILoggingEvent> targetAppender,
                                                    int queueSize,
                                                    boolean includeCallerData) {
        AsyncAppender asyncAppender = new AsyncAppender();
        asyncAppender.setContext(context);
        asyncAppender.setName(asyncName);
        asyncAppender.setQueueSize(queueSize);
        asyncAppender.setIncludeCallerData(includeCallerData);
        asyncAppender.setDiscardingThreshold(0); // 全级别都尽量保留
        asyncAppender.setNeverBlock(true); // 队列满就丢弃日志，主线程绝不阻塞

        asyncAppender.addAppender(targetAppender);
        asyncAppender.start();

        return asyncAppender;
    }

}
