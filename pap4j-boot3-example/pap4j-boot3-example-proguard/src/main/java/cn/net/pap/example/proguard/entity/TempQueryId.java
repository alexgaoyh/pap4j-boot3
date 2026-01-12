package cn.net.pap.example.proguard.entity;

import java.util.Objects;

public class TempQueryId implements java.io.Serializable {

    private String bizType;

    private Long id;

    public TempQueryId() {
    }

    public TempQueryId(String bizType, Long id) {
        this.bizType = bizType;
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TempQueryId)) return false;
        TempQueryId that = (TempQueryId) o;
        return Objects.equals(bizType, that.bizType) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bizType, id);
    }

}