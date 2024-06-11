package cn.net.pap.neo4j.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.io.Serializable;

@Node("hobby")
public class HobbyEntity implements Serializable {

    /**
     * 编号
     */
    @Id
    private String hobbyId;

    /**
     * 爱好
     */
    @Property("hobbyName")
    private String hobbyName;

    public String getHobbyId() {
        return hobbyId;
    }

    public void setHobbyId(String hobbyId) {
        this.hobbyId = hobbyId;
    }

    public String getHobbyName() {
        return hobbyName;
    }

    public void setHobbyName(String hobbyName) {
        this.hobbyName = hobbyName;
    }
}
