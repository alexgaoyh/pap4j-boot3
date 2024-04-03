package cn.net.pap.common.jsonorm.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 待 DB 操作的结构化数据结构。
 * 将 业务数据-MappingDataDTO 按照  业务-表结构映射关系-MappingORMDTO 进行数据封装，转换为 结构化的数据
 */
public class TableFieldValueDTO implements Serializable {

    /**
     * 业务 操作表名
     */
    private String tableName;

    /**
     * 业务 操作表名 主键
     */
    private String pk;

    /**
     * 业务 操作表名 外键
     */
    private List<String> fk;

    /**
     * 业务 操作数据
     */
    private Map<String, Object> valueMap;

    /**
     * 是否成功状态标识
     * 0-成功 ; 1-缺失字段
     */
    private Integer successInt = 0;

    /**
     * 错误信息
     */
    private String errorMsg = "";

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

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

    public Map<String, Object> getValueMap() {
        return valueMap;
    }

    public void setValueMap(Map<String, Object> valueMap) {
        this.valueMap = valueMap;
    }

    public Integer getSuccessInt() {
        return successInt;
    }

    public void setSuccessInt(Integer successInt) {
        this.successInt = successInt;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("TableFieldValueDTO{");
        sb.append("tableName='").append(tableName).append('\'');
        sb.append(", pk='").append(pk).append('\'');
        sb.append(", fk=").append(fk);
        sb.append(", valueMap=").append(valueMap);
        sb.append(", successInt=").append(successInt);
        sb.append(", errorMsg='").append(errorMsg).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
