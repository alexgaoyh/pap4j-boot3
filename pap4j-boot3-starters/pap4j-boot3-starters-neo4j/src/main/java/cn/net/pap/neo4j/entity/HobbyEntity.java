package cn.net.pap.neo4j.entity;

import org.springframework.data.neo4j.core.schema.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 属性
     */
    @DynamicLabels
    private List<String> propList = new ArrayList<>();

    /**
     * 属性
     */
    @CompositeProperty
    private Map<String, Object> propMap = new HashMap<>();

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

    public List<String> getPropList() {
        return propList;
    }

    public void setPropList(List<String> propList) {
        this.propList = propList;
    }

    public Map<String, Object> getPropMap() {
        return propMap;
    }

    public void setPropMap(Map<String, Object> propMap) {
        this.propMap = propMap;
    }

}
