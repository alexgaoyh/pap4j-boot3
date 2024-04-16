package cn.net.pap.common.excel.dto;

import java.io.File;
import java.io.Serializable;

/**
 * 图像导出测试 - 等比例缩放
 */
public class ExportDTO implements Serializable {

    private File picture;

    public ExportDTO(File picture) {
        this.picture = picture;
    }

    public File getPicture() {
        return picture;
    }

    public void setPicture(File picture) {
        this.picture = picture;
    }

}
