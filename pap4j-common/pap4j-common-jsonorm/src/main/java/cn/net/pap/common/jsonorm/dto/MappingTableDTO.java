package cn.net.pap.common.jsonorm.dto;

import java.io.Serializable;
import java.util.List;

/**
 * table mapping 表结构 mapping 映射关系
 */
public class MappingTableDTO implements Serializable {

    /**
     * 表结构 主键
     */
    private String pk;

    /**
     * 表结构 外键
     */
    private List<String> fk;

    /**
     * 表结构 操作字段
     */
    private List<String> field;

    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    public List<String> getFk() {
        return fk;
    }

    public void setFk(List<String> fk) {
        this.fk = fk;
    }

    public List<String> getField() {
        return field;
    }

    public void setField(List<String> field) {
        this.field = field;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MappingTableDTO{");
        sb.append("pk='").append(pk).append('\'');
        sb.append(", fk=").append(fk);
        sb.append(", field=").append(field);
        sb.append('}');
        return sb.toString();
    }
}
