package cn.net.pap.example.dynamic.form.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>动态数据记录实体 (Dynamic Data Record / Anchor)</p>
 * <p>EAV 模型中的 <b>Main Record</b> 层，作为数据的锚点链接具体的值和关系。</p>
 *
 * <p>设计要点:
 * <ul>
 *   <li>通过 formCode 关联到具体的表单定义</li>
 *   <li>通过 fieldValues 级联管理所有的属性值</li>
 *   <li>通过 relations 级联管理所有的嵌套关系</li>
 * </ul>
 * </p>
 *
 */
@Entity
@Table(name = "dynamic_record")
@Getter
@Setter
@Schema(description = "动态数据记录实体")
public class DynamicRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "记录主键ID")
    private Long id;

    @Column(name = "form_code", nullable = false)
    @Schema(description = "所属表单编码")
    private String formCode;

    @Column(name = "create_time")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @Schema(description = "动态属性值列表")
    private List<DynamicFieldValue> fieldValues = new ArrayList<>();

    @OneToMany(mappedBy = "sourceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Schema(description = "动态关系列表")
    private List<DynamicRelation> relations = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }

    /**
     * 添加属性值并维护双向关联
     *
     * @param value 属性值对象
     */
    public void addFieldValue(DynamicFieldValue value) {
        fieldValues.add(value);
        value.setRecord(this);
    }

    /**
     * 添加关系并维护双向关联
     *
     * @param relation 关系对象
     */
    public void addRelation(DynamicRelation relation) {
        relations.add(relation);
        relation.setSourceRecord(this);
    }
}
