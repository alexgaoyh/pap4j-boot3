package cn.net.pap.elasticsearch.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 排序方向枚举
 */
public enum SortOrder {

    /**
     * 升序
     */
    ASC("Asc"),

    /**
     * 降序
     */
    DESC("Desc");

    private final String value;

    SortOrder(String value) {
        this.value = value;
    }

    /**
     * 获取排序方向字符串值
     *
     * @return 排序方向字符串（asc 或 desc）
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串解析排序方向
     *
     * @param order 排序方向字符串
     * @return 匹配的 SortOrder，默认返回 DESC
     */
    @JsonCreator
    public static SortOrder fromString(String order) {
        if (order == null || order.trim().isEmpty()) {
            return DESC;
        }
        String normalized = order.trim().toLowerCase(Locale.ROOT);
        if ("asc".equals(normalized)) {
            return ASC;
        }
        return DESC;
    }
}
