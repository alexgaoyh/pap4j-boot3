package cn.net.pap.task.enums;

/**
 * 任务返回状态枚举类
 * 在 Java 语言规范中，枚举类（Enum）天生是全局单例的（Singleton）。
 * 当 JVM 加载 TaskEnums 类时，会在堆内存中为 REJECT、UNKNOWN、SUCCESS 等枚举项各初始化唯一的一个对象实例。
 * 无论你在代码的哪个类、哪个线程里调用 TaskEnums.UNKNOWN，拿到的永远是内存里的同一个对象引用。
 */
public enum TaskEnums {
    SUCCESS("200", "成功", "success"),

    NOT_EXECUTED("590", "任务未执行", "Task not executed"),

    REJECT("500", "被线程池拒绝", "Task rejected by pool"),

    UNKNOWN("600", "未知致命异常", "Unknown system error"),

    EXECUTION_FAILED("601", "任务执行报错", "Task execution failed"),

    INTERRUPTED("602", "任务被外部中断", "Task interrupted by external signal"),

    TIMEOUT("604", "任务执行超时", "Timeout waiting for task result");

    // 必须加 final！绝对不允许修改！
    private final String code;

    private final String msg;

    // 枚举只负责“静态死状态”，不负责“动态活数据”：
    // 枚举的职责是定义像“红、黄、绿”或“状态码 200、500”这样有限的、固定的类别。
    // 绝不能试图把每次执行都不一样的动态数据（如千变万化的 e.getMessage() 异常详情、时间戳、流水号等）塞进枚举中。
    private final String exception;

    TaskEnums(String code, String msg, String exception) {
        this.code = code;
        this.msg = msg;
        this.exception = exception;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public String getException() {
        return exception;
    }

}
