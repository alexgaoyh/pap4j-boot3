package cn.net.pap.example.proguard.aspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConcurrentLock {
    /**
     * 锁的key，支持SpEL表达式
     * 例如: "#orderId", "#user.id"
     */
    String value();

    /**
     * 等待锁的最长时间(默认3秒)
     */
    long waitTime() default 3;

    /**
     * 锁的自动释放时间(默认10秒)
     */
    long releaseTime() default 10;

    /**
     * 时间单位(默认秒)
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败时的错误信息
     */
    String message() default "操作过于频繁，请稍后再试";
}
