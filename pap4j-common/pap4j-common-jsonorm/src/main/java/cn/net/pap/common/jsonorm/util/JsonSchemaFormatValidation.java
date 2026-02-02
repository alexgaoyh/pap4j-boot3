package cn.net.pap.common.jsonorm.util;

import org.everit.json.schema.FormatValidator;

import java.util.Optional;

/**
 * json schema 自定义验证
 */
public class JsonSchemaFormatValidation {

    /**
     * 密码强度
     */
    public static class StrongPasswordValidator implements FormatValidator {
        @Override
        public Optional<String> validate(String value) {
            if (value.length() < 8) {
                return Optional.of("密码长度必须至少8位");
            }
            if (!value.matches(".*[a-z].*")) {
                return Optional.of("必须包含小写字母");
            }
            if (!value.matches(".*[A-Z].*")) {
                return Optional.of("必须包含大写字母");
            }
            if (!value.matches(".*\\d.*")) {
                return Optional.of("必须包含数字");
            }
            if (!value.matches(".*[@$!%*?&].*")) {
                return Optional.of("必须包含特殊字符");
            }
            return Optional.empty();
        }
    }


}
