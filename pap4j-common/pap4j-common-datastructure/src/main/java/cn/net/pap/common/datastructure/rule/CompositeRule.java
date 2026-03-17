package cn.net.pap.common.datastructure.rule;

import java.util.List;

/**
 * <p><strong>CompositeRule</strong> 是组合模式规则的抽象基类。</p>
 *
 * <p>它包含其他规则的集合，允许创建复杂的规则树（例如，AND、OR 逻辑）。</p>
 *
 * @param <T> 规则评估的数据类型。
 */
public abstract class CompositeRule<T> extends AbstractRule<T> {

    /**
     * <p>包含的规则的内部列表。</p>
     */
    protected final List<AbstractRule<T>> rules;

    /**
     * <p>构造一个包含给定规则列表的组合规则。</p>
     *
     * @param rules 子 {@link AbstractRule} 元素的 {@link List}。
     */
    public CompositeRule(List<AbstractRule<T>> rules) {
        this.rules = rules;
    }

}
