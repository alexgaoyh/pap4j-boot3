package cn.net.pap.example.proguard.entity;

import cn.net.pap.example.proguard.convert.JsonTypeConvert;
import jakarta.persistence.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "proguard")
public class Proguard {

    @Id
    @Comment(value = "主键")
    private Long proguardId;

    @Column(length = 50, nullable = false)
    @Comment(value = "名称")
    private String proguardName;

    @Type(value = JsonTypeConvert.class)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> extMap = new HashMap<>();

    public Long getProguardId() {
        return proguardId;
    }

    public void setProguardId(Long proguardId) {
        this.proguardId = proguardId;
    }

    public String getProguardName() {
        return proguardName;
    }

    public void setProguardName(String proguardName) {
        this.proguardName = proguardName;
    }

    public Map<String, Object> getExtMap() {
        return extMap;
    }

    public void setExtMap(Map<String, Object> extMap) {
        this.extMap = extMap;
    }

}
