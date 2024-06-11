package cn.net.pap.neo4j.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.io.Serializable;
import java.util.List;

@Node("department")
public class DepartmentEntity implements Serializable {

    /**
     * 备注
     */
    @Id
    private String remark;

    /**
     * 父节点
     */
    @Relationship(type = "parent", direction = Relationship.Direction.OUTGOING)
    private DepartmentEntity parent;

    /**
     * 子节点
     */
    @Relationship(type = "child", direction = Relationship.Direction.OUTGOING)
    private List<DepartmentEntity> child;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public DepartmentEntity getParent() {
        return parent;
    }

    public void setParent(DepartmentEntity parent) {
        this.parent = parent;
    }

    public List<DepartmentEntity> getChild() {
        return child;
    }

    public void setChild(List<DepartmentEntity> child) {
        this.child = child;
    }


}
