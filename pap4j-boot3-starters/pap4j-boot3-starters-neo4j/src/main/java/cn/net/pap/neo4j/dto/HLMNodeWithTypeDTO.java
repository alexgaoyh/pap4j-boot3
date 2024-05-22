package cn.net.pap.neo4j.dto;

import cn.net.pap.neo4j.entity.HLMEntity;

import java.io.Serializable;

public class HLMNodeWithTypeDTO implements Serializable {

    /**
     * 节点 node
     */
    private HLMEntity node;

    /**
     * 节点 type类型
     */
    private String nodeType;

    /**
     * 节点关系 方向
     */
    private String direction;

    public HLMEntity getNode() {
        return node;
    }

    public void setNode(HLMEntity node) {
        this.node = node;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
