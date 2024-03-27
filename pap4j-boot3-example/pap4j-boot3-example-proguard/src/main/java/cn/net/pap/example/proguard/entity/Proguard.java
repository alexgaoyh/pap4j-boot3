package cn.net.pap.example.proguard.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "proguard")
public class Proguard {

    @Id
    @Comment(value = "主键")
    private Long proguardId;

    @Column(length = 50, nullable = false)
    @Comment(value = "名称")
    private String proguardName;

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
}
