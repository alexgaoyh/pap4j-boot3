package cn.net.pap.example.proguard.entity;

import cn.net.pap.example.proguard.convert.JsonTypeConvert;
import cn.net.pap.example.proguard.listener.TransactionCompletionListener;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.persistence.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "proguard")
@org.hibernate.envers.Audited
@EntityListeners(TransactionCompletionListener.class)
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

    @Type(value = JsonTypeConvert.class)
    @Column(nullable = false, columnDefinition = "json")
    private List<String> extList = new ArrayList<>();

    /**
     * 结构未定的数组 Jackson ArrayNode
     */
    @Convert(converter = cn.net.pap.example.proguard.convert.JacksonArrayNodeConverter.class)
    private ArrayNode abstractList;

    @org.hibernate.annotations.TenantId
    private String tenantId;

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

    public List<String> getExtList() {
        return extList;
    }

    public void setExtList(List<String> extList) {
        this.extList = extList;
    }

    public ArrayNode getAbstractList() {
        return abstractList;
    }

    public void setAbstractList(ArrayNode abstractList) {
        this.abstractList = abstractList;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public String toString() {
        return "Proguard{" +
                "proguardId=" + proguardId +
                ", proguardName='" + proguardName + '\'' +
                ", extMap=" + extMap +
                ", extList=" + extList +
                ", abstractList=" + abstractList +
                ", tenantId='" + tenantId + '\'' +
                '}';
    }
}
