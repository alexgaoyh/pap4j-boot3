package cn.net.pap.common.jsonorm.util.dto;

import java.io.Serializable;

/**
 * Validation 对象
 */
public class ValidationDTO implements Serializable {

    /**
     * 类型
     */
    private String type;

    /**
     * message
     */
    private String message;

    /**
     * 最小值
     */
    private Integer min;

    /**
     * 最大值
     */
    private Integer max;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getMin() {
        return min;
    }

    public void setMin(Integer min) {
        this.min = min;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

}
