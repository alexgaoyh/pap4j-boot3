package cn.net.pap.common.datastructure.catalog.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 目录属性结构
 */
public class CatalogTreeDTO implements Serializable {

    /**
     * 主键
     */
    private String id;

    /**
     * 主键
     */
    private String pid;

    /**
     * 文本
     */
    private String text;

    /**
     * 类型
     */
    private String type;

    /**
     * 子目录
     */
    private List<CatalogTreeDTO> children;

    public CatalogTreeDTO() {
    }

    public CatalogTreeDTO(String id, String text, String type) {
        this.id = id;
        this.text = text;
        this.type = type;
    }

    public CatalogTreeDTO(String text, String type) {
        this.text = text;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
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

    public List<CatalogTreeDTO> getChildren() {
        return children;
    }

    public void setChildren(List<CatalogTreeDTO> children) {
        this.children = children;
    }

    public void addChild(CatalogTreeDTO child) {
        if(this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }

    @Override
    public String toString() {
        return "CatalogTreeDTO{" +
                "id='" + id + '\'' +
                ", pid='" + pid + '\'' +
                ", text='" + text + '\'' +
                ", type='" + type + '\'' +
                ", children=" + children +
                '}';
    }
}
