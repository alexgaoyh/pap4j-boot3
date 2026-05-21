package cn.net.pap.example.dynamic.form.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>动态表单定义传输对象 (Data Transfer Object for Dynamic Form Definition)</p>
 * <p>用于在 API 层传输表单的元数据信息，包括表单编码、名称及对应的 JSON Schema 定义。</p>
 *
 * <pre>
 * <b>示例 JSON Schema:</b>
 * {
 *   "type": "object",
 *   "properties": {
 *     "username": { "type": "string", "title": "用户名" }
 *   }
 * }
 * </pre>
 *
 * @version 1.0
 */
@Data
@Schema(description = "动态表单元数据传输对象")
public class DynamicFormDTO {

    @Schema(description = "表单唯一编码，如 'business_card'", example = "business_card", requiredMode = Schema.RequiredMode.REQUIRED)
    private String formCode;

    @Schema(description = "表单显示名称", example = "名片申请表")
    private String formName;

    @Schema(description = "表单的结构定义 (Formily JSON Schema)", example = "{\"type\":\"object\",...}")
    private String schemaDefinition;
}
