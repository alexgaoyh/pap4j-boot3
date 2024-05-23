package cn.net.pap.neo4j.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HLMEntity hlmEntity = (HLMEntity) o;
        return Objects.equals(name, hlmEntity.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public static int sort(HLMEntity o1, HLMEntity o2) {
        if (o1.getName() == null) {
            return 1;
        }
        if (o2.getName() == null) {
            return -1;
        }
        //升序
        return o1.getName().compareTo(o2.getName());
    }

}
