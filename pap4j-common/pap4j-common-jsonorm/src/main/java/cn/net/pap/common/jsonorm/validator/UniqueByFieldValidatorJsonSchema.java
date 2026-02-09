package cn.net.pap.common.jsonorm.validator;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * 基于指定字段的唯一性验证器。 验证 JSON 数组中每个对象的指定字段值是否唯一，常用于防止数据重复。 仅对 JSONArray 类型的实例生效，且仅验证包含指定字段的 JSONObject 元素。
 * <p>
 * 使用场景示例：确保数组中的对象在某个关键字段（如 ID、编码等）上不重复。
 *
 * @see CompositeJsonSchemaValidator 可集成此验证器的组合验证器
 * @see JsonSchemaExtraValidator 实现的接口
 */
public class UniqueByFieldValidatorJsonSchema implements JsonSchemaExtraValidator {

    private final String field;

    public UniqueByFieldValidatorJsonSchema(String field) {
        this.field = field;
    }

    @Override
    public void validate(Schema schema, Object instance) {
        if (!(instance instanceof JSONArray)) {
            return;
        }

        JSONArray array = (JSONArray) instance;
        Set<Object> seen = new HashSet<>();

        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject obj = (JSONObject) item;
            if (!obj.has(field)) {
                continue;
            }
            Object value = obj.get(field);
            if (!seen.add(value)) {
                throw new ValidationException(schema, "/" + i + "/" + field + " duplicate value for field '" + field + "': " + value);
            }
        }

    }
}
