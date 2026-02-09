package cn.net.pap.common.jsonorm.validator;

import org.everit.json.schema.Schema;

/**
 * JSON Schema 额外验证器接口。
 * 用于扩展标准 JSON Schema 验证功能，支持业务特定的验证逻辑。
 * 实现类可以通过此接口添加如字段唯一性、跨字段关联、业务规则等自定义验证。
 *
 * @see CompositeJsonSchemaValidator 使用此接口的组合验证器
 */
public interface JsonSchemaExtraValidator {

    void validate(Schema schema, Object instance);

}