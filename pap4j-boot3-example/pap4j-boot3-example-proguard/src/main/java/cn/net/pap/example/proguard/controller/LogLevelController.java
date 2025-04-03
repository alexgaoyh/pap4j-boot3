package cn.net.pap.example.proguard.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/log-level")
public class LogLevelController {

    /**
     * 获取指定logger的当前日志级别
     * GET /api/log-level/getLogLevel?logger=com.example
     */
    @GetMapping("/getLogLevel")
    public ResponseEntity<Map<String, String>> getLogLevel(@RequestParam(required = false, defaultValue = "ROOT") String logger) {

        Logger targetLogger = (Logger) LoggerFactory.getLogger(logger);
        Map<String, String> response = new HashMap<>();
        response.put("logger", logger);
        response.put("level", targetLogger.getEffectiveLevel().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * 修改指定logger的日志级别
     * GET /api/log-level/setLogLevel?logger=com.example&level=DEBUG
     */
    @GetMapping("/setLogLevel")
    public ResponseEntity<Map<String, String>> setLogLevel(@RequestParam String logger, @RequestParam String level) {

        Logger targetLogger = (Logger) LoggerFactory.getLogger(logger);
        Level newLevel = Level.toLevel(level.toUpperCase());
        targetLogger.setLevel(newLevel);

        Map<String, String> response = new HashMap<>();
        response.put("logger", logger);
        response.put("oldLevel", targetLogger.getEffectiveLevel().toString());
        response.put("newLevel", newLevel.toString());
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有logger及其级别
     * GET /api/log-level/all
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, String>> getAllLogLevels() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Map<String, String> loggers = new HashMap<>();

        loggerContext.getLoggerList().forEach(logger -> {
            loggers.put(logger.getName(), logger.getEffectiveLevel().toString());
        });

        return ResponseEntity.ok(loggers);
    }
}
