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

    public CatalogTreeDTO(String text, String type) {
        this.text = text;
        this.type = type;
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
        return new StringJoiner(", ", CatalogTreeDTO.class.getSimpleName() + "[", "]")
                .add("text='" + text + "'")
                .add("type='" + type + "'")
                .add("children=" + children)
                .toString();
    }
}
