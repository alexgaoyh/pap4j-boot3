package cn.net.pap.example.admin.config.validator.dto;

/**
 * 排序枚举类
 */
public enum OrderByEnum {

    DESC,
    ASC;

    /**
     * 如果枚举类发送变化，常量类同步发生变化
     */
    public static final String SPLIT = "DESC|ASC";

}
