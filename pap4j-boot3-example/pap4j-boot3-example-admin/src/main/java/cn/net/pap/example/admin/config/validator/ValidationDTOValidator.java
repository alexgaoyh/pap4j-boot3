package cn.net.pap.example.admin.config.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidationDTOValidator implements ConstraintValidator<ValidationDTOValid, Object> {

    private static final Logger log = LoggerFactory.getLogger(ValidationDTOValidator.class);

    @Override
    public void initialize(ValidationDTOValid constraintAnnotation) {
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext constraintValidatorContext) {
        // 如果将 ValidationDTOValid 注解添加到类上面，那么这里的 value 就是当前的类对象。这里就可以将一些特殊的校验需求给抽离出来。
        log.info("{}", value);
        return true;
    }

}
