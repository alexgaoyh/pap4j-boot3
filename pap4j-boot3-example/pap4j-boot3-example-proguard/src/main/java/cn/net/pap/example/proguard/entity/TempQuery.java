package cn.net.pap.example.proguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "temp_query",
        indexes = {
                @Index(name = "idx_temp_query_biz_type", columnList = "biz_type"),
                @Index(name = "idx_temp_query_id", columnList = "id")
        })
@IdClass(TempQueryId.class)
public class TempQuery {

    @Id
    @Column(name = "biz_type", length = 32, nullable = false)
    @Comment("业务类型")
    private String bizType;

    @Id
    @Column(name = "id", nullable = false)
    @Comment("查询ID值")
    private Long id;

    public TempQuery() {
    }

    public TempQuery(String bizType, Long id) {
        this.bizType = bizType;
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
