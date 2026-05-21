package cn.net.pap.example.dynamic.form.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>动态关系实体 (Complex Relationships)</p>
 * <p>用于存储记录之间的嵌套关系，如订单与订单项的一对多关系。</p>
 *
 * <p><b>关系模型:</b> Source Record --(relation_code)--> Target Record</p>
 *
 */
@Entity
@Table(name = "dynamic_relation")
@Getter
@Setter
@Schema(description = "动态关系实体")
public class DynamicRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "关系ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_record_id")
    @Schema(description = "源记录 (父级)")
    private DynamicRecord sourceRecord;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "target_record_id")
    @Schema(description = "目标记录 (子级)")
    private DynamicRecord targetRecord;

    @Column(name = "relation_code")
    @Schema(description = "关系标识码", example = "order_items")
    private String relationCode;

    @Column(name = "relation_type")
    @Schema(description = "关系类型", example = "ONE_TO_MANY")
    private String relationType;
}
