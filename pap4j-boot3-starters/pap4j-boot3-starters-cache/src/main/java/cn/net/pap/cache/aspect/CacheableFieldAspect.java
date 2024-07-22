package cn.net.pap.cache.aspect;

import cn.net.pap.cache.annotation.CacheEvictField;
import cn.net.pap.cache.annotation.CacheableField;
import cn.net.pap.cache.annotation.CacheableType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义缓存注解类，添加了自定义的缓存字段（缓存特定字段） 环绕AOP
 */
@Aspect
@Component
@ConditionalOnClass(RedisOperations.class)
public class CacheableFieldAspect {

    @Autowired
    private CacheManager cacheManager;

    @Around(value = "@annotation(cn.net.pap.cache.annotation.CacheableField)")
    public Object cacheable(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        CacheableField cacheableField = method.getAnnotation(CacheableField.class);
        String cacheName = cacheableField.value();
        String key = cacheableField.key();
        CacheableType[] cacheableTypes = cacheableField.fields();

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
            if(cacheableTypes != null && cacheableTypes.length > 0) {
                for(CacheableType type : cacheableTypes) {
                    if(type.type().equals("map")) {
                        Map<Object, Object> map = (Map)extractField(result, type.field());
                        String mapKey = key + ":" + type.field();
                        if(map != null && !map.isEmpty()) {
                            cache.put(mapKey, map);
                        }
                    }
                    if(type.type().equals("list")) {
                        List<Object> list = (List)extractField(result, type.field());
                        String listKey = key + ":" + type.field();
                        cache.put(listKey, list);
                    }
                }
            }
        }
        return result;
    }

    @Around(value = "@annotation(cn.net.pap.cache.annotation.CacheEvictField)")
    public Object cacheEvict(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        CacheEvictField cacheEvictField = method.getAnnotation(CacheEvictField.class);
        String cacheName = cacheEvictField.value();
        String key = cacheEvictField.key();

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
        Map<String, Object> fieldMap = extractFields(cachedValue);
        for(Map.Entry<String, Object> entry : fieldMap.entrySet()) {
            cache.evict(key + ":" + entry.getKey());
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
