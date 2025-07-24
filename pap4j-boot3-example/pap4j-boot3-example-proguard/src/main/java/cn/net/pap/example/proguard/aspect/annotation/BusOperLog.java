package cn.net.pap.example.proguard.aspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusOperLog {

    /**
     * 记录ID（支持SpEL表达式）
     */
    String recId() default "";

    /**
     * 操作描述（支持SpEL表达式）
     */
    String message() default "";

}
