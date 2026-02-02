package cn.net.pap.example.ftp.server.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class LogbackLevelManager {

    /**
     * 动态修改指定Logger的日志级别（适配Logback 1.5.x）
     *
     * @param loggerName Logger名称，支持：
     *                   - "root" 修改根日志级别
     *                   - "org.apache.ftpserver" 等具体包名
     *                   - 完整类名
     * @param levelName  日志级别，支持：TRACE, DEBUG, INFO, WARN, ERROR, OFF
     * @return 是否修改成功
     */
    public static boolean setLoggerLevel(String loggerName, String levelName) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            // 获取或创建Logger
            Logger logger;
            if ("root".equalsIgnoreCase(loggerName)) {
                logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            } else {
                logger = loggerContext.getLogger(loggerName);
            }

            // 设置日志级别
            Level level = Level.toLevel(levelName.toUpperCase(), null);
            if (level == null) {
                throw new IllegalArgumentException("Invalid log level: " + levelName + ". Valid levels: TRACE, DEBUG, INFO, WARN, ERROR, OFF");
            }

            logger.setLevel(level);

            // Logback 1.5.x 会自动传播级别变化，无需调用 updateLoggers()
            return true;
        } catch (Exception e) {
            System.err.println("Failed to set logger level: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 批量修改日志级别
     *
     * @param levelMap key: loggerName, value: levelName
     * @return 成功修改的数量
     */
    public static int batchSetLoggerLevel(Map<String, String> levelMap) {
        int successCount = 0;
        for (Map.Entry<String, String> entry : levelMap.entrySet()) {
            if (setLoggerLevel(entry.getKey(), entry.getValue())) {
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * 获取当前Logger的日志级别
     *
     * @param loggerName Logger名称
     * @return 日志级别字符串，如果Logger不存在则返回null
     */
    public static String getLoggerLevel(String loggerName) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            Logger logger;
            if ("root".equalsIgnoreCase(loggerName)) {
                logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            } else {
                logger = loggerContext.getLogger(loggerName);
            }

            Level level = logger.getLevel();
            if (level == null) {
                // 如果没有显式设置级别，返回有效级别
                level = logger.getEffectiveLevel();
            }

            return level != null ? level.levelStr : null;
        } catch (Exception e) {
            System.err.println("Failed to get logger level: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取Logger的有效级别（考虑继承关系）
     *
     * @param loggerName Logger名称
     * @return 有效的日志级别字符串
     */
    public static String getEffectiveLoggerLevel(String loggerName) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            Logger logger;
            if ("root".equalsIgnoreCase(loggerName)) {
                logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            } else {
                logger = loggerContext.getLogger(loggerName);
            }

            Level effectiveLevel = logger.getEffectiveLevel();
            return effectiveLevel != null ? effectiveLevel.levelStr : null;
        } catch (Exception e) {
            System.err.println("Failed to get effective logger level: " + e.getMessage());
            return null;
        }
    }

    /**
     * 重置为默认配置（重新读取logback配置文件）
     * 在Logback 1.5.x中，通过重新加载配置实现
     */
    public static void resetToDefault() {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.reset();

            // 重新配置LoggerContext
            ch.qos.logback.classic.util.ContextInitializer ci = new ch.qos.logback.classic.util.ContextInitializer(loggerContext);
            ci.autoConfig();
        } catch (Exception e) {
            System.err.println("Failed to reset logger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 列出所有已配置的Logger及其级别
     */
    public static void listAllLoggers() {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            for (Logger logger : loggerContext.getLoggerList()) {
                String name = logger.getName();
                Level level = logger.getLevel();
                Level effectiveLevel = logger.getEffectiveLevel();

                if (level != null || !name.equals(Logger.ROOT_LOGGER_NAME)) {
                    System.out.printf("Logger: %-50s | Level: %-10s | Effective Level: %-10s%n", name, level != null ? level.levelStr : "null", effectiveLevel.levelStr);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to list loggers: " + e.getMessage());
        }
    }

}