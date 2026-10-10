package cn.net.pap.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义缓存注解类，添加了自定义的缓存字段（缓存特定字段）
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheEvictField {

    /**
     * 缓存名称。
     *
     * @return 缓存名称
     */
    String value();

    /**
     * 缓存键表达式。
     *
     * @return 缓存键，默认为空字符串
     */
    String key() default "";

}
