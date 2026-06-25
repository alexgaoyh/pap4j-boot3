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

import java.time.LocalDateTime;

/**
 * <p>动态属性值实体 (Dynamic Properties / Attribute-Value)</p>
 * <p>EAV 模型中的 <b>Values</b> 层，采用多列存储不同类型的值以优化查询性能。</p>
 *
 * <div style="background-color: #f9f9f9; padding: 10px; border-left: 5px solid #ccc;">
 *   注意: 实际存储时会根据 Java 类型自动路由到对应的列（string_value, num_value 等）。
 * </div>
 *
 */
@Entity
@Table(name = "dynamic_field_value")
@Getter
@Setter
@Schema(description = "动态属性值实体")
public class DynamicFieldValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "属性值ID")
    private Long id;

    @Column(name = "record_id", nullable = false)
    @Schema(description = "关联的主记录ID")
    private Long recordId;

    @jakarta.persistence.Transient
    @Schema(description = "关联的主记录 (非 JPA 关联映射)")
    private DynamicRecord record;

    @Column(name = "field_key", nullable = false)
    @Schema(description = "属性名 (Key from JSON Schema)")
    private String fieldKey;

    @Column(name = "string_value")
    @Schema(description = "短文本值")
    private String stringValue;

    @Column(name = "text_value")
    @Lob
    @Schema(description = "长文本或 JSON 块值")
    private String textValue;

    @Column(name = "num_value")
    @Schema(description = "数值型值")
    private Double numValue;

    @Column(name = "date_value")
    @Schema(description = "日期时间值")
    private LocalDateTime dateValue;
}
