package cn.net.pap.common.datastructure.catalog.dto;

import java.io.Serializable;

/**
 * <h1>目录数据传输对象 (Catalog DTO)</h1>
 * <p>表示一个扁平化的目录结构记录。</p>
 * <p>包含基本的属性如：主键、文本内容以及目录类型。</p>
 * 
 * @author alexgaoyh
 */
public class CatalogDTO implements Serializable {

    /**
     * <p>目录主键 ID。</p>
     */
    private String id;

    /**
     * <p>目录显示的文本内容。</p>
     */
    private String text;

    /**
     * <p>目录类型（如：目录一、目录二等）。</p>
     */
    private String type;

    /**
     * <p>默认无参构造函数。</p>
     */
    public CatalogDTO() {
    }

    /**
     * <p>全参数构造函数。</p>
     *
     * @param id   主键 ID
     * @param text 目录文本
     * @param type 目录类型
     */
    public CatalogDTO(String id, String text, String type) {
        this.id = id;
        this.text = text;
        this.type = type;
    }

    /**
     * <p>不包含 ID 的构造函数。</p>
     *
     * @param text 目录文本
     * @param type 目录类型
     */
    public CatalogDTO(String text, String type) {
        this.text = text;
        this.type = type;
    }

    /**
     * <p>获取主键 ID。</p>
     *
     * @return 目录的 ID 字符串
     */
    public String getId() {
        return id;
    }

    /**
     * <p>设置主键 ID。</p>
     *
     * @param id 目录的 ID 字符串
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * <p>获取目录文本内容。</p>
     *
     * @return 目录显示的文本
     */
    public String getText() {
        return text;
    }

    /**
     * <p>设置目录文本内容。</p>
     *
     * @param text 目录显示的文本
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * <p>获取目录类型。</p>
     *
     * @return 目录类型字符串
     */
    public String getType() {
        return type;
    }

    /**
     * <p>设置目录类型。</p>
     *
     * @param type 目录类型字符串
     */
    public void setType(String type) {
        this.type = type;
    }
}
