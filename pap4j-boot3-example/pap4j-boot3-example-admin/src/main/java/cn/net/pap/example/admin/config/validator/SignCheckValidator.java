package cn.net.pap.example.admin.config.validator;

import cn.net.pap.example.admin.util.TimestampCryptoUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 签名校验器
 */
public class SignCheckValidator implements ConstraintValidator<SignCheck, String> {

    private long timeTolerance;

    @Override
    public void initialize(SignCheck constraintAnnotation) {
        this.timeTolerance = constraintAnnotation.timeTolerance();
    }

    /**
     * 实际校验逻辑
     *
     * @param signFieldValue 被注解字段的值（通常是签名字段）
     * @param context        校验上下文
     * @return 校验是否通过
     */
    @Override
    public boolean isValid(String signFieldValue, ConstraintValidatorContext context) {
        // 如果签名为空，直接返回false
        if (StringUtils.isBlank(signFieldValue)) {
            return false;
        }
        try {
            return TimestampCryptoUtil.isValid(signFieldValue, timeTolerance);
        } catch (Exception e) {
            // 记录异常日志，但校验失败
            return false;
        }
    }

}
