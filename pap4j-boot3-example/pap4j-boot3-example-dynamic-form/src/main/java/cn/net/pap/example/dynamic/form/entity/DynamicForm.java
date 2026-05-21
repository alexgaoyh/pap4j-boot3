package cn.net.pap.example.dynamic.form.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>动态表单元数据实体 (Dynamic Form Metadata Entity)</p>
 * <p>存储表单的元数据定义，是 EAV 模型中的 <b>Form/Module Definition</b> 层。</p>
 *
 * <table border="1">
 *   <caption>字段说明</caption>
 *   <tr><th>字段</th><th>描述</th></tr>
 *   <tr><td>formCode</td><td>唯一标识一个动态表单模块</td></tr>
 *   <tr><td>formName</td><td>表单的人类可读名称</td></tr>
 *   <tr><td>schemaDefinition</td><td>核心定义的 JSON Schema</td></tr>
 * </table>
 *
 */
@Entity
@Table(name = "dynamic_form")
@Getter
@Setter
@Schema(description = "动态表单定义实体")
public class DynamicForm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Column(name = "form_code", unique = true, nullable = false)
    @Schema(description = "表单编码", example = "product_catalog")
    private String formCode;

    @Column(name = "form_name")
    @Schema(description = "表单名称", example = "产品目录")
    private String formName;

    @Column(name = "schema_definition")
    @Lob
    @Schema(description = "JSON Schema 定义内容")
    private String schemaDefinition;
}
