package cn.net.pap.neo4j.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 抽象节点
 */
@Node("absNodeEntity")
public class AbsNodeEntity implements Serializable {

    /**
     * 抽象节点Id
     */
    @Id
    private String absNodeId;

    /**
     * 抽象节点 label 标签
     */
    @Property("absNodeLabel")
    private String absNodeLabel;

    /**
     * 抽象节点 type 类型
     */
    @Property("absNodeType")
    private String absNodeType;

    /**
     * 用于存储对外数据的列表
     */
    @Relationship(type = "parents", direction = Relationship.Direction.OUTGOING)
    private Set<AbsNodeEntity> parents = new HashSet<>();

    /**
     * 用于存储对内数据的列表
     */
    @Relationship(type = "childrens", direction = Relationship.Direction.OUTGOING)
    private Set<AbsNodeEntity> childrens = new HashSet<>();

    /**
     * 构造函数
     * @param absNodeId id
     * @param absNodeLabel label
     * @param absNodeType type
     */
    public AbsNodeEntity(String absNodeId, String absNodeLabel, String absNodeType) {
        this.absNodeId = absNodeId;
        this.absNodeLabel = absNodeLabel;
        this.absNodeType = absNodeType;
    }

    public String getAbsNodeId() {
        return absNodeId;
    }

    public void setAbsNodeId(String absNodeId) {
        this.absNodeId = absNodeId;
    }

    public String getAbsNodeLabel() {
        return absNodeLabel;
    }

    public void setAbsNodeLabel(String absNodeLabel) {
        this.absNodeLabel = absNodeLabel;
    }

    public String getAbsNodeType() {
        return absNodeType;
    }

    public void setAbsNodeType(String absNodeType) {
        this.absNodeType = absNodeType;
    }

    public Set<AbsNodeEntity> getParents() {
        return parents;
    }

    public void setParents(Set<AbsNodeEntity> parents) {
        this.parents = parents;
    }

    public Set<AbsNodeEntity> getChildrens() {
        return childrens;
    }

    public void setChildrens(Set<AbsNodeEntity> childrens) {
        this.childrens = childrens;
    }
}
