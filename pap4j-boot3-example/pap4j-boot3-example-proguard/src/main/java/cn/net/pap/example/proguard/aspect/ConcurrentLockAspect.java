package cn.net.pap.example.proguard.aspect;

import cn.net.pap.example.proguard.aspect.annotation.ConcurrentLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Aspect
@Component
public class ConcurrentLockAspect {

    // 使用ConcurrentHashMap存储所有锁对象
    private static final Map<String, ReentrantLock> LOCK_MAP = new ConcurrentHashMap<>();

    // SpEL表达式解析器
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(concurrentLock)")
    public Object around(ProceedingJoinPoint joinPoint, ConcurrentLock concurrentLock) throws Throwable {
        // 1. 解析锁key
        String lockKey = resolveLockKey(joinPoint, concurrentLock.value());

        // 2. 获取或创建锁对象
        ReentrantLock lock = LOCK_MAP.computeIfAbsent(lockKey, k -> new ReentrantLock());

        boolean acquired = false;
        try {
            // 3. 尝试获取锁
            acquired = lock.tryLock(concurrentLock.waitTime(), concurrentLock.timeUnit());
            // Thread.sleep(101);
            if (!acquired) {
                throw new RuntimeException(concurrentLock.message());
            }

            // 4. 执行目标方法
            return joinPoint.proceed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        } finally {
            // 5. 释放锁
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 解析锁key，支持SpEL表达式
     */
    private String resolveLockKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        // 如果不是SpEL表达式，直接返回
        if (!keyExpression.startsWith("#")) {
            return keyExpression;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();

        // 创建SpEL上下文
        EvaluationContext context = new StandardEvaluationContext();
        context.setVariable("methodName", method.getName());

        // 添加方法参数到上下文
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        // 解析表达式
        Expression expression = parser.parseExpression(keyExpression);
        Object value = expression.getValue(context);
        return value != null ? value.toString() : keyExpression;
    }

    /**
     * 清理无效锁对象的方法（可选）
     * 用定时任务或异步线程周期性清理, 比如 @Scheduled 周期性的调用当前方法。
     */
    public static void cleanUnusedLocks() {
        LOCK_MAP.entrySet().removeIf(entry -> !entry.getValue().isLocked() && entry.getValue().getQueueLength() == 0);
    }
}
