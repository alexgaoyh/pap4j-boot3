package cn.net.pap.neo4j.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 1、可以在类文件上添加 @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "personId") 注解处理循环引用，使用对象的属性作为唯一标识符，
 *          ObjectMapper objectMapperJsonIdentityInfo = new ObjectMapper();
 *          String jsonIdentityInfoStr = objectMapperJsonIdentityInfo.writeValueAsString(p2);
 */
@Node("person")
public class PersonEntity implements Serializable {

    @Id
    private String personId;

    @Property("personName")
    private String personName;

    @Property("description")
    private String description;

    @Relationship(type = "hobbys", direction = Relationship.Direction.INCOMING)
    private List<HobbyEntity> hobbys;

    @Relationship(type = "parents", direction = Relationship.Direction.INCOMING)
    private List<PersonEntity> parents = new ArrayList<>();

    @Relationship(type = "childrens", direction = Relationship.Direction.INCOMING)
    private List<PersonEntity> childrens = new ArrayList<>();

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<HobbyEntity> getHobbys() {
        return hobbys;
    }

    public void setHobbys(List<HobbyEntity> hobbys) {
        this.hobbys = hobbys;
    }

    public List<PersonEntity> getParents() {
        return parents;
    }

    public void setParents(List<PersonEntity> parents) {
        this.parents = parents;
    }

    public List<PersonEntity> getChildrens() {
        return childrens;
    }

    public void setChildrens(List<PersonEntity> childrens) {
        this.childrens = childrens;
    }
}
