package cn.net.pap.example.proguard.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 实现 某个字段在数据库中根据其他列自增（分组自增）
 * 比如当前表的功能是，在 Proguard 插入数据的时候，根据 proguardName 来查询当前最新的数据，然后放入 Proguard 表
 */
@Entity
@Table(name = "proguard_id_seq")
public class ProguardIdxSeq {

    @Id
    private String proguardName;

    private Integer proguardIdxLast;

    public String getProguardName() {
        return proguardName;
    }

    public void setProguardName(String proguardName) {
        this.proguardName = proguardName;
    }

    public Integer getProguardIdxLast() {
        return proguardIdxLast;
    }

    public void setProguardIdxLast(Integer proguardIdxLast) {
        this.proguardIdxLast = proguardIdxLast;
    }

}
