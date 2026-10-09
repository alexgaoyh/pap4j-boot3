package cn.net.pap.example.admin.config.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * OrderByEnum 校验类
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {OrderByEnumValidator.class})
public @interface OrderByEnumValid {

    /**
     * 待校验的字段名或表达式。
     *
     * @return 字段名或表达式
     */
    String value() default "";

    /**
     * 校验失败提示信息。
     *
     * @return 提示信息
     */
    String message() default "Validation failed";

    /**
     * 校验分组。
     *
     * @return 分组类型数组
     */
    Class<?>[] groups() default {};

    /**
     * 负载类型。
     *
     * @return 负载类型数组
     */
    Class<? extends Payload>[] payload() default {};
}
