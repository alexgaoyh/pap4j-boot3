package cn.net.pap.cache.annotation;

import java.lang.annotation.*;

/**
 * 自定义缓存注解类，添加了自定义的缓存字段（缓存特定字段）
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheableField {

    String value();

    String key() default "";

    /**
     * 指定要缓存的字段
     *
     * @return
     */
    CacheableType[] fields() default {};

}
