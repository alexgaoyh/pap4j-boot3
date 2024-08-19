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

    String value();

    String key() default "";

    String singleFuzzyField() default "";

}
