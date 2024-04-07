package cn.net.pap.common.jsonorm.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 在做更新操作的时候，如果出现一对多的关系，则可以把多的数据都删了，然后再插入，这里记录需要做删除操作的数据
 *
 */
public class DelDetailTableValueDTO<T> implements Serializable {

    /**
     * 业务 操作表名，有可能是多张明细，那么这里需要维护的是一个集合
     */
    private List<String> tableNameList;

    /**
     * 业务 操作表名 主键
     */
    private String pk;

    /**
     * 业务 操作表名 主键值
     */
    private T pkValue;

    public List<String> getTableNameList() {
        return tableNameList;
    }

    public void setTableNameList(List<String> tableNameList) {
        this.tableNameList = tableNameList;
    }

    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    public T getPkValue() {
        return pkValue;
    }

    public void setPkValue(T pkValue) {
        this.pkValue = pkValue;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("DelDetailTableValueDTO{");
        sb.append("tableNameList=").append(tableNameList);
        sb.append(", pk='").append(pk).append('\'');
        sb.append(", pkValue=").append(pkValue);
        sb.append('}');
        return sb.toString();
    }

}
