package cn.net.pap.common.jsonorm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.Map;

/**
 * JSON 结构描述的使用业务标识操作的 业务-表结构 的映射关系
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MappingORMDTO implements Serializable {

    /**
     * 业务标识： 唯一键
     */
    private String papBussId;

    /**
     * 业务标识: 操作方式
     */
    private String operator;

    /**
     * table mapping 表结构映射关系
     */
    private Map<String, MappingTableDTO> mapping;

    public String getPapBussId() {
        return papBussId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public void setPapBussId(String papBussId) {
        this.papBussId = papBussId;
    }

    public Map<String, MappingTableDTO> getMapping() {
        return mapping;
    }

    public void setMapping(Map<String, MappingTableDTO> mapping) {
        this.mapping = mapping;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MappingORMDTO{");
        sb.append("papBussId='").append(papBussId).append('\'');
        sb.append(", operator='").append(operator).append('\'');
        sb.append(", mapping=").append(mapping);
        sb.append('}');
        return sb.toString();
    }
}
