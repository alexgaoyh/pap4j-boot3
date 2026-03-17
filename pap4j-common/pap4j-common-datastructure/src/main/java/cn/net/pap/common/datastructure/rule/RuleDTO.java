package cn.net.pap.common.datastructure.rule;

import java.io.Serializable;

/**
 * <p><strong>RuleDTO</strong> 表示在规则执行期间使用的数据传输对象。</p>
 *
 * <p>它保存在将被 {@link AbstractRule} 实例评估的值。</p>
 *
 * @param <T> 所保存值的类型。
 */
public class RuleDTO<T> implements Serializable {

    /**
     * <p>要被评估的值。</p>
     */
    private T value;

    /**
     * <p>获取当前值。</p>
     *
     * @return 该值。
     */
    public T getValue() {
        return value;
    }

    /**
     * <p>设置要评估的值。</p>
     *
     * @param value 要设置的值。
     */
    public void setValue(T value) {
        this.value = value;
    }
}
