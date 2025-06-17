package cn.net.pap.example.proguard.aspect;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
@Profile("demo")
public class DemoEnvAspect {

    // 从配置读取需要排除的读操作前缀（默认值）
    @Value("${demo.intercept.read-prefixes:get,find,query,list,count,select,read,fetch,load}")
    private String[] readMethodPrefixes;

    // 从配置读取需要拦截的写操作前缀（默认值）
    @Value("${demo.intercept.write-prefixes:save,update,delete,create,remove,add,modify,change}")
    private String[] writeMethodPrefixes;

    // 是否启用详细日志
    @Value("${demo.intercept.enable-detail-log:true}")
    private boolean enableDetailLog;

    // 是否标记演示数据
    @Value("${demo.intercept.mark-demo-data:true}")
    private boolean markDemoData;

    /**
     * 拦截Controller和Service层的方法
     */
    @Around("within(@org.springframework.stereotype.Controller *) || " +
            "within(@org.springframework.web.bind.annotation.RestController *) || " +
            "within(@org.springframework.stereotype.Service *)")
    public Object interceptByMethodPrefix(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName().toLowerCase();

        // 1. 检查是否是读操作前缀
        if (startsWithAny(methodName, readMethodPrefixes)) {
            if (enableDetailLog) {
                System.out.println("[Demo] 放行读操作: " + methodName);
            }
            return joinPoint.proceed();
        }

        // 2. 检查是否是写操作前缀
        boolean shouldIntercept = startsWithAny(methodName, writeMethodPrefixes);

        if (shouldIntercept) {
            if (enableDetailLog) {
                System.out.println("[Demo] 拦截写操作: " + methodName);
            }
            return generateDemoResponse(joinPoint, signature);
        }

        // 3. 默认放行其他方法
        return joinPoint.proceed();
    }

    /**
     * 生成演示模式响应
     */
    private Object generateDemoResponse(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        Class<?> returnType = signature.getReturnType();
        Object[] args = joinPoint.getArgs();

        try {
            // 1. 处理void返回类型
            if (returnType == void.class) {
                return null;
            }

            // 2. 处理基本类型
            if (returnType == boolean.class || returnType == Boolean.class) {
                return true;
            }
            if (returnType == int.class || returnType == Integer.class) {
                return 1;
            }
            if (returnType == long.class || returnType == Long.class) {
                return 1L;
            }

            // 3. 处理ResponseEntity
            if (ResponseEntity.class.isAssignableFrom(returnType)) {
                return ResponseEntity.ok(buildSuccessResponse(joinPoint));
            }

            // 4. 处理有参数的方法
            if (args.length > 0) {
                Object firstArg = args[0];
                if (markDemoData) {
                    markAsDemoData(firstArg);
                }
                return firstArg;
            }

            // 5. 返回默认成功响应
            return buildSuccessResponse(joinPoint);

        } catch (Exception e) {
            System.err.println("生成演示响应失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 构建统一的成功响应
     */
    private Map<String, Object> buildSuccessResponse(ProceedingJoinPoint joinPoint) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("code", 200);
        response.put("message", "演示模式: 操作已模拟成功");
        response.put("demoMode", true);

        // 添加请求信息
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes()).getRequest();
            response.put("path", request.getRequestURI());
            response.put("method", request.getMethod());
        } catch (Exception ignored) {
            // 非Web环境忽略
        }

        // 添加方法参数
        if (joinPoint.getArgs().length > 0) {
            response.put("simulatedData", joinPoint.getArgs()[0]);
        }

        return response;
    }

    /**
     * 标记对象为演示数据
     */
    private void markAsDemoData(Object obj) {
        if (obj == null) return;

        try {
            // 尝试调用setDemoMode方法
            Method setter = obj.getClass().getMethod("setDemoMode", boolean.class);
            setter.invoke(obj, true);
        } catch (NoSuchMethodException ignored) {
            // 没有该方法则忽略
        } catch (Exception e) {
            System.err.println("标记演示数据失败: " + e.getMessage());
        }
    }

    /**
     * 检查字符串是否以任意指定前缀开头
     */
    private boolean startsWithAny(String input, String[] prefixes) {
        if (input == null || prefixes == null) return false;

        for (String prefix : prefixes) {
            if (prefix != null && input.startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}