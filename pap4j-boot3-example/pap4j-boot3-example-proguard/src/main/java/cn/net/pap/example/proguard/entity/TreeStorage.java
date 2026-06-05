package cn.net.pap.example.proguard.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Comment;

/**
 * 树形结构存储实体类
 */
@Entity
@Table(name = "tree_storage")
public class TreeStorage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("数据库自增主键")
    private Long id;

    @Column(unique = true, nullable = false)
    @Comment("业务键 (Sequence)")
    private Integer sequence;

    @Column(name = "parent_id")
    @Comment("关联数据库主键的父节点ID")
    private Long parentId;

    @Column(length = 100)
    @Comment("业务属性")
    private String attr1;

    public TreeStorage() {
    }

    public TreeStorage(Integer sequence, Long parentId, String attr1) {
        this.sequence = sequence;
        this.parentId = parentId;
        this.attr1 = attr1;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getAttr1() {
        return attr1;
    }

    public void setAttr1(String attr1) {
        this.attr1 = attr1;
    }

    @Override
    public String toString() {
        return "TreeStorage{" +
                "id=" + id +
                ", sequence=" + sequence +
                ", parentId=" + parentId +
                ", attr1='" + attr1 + '\'' +
                '}';
    }
}
