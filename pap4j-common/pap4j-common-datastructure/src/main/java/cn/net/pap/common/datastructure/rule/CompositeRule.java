package cn.net.pap.common.datastructure.rule;

import java.util.List;

/**
 * 嵌套规则
 *
 * @param <T>
 */
public abstract class CompositeRule<T> extends AbstractRule<T> {

    protected final List<AbstractRule<T>> rules;

    public CompositeRule(List<AbstractRule<T>> rules) {
        this.rules = rules;
    }

}
