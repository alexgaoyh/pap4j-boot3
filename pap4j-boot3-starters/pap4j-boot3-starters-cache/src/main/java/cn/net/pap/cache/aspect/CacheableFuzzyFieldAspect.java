package cn.net.pap.cache.aspect;

import cn.net.pap.cache.annotation.CacheEvictFuzzyField;
import cn.net.pap.cache.annotation.CacheableFuzzyField;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义缓存注解类，单字段的相互模糊搜索的索引处理 环绕AOP
 */
@Aspect
@Component
@ConditionalOnClass(RedisOperations.class)
public class CacheableFuzzyFieldAspect {

    private final CacheManager cacheManager;

    public CacheableFuzzyFieldAspect(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Around(value = "@annotation(cn.net.pap.cache.annotation.CacheableFuzzyField)")
    public Object cacheable(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        CacheableFuzzyField cacheableFuzzyField = method.getAnnotation(CacheableFuzzyField.class);
        String cacheName = cacheableFuzzyField.value();
        String key = cacheableFuzzyField.key();
        String singleFuzzyField = cacheableFuzzyField.singleFuzzyField();

        // 获取参数名称
        String[] parameterNames = signature.getParameterNames();
        Object[] args = pjp.getArgs();

        // 创建 EL 表达式上下文
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 将参数名称和参数值放入上下文
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        // 解析 keyExpression
        key = parser.parseExpression(key).getValue(context, String.class);

        if (cacheManager == null) {
            return pjp.proceed();
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return pjp.proceed();
        }

        Object cachedValue = cache.get(key, Object.class);
        if (cachedValue != null) {
            return cachedValue;
        }

        Object result = pjp.proceed();

        if (result != null) {
            cache.put(key, result);

            // 这里是额外的数据缓存
            String singleFuzzyFieldValue = (String)extractField(result, singleFuzzyField);
            if(!StringUtils.isEmpty(singleFuzzyFieldValue)) {
                for(int idx = 0; idx < singleFuzzyFieldValue.length(); idx++) {
                    char c = singleFuzzyFieldValue.charAt(idx);
                    cache.put(c, singleFuzzyFieldValue.replace(c + "", ""));
                }
            }

        }
        return result;
    }

    @Around(value = "@annotation(cn.net.pap.cache.annotation.CacheEvictFuzzyField)")
    public Object cacheEvict(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        CacheEvictFuzzyField cacheEvictFuzzyField = method.getAnnotation(CacheEvictFuzzyField.class);
        String cacheName = cacheEvictFuzzyField.value();
        String key = cacheEvictFuzzyField.key();
        String singleFuzzyField = cacheEvictFuzzyField.singleFuzzyField();

        // 获取参数名称
        String[] parameterNames = signature.getParameterNames();
        Object[] args = pjp.getArgs();

        // 创建 EL 表达式上下文
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 将参数名称和参数值放入上下文
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        // 解析 keyExpression
        key = parser.parseExpression(key).getValue(context, String.class);

        if (cacheManager == null) {
            return pjp.proceed();
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return pjp.proceed();
        }

        Object cachedValue = cache.get(key, Object.class);
        cache.evict(key);

        String singleFuzzyFieldValue = (String)extractField(cachedValue, singleFuzzyField);
        if(!StringUtils.isEmpty(singleFuzzyFieldValue)) {
            for(int idx = 0; idx < singleFuzzyFieldValue.length(); idx++) {
                char c = singleFuzzyFieldValue.charAt(idx);
                cache.evict(c);
            }
        }


        return pjp.proceed();
    }

    private Object extractField(Object result, String field) {
        try {
            Class<?> resultClass = result.getClass();

            Field declaredField = ReflectionUtils.findField(resultClass, field);
            if (declaredField != null) {
                declaredField.setAccessible(true);
                Object value = declaredField.get(result);
                return value;
            }

            return null;
        } catch (Exception e) {
            throw new RuntimeException("Error extracting fields for caching", e);
        }
    }

    public Map<String, Object> extractFields(Object result) {
        try {
            if(result != null) {
                Class<?> resultClass = result.getClass();
                Map<String, Object> fieldValues = new HashMap<>();

                // 获取所有声明的字段（包括私有字段）
                Field[] declaredFields = resultClass.getDeclaredFields();
                for (Field field : declaredFields) {
                    field.setAccessible(true);
                    Object value = field.get(result);
                    fieldValues.put(field.getName(), value);
                }

                return fieldValues;
            } else {
                return new HashMap<>();
            }

        } catch (Exception e) {
            throw new RuntimeException("Error extracting fields for caching", e);
        }
    }
}
