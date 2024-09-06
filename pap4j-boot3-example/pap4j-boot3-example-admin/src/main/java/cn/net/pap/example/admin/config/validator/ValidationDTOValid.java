package cn.net.pap.example.admin.config.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {ValidationDTOValidator.class})
public @interface ValidationDTOValid {

    String value() default "";

    String message() default "Validation failed";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
