package cn.net.pap.example.admin.config.validator;

import cn.net.pap.example.admin.config.validator.dto.OrderByEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;

/**
 * OrderByEnum 校验类
 */
public class OrderByEnumValidator implements ConstraintValidator<OrderByEnumValid, String> {

    @Override
    public void initialize(OrderByEnumValid constraintAnnotation) {
    }

    /**
     * 实际校验类
     * @param value
     * @param constraintValidatorContext
     * @return
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if(Arrays.stream(OrderByEnum.values()).filter(e -> e.name().equals(value)).findAny().isPresent()){
            return true;
        } else {
            return false;
        }
    }

}
