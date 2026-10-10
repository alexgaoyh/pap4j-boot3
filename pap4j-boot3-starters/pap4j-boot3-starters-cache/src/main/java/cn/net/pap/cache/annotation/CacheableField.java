package cn.net.pap.cache.annotation;

import java.lang.annotation.*;

/**
 * 自定义缓存注解类，添加了自定义的缓存字段（缓存特定字段）
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheableField {

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

    /**
     * 指定要缓存的字段
     *
     * @return
     */
    CacheableType[] fields() default {};

}
