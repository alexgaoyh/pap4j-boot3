package cn.net.pap.common.excel.dto;

import java.io.Serializable;

public class CompareDTO implements Serializable {

    private String sourceField;

    private String targetField;

    public CompareDTO(String sourceField, String targetField) {
        this.sourceField = sourceField;
        this.targetField = targetField;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }

}
