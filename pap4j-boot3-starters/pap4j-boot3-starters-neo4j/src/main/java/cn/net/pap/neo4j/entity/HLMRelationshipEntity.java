package cn.net.pap.neo4j.entity;

import org.springframework.data.neo4j.core.schema.*;

import java.io.Serializable;

@RelationshipProperties
public class HLMRelationshipEntity implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    @Relationship(type = "relation", direction = Relationship.Direction.OUTGOING)
    private HLMEntity startNode;

    @Relationship(type = "relation", direction = Relationship.Direction.INCOMING)
    @TargetNode
    private HLMEntity endNode;

    private String type;

    public HLMEntity getStartNode() {
        return startNode;
    }

    public void setStartNode(HLMEntity startNode) {
        this.startNode = startNode;
    }

    public HLMEntity getEndNode() {
        return endNode;
    }

    public void setEndNode(HLMEntity endNode) {
        this.endNode = endNode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
