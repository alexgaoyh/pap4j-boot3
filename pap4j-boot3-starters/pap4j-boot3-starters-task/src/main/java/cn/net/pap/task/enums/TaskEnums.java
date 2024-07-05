package cn.net.pap.task.enums;

/**
 * 任务返回状态枚举类
 */
public enum TaskEnums {

    REJECT("500", "拒绝", "reject"),

    UNKNOWN("600", "未知", "unknown"),

    SUCCESS("200", "成功", "success");

    private String code;

    private String msg;

    private String exception;

    TaskEnums(String code, String msg, String exception) {
        this.code = code;
        this.msg = msg;
        this.exception = exception;
    }

    public static TaskEnums reject(String exception) {
        TaskEnums reject = TaskEnums.REJECT;
        reject.exception = exception;
        return reject;
    }

    public static TaskEnums unknown(String exception) {
        TaskEnums unknown = TaskEnums.UNKNOWN;
        unknown.exception = exception;
        return unknown;
    }

}
