package cn.net.pap.common.jsonorm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.util.List;

/**
 * 业务数据，与 MappingORMDTO 匹配使用
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MappingDataDTO implements Serializable {

    /**
     * 业务标识： 唯一键
     */
    private String papBussId;

    /**
     * 业务数据： 数组结构
     */
    private List<JsonNode> data;

    public String getPapBussId() {
        return papBussId;
    }

    public void setPapBussId(String papBussId) {
        this.papBussId = papBussId;
    }

    public List<JsonNode> getData() {
        return data;
    }

    public void setData(List<JsonNode> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MappingDataDTO{");
        sb.append("papBussId='").append(papBussId).append('\'');
        sb.append(", data=").append(data);
        sb.append('}');
        return sb.toString();
    }
}
