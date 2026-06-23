package cn.net.pap.example.proguard.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/log-level")
@Tag(name = "日志级别管理接口", description = "动态获取、设置指定 Logger 的日志输出级别的相关接口")
public class LogLevelController {

    /**
     * 获取指定logger的当前日志级别
     * GET /api/log-level/getLogLevel?logger=com.example
     */
    @Operation(summary = "获取指定 Logger 的当前日志级别")
    @GetMapping("/getLogLevel")
    public ResponseEntity<Map<String, String>> getLogLevel(@Parameter(description = "Logger 名称，默认为 ROOT") @RequestParam(required = false, defaultValue = "ROOT") String logger) {

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
    @Operation(summary = "修改指定 Logger 的日志级别")
    @GetMapping("/setLogLevel")
    public ResponseEntity<Map<String, String>> setLogLevel(@Parameter(description = "Logger 名称") @RequestParam String logger, @Parameter(description = "新日志级别（DEBUG/INFO/WARN/ERROR等）") @RequestParam String level) {

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
    @Operation(summary = "获取所有 Logger 及其当前的有效级别")
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
