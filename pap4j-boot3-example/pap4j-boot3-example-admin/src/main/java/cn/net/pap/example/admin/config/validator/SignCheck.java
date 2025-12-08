package cn.net.pap.example.admin.config.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 签名校验注解
 * 用法：@SignCheck(timeTolerance = 300)
 * 只能用于String类型字段，验证请求签名
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {SignCheckValidator.class})
public @interface SignCheck {

    /**
     * 时间容差（毫秒），默认 5 秒
     */
    long timeTolerance() default 5000;

    /**
     * 错误提示信息
     */
    String message() default "签名验证失败";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
