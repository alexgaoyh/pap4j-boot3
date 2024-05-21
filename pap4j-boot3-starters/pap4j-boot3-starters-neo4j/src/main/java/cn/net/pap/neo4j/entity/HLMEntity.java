package cn.net.pap.neo4j.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Node("HLM")
public class HLMEntity implements Serializable {

    @Id
    private String name;

    @Relationship(type = "RELATIONSHIP", direction = Relationship.Direction.OUTGOING)
    private Set<HLMRelationshipEntity> relationships = new HashSet<>();

    public void addRelationship(String type, HLMEntity target) {
        HLMRelationshipEntity relationship = new HLMRelationshipEntity();
        relationship.setType(type);
        relationship.setStartNode(this);
        relationship.setEndNode(target);
        this.relationships.add(relationship);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<HLMRelationshipEntity> getRelationships() {
        return relationships;
    }

    public void setRelationships(Set<HLMRelationshipEntity> relationships) {
        this.relationships = relationships;
    }
}
