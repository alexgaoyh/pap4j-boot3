package cn.net.pap.neo4j.dto;

import cn.net.pap.neo4j.entity.AbsNodeEntity;

import java.io.Serializable;

public class AbsNodeWithTypeDTO implements Serializable {

    /**
     * 节点 node
     */
    private AbsNodeEntity node;

    /**
     * 节点 type类型
     */
    private String nodeType;

    public AbsNodeEntity getNode() {
        return node;
    }

    public void setNode(AbsNodeEntity node) {
        this.node = node;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }
}
