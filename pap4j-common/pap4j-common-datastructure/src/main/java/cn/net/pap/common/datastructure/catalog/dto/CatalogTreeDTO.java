package cn.net.pap.common.datastructure.catalog.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <h1>目录树属性结构 (Catalog Tree DTO)</h1>
 * <p>表示一个具备层级关系的树形目录节点数据传输对象。</p>
 * <p>除了包含基本属性外，还通过 {@link #pid} 和 {@link #children} 维护父子层级关系。</p>
 *
 * @author alexgaoyh
 */
public class CatalogTreeDTO implements Serializable {

    /**
     * <p>目录主键 ID。</p>
     */
    private String id;

    /**
     * <p>父节点主键 ID (Parent ID)。</p>
     */
    private String pid;

    /**
     * <p>目录显示的文本内容。</p>
     */
    private String text;

    /**
     * <p>目录类型。</p>
     */
    private String type;

    /**
     * <p>子目录节点列表。</p>
     */
    private List<CatalogTreeDTO> children;

    /**
     * <p>默认无参构造函数。</p>
     */
    public CatalogTreeDTO() {
    }

    /**
     * <p>指定主键、文本和类型的构造函数。</p>
     *
     * @param id   主键 ID
     * @param text 目录文本
     * @param type 目录类型
     */
    public CatalogTreeDTO(String id, String text, String type) {
        this.id = id;
        this.text = text;
        this.type = type;
    }

    /**
     * <p>不包含主键的构造函数。</p>
     *
     * @param text 目录文本
     * @param type 目录类型
     */
    public CatalogTreeDTO(String text, String type) {
        this.text = text;
        this.type = type;
    }

    /**
     * <p>获取主键 ID。</p>
     *
     * @return 目录的主键 ID
     */
    public String getId() {
        return id;
    }

    /**
     * <p>设置主键 ID。</p>
     *
     * @param id 目录的主键 ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * <p>获取父节点主键 ID。</p>
     *
     * @return 父节点的主键 ID
     */
    public String getPid() {
        return pid;
    }

    /**
     * <p>设置父节点主键 ID。</p>
     *
     * @param pid 父节点的主键 ID
     */
    public void setPid(String pid) {
        this.pid = pid;
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

    /**
     * <p>获取子目录节点列表。</p>
     *
     * @return 包含所有子节点的列表，可能为 {@code null}
     */
    public List<CatalogTreeDTO> getChildren() {
        return children;
    }

    /**
     * <p>设置子目录节点列表。</p>
     *
     * @param children 包含所有子节点的列表
     */
    public void setChildren(List<CatalogTreeDTO> children) {
        this.children = children;
    }

    /**
     * <p>向当前节点追加一个子节点。</p>
     * <p>如果当前的 {@link #children} 列表尚未初始化，则会先自动创建一个 {@link ArrayList}。</p>
     *
     * @param child 需要添加的子目录节点对象
     */
    public void addChild(CatalogTreeDTO child) {
        if(this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }

    /**
     * <p>返回该目录树节点的字符串表示。</p>
     *
     * @return 包含节点各属性值的字符串
     */
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
