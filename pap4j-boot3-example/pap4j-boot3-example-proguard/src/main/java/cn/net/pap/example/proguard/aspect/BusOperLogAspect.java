package cn.net.pap.example.proguard.aspect;

import cn.net.pap.example.proguard.aspect.annotation.BusOperLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
public class BusOperLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(BusOperLogAspect.class);
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(busOperLog)")
    public Object logOperation(ProceedingJoinPoint joinPoint, BusOperLog busOperLog) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        String recId = parseSpel(busOperLog.recId(), context);
        String message = parseSpel(busOperLog.message(), context);

        Map<String, Object> allParams = new HashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            Object arg = args[i];
            if (!(arg instanceof HttpServletRequest) && !(arg instanceof HttpServletResponse)) {
                allParams.put(paramNames[i], arg);
            }
        }

        String otherParamsJson = objectMapper.writeValueAsString(allParams);

        // todo async 配置
        logger.info("BusOperLog | RecId: [{}] | Message: [{}] | Params: {}", recId, message, otherParamsJson);

        return joinPoint.proceed();
    }

    private String parseSpel(String expressionStr, EvaluationContext context) {
        if (expressionStr == null || expressionStr.equals("")) {
            return "";
        }
        try {
            Expression expression = parser.parseExpression(expressionStr);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            return expressionStr;
        }
    }

}
