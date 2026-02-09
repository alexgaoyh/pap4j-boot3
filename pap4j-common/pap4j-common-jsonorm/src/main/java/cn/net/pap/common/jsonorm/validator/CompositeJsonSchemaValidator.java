package cn.net.pap.common.jsonorm.validator;

import org.everit.json.schema.Schema;

import java.util.List;

/**
 * 组合式 JSON Schema 验证器。 在基础 JSON Schema 验证的基础上，支持通过额外的验证器进行补充验证。
 * 主要流程为：先执行标准 JSON Schema 验证，然后依次执行额外的自定义验证规则。
 *
 * @see JsonSchemaExtraValidator 自定义验证器接口
 * @see UniqueByFieldValidatorJsonSchema 基于字段唯一性的验证器实现
 */
public class CompositeJsonSchemaValidator {

    private final Schema schema;

    private final List<JsonSchemaExtraValidator> jsonSchemaExtraValidators;

    public CompositeJsonSchemaValidator(Schema schema, List<JsonSchemaExtraValidator> jsonSchemaExtraValidators) {
        this.schema = schema;
        this.jsonSchemaExtraValidators = jsonSchemaExtraValidators;
    }

    public void validate(Object instance) {
        schema.validate(instance);

        for (JsonSchemaExtraValidator v : jsonSchemaExtraValidators) {
            v.validate(schema, instance);
        }
    }

}

