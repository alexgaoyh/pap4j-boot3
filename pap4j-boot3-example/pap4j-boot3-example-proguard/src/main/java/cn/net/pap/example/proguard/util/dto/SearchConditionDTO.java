package cn.net.pap.example.proguard.util.dto;

import java.io.Serializable;

public class SearchConditionDTO implements Serializable {
    private String field;
    private Operator operator;
    private Object value;

    public enum Operator {
        EQUAL,
        LIKE,
        GREATER_THAN,
        LESS_THAN,
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
