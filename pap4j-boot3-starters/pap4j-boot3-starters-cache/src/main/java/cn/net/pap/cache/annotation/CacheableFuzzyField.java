package cn.net.pap.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义缓存注解类，单字段的相互模糊搜索的索引处理
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheableFuzzyField {

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
     * 单字段模糊搜索字段。
     *
     * @return 模糊搜索字段，默认为空字符串
     */
    String singleFuzzyField() default "";

}
