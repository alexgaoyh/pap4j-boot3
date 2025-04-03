package cn.net.pap.logback.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 更改日志级别工具类
 */
public class LoggerLevelUtil {

    /**
     * 获取指定logger的当前有效日志级别
     *
     * @param loggerName logger名称(包或类全路径)，默认ROOT
     * @return 包含logger名称和级别的Map
     */
    public static Map<String, String> getLogLevel(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            loggerName = "ROOT";
        }

        Logger targetLogger = (Logger) LoggerFactory.getLogger(loggerName);
        Map<String, String> result = new HashMap<>();
        result.put("logger", loggerName);
        result.put("level", targetLogger.getEffectiveLevel().toString());

        return result;
    }

    /**
     * 设置指定logger的日志级别
     *
     * @param loggerName logger名称(包或类全路径)
     * @param levelName  要设置的级别(TRACE/DEBUG/INFO/WARN/ERROR/OFF)
     * @return 包含修改前后信息的Map
     */
    public static Map<String, String> setLogLevel(String loggerName, String levelName) {
        Logger targetLogger = (Logger) LoggerFactory.getLogger(loggerName);
        Level newLevel = Level.toLevel(levelName.toUpperCase());
        Level oldLevel = targetLogger.getEffectiveLevel();

        targetLogger.setLevel(newLevel);

        Map<String, String> result = new HashMap<>();
        result.put("logger", loggerName);
        result.put("oldLevel", oldLevel.toString());
        result.put("newLevel", newLevel.toString());
        result.put("status", "success");

        return result;
    }

    /**
     * 获取所有logger及其有效级别
     *
     * @return 所有logger名称和级别的Map
     */
    public static Map<String, String> getAllLogLevels() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Map<String, String> loggers = new HashMap<>();

        loggerContext.getLoggerList().forEach(logger -> {
            loggers.put(logger.getName(), logger.getEffectiveLevel().toString());
        });

        return loggers;
    }

    /**
     * 检查指定logger是否启用了DEBUG级别
     *
     * @param loggerName logger名称
     * @return 是否启用DEBUG级别
     */
    public static boolean isDebugEnabled(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        return logger.isDebugEnabled();
    }

    /**
     * 检查指定logger是否启用了指定级别
     *
     * @param loggerName logger名称
     * @param level      要检查的级别
     * @return 是否启用或高于指定级别
     */
    public static boolean isLevelEnabled(String loggerName, Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        return logger.isEnabledFor(level);
    }
}
