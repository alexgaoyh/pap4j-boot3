package cn.net.pap.common.datastructure.catalog.dto;

import java.io.Serializable;

/**
 * 目录
 */
public class CatalogDTO implements Serializable {

    /**
     * 主键
     */
    private String id;

    /**
     * 文本
     */
    private String text;

    /**
     * 类型
     */
    private String type;

    public CatalogDTO() {
    }

    public CatalogDTO(String id, String text, String type) {
        this.id = id;
        this.text = text;
        this.type = type;
    }

    public CatalogDTO(String text, String type) {
        this.text = text;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
