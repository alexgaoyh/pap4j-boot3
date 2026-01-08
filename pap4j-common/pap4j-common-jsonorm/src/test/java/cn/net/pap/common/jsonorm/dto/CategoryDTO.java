package cn.net.pap.common.jsonorm.dto;

import java.util.List;

public class CategoryDTO {

    private String orderCode;

    private String levelCode;

    private String nodeName;

    private String fullPath;

    private Integer depth;

    private Integer categoryLevel;

    private String level1Name;

    private String level2Name;

    private String level3Name;

    private String level4Name;

    private String level5Name;

    private String level6Name;

    private List<CategoryDTO> children;

    // 构造函数
    public CategoryDTO() {
    }

    // Getters and Setters
    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public String getLevelCode() {
        return levelCode;
    }

    public void setLevelCode(String levelCode) {
        this.levelCode = levelCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Integer getCategoryLevel() {
        return categoryLevel;
    }

    public void setCategoryLevel(Integer categoryLevel) {
        this.categoryLevel = categoryLevel;
    }

    public String getLevel1Name() {
        return level1Name;
    }

    public void setLevel1Name(String level1Name) {
        this.level1Name = level1Name;
    }

    public String getLevel2Name() {
        return level2Name;
    }

    public void setLevel2Name(String level2Name) {
        this.level2Name = level2Name;
    }

    public String getLevel3Name() {
        return level3Name;
    }

    public void setLevel3Name(String level3Name) {
        this.level3Name = level3Name;
    }

    public String getLevel4Name() {
        return level4Name;
    }

    public void setLevel4Name(String level4Name) {
        this.level4Name = level4Name;
    }

    public String getLevel5Name() {
        return level5Name;
    }

    public void setLevel5Name(String level5Name) {
        this.level5Name = level5Name;
    }

    public String getLevel6Name() {
        return level6Name;
    }

    public void setLevel6Name(String level6Name) {
        this.level6Name = level6Name;
    }

    public List<CategoryDTO> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryDTO> children) {
        this.children = children;
    }

}
