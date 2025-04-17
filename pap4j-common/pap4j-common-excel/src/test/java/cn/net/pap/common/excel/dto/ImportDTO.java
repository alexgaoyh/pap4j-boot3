package cn.net.pap.common.excel.dto;

import com.alibaba.excel.annotation.ExcelProperty;

import java.io.Serializable;

/**
 * 图像导入测试
 */
public class ImportDTO implements Serializable {

    /**
     *
     */
    @ExcelProperty(index = 0)
    private String name;

    /**
     * 使用byte[]存储图片数据
     */
    @ExcelProperty(index = 1)
    private byte[] picture;

    private String imageType;
    /**
     * 无参构造器
     */
    public ImportDTO() {
    }

    // 全参构造器
    public ImportDTO(String name, byte[] picture, String imageType) {
        this.name = name;
        this.picture = picture;
        this.imageType = imageType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getPicture() {
        return picture;
    }

    public void setPicture(byte[] picture) {
        this.picture = picture;
    }

    public String getImageType() {
        return imageType;
    }

    public void setImageType(String imageType) {
        this.imageType = imageType;
    }

}
