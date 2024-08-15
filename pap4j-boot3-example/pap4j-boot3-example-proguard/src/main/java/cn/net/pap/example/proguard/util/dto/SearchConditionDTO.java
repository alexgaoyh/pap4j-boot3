package cn.net.pap.example.proguard.util.dto;

import java.io.Serializable;

public class SearchConditionDTO implements Serializable {
    private String field;
    private Operator operator;
    private Object value;

    public enum Operator {
        EQUAL,
        NOT_EQUAL,
        LIKE,
        NOT_LIKE,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL_TO,
        LESS_THAN,
        LESS_THAN_OR_EQUAL_TO,
        GT,
        LT,
        GE,
        LE,
        // Add more operators as needed
    }

    public SearchConditionDTO(String field, Operator operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public Operator getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

}
