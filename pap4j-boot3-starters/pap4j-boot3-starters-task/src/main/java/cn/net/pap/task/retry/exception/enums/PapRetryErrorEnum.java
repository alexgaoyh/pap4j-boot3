package cn.net.pap.task.retry.exception.enums;

/**
 * 重试滑动窗口断路器 枚举类
 */
public enum PapRetryErrorEnum {

    RETRY_CIRCUIT_OPEN("804", "断路器已打开!"),

    RETRY_FINAL_FAILURE("805", "重试调用最终失败!");

    private String value;
    private String desc;

    private PapRetryErrorEnum(String value, String desc) {
        this.setValue(value);
        this.setDesc(desc);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return "PapRetryErrorEnum{" +
                "value='" + value + '\'' +
                ", desc='" + desc + '\'' +
                '}';
    }
}
