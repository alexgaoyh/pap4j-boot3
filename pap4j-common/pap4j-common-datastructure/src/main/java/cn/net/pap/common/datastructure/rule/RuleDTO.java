package cn.net.pap.common.datastructure.rule;

import java.io.Serializable;

/**
 * 规则对象
 *
 * @param <T>
 */
public class RuleDTO<T> implements Serializable {

    /**
     * 值
     */
    private T value;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
