package cn.net.pap.example.enums;

public enum EventType {
    // 用户相关事件
    USER_REGISTERED("user.registered"),
    USER_UPDATED("user.updated"),

    // 订单相关事件
    ORDER_CREATED("order.created"),
    ORDER_PAID("order.paid"),
    ORDER_CANCELLED("order.cancelled"),

    // 产品相关事件
    PRODUCT_CREATED("product.created"),
    PRODUCT_UPDATED("product.updated"),

    // 系统事件
    SYSTEM_BACKUP("system.backup"),
    SYSTEM_ALERT("system.alert");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
