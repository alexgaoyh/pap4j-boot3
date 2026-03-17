package cn.net.pap.common.datastructure.rule;

import java.util.List;

/**
 * <p><strong>OrCompositeRule</strong> 表示多个规则的逻辑 OR 组合。</p>
 *
 * <p>如果其至少一个组件规则评估为 <strong>true</strong>，则此规则评估为 <strong>true</strong>。</p>
 *
 * @param <T> 规则评估的数据类型。
 */
public class OrCompositeRule<T> extends CompositeRule<T> {

    /**
     * <p>从规则列表构造一个 OR 组合规则。</p>
     *
     * @param rules 要进行逻辑 OR 操作的 {@link AbstractRule} 实例的 {@link List}。
     */
    public OrCompositeRule(List<AbstractRule<T>> rules) {
        super(rules);
    }

    /**
     * <p>按顺序执行所有嵌入的规则。</p>
     *
     * <p>如果任何规则通过，它会短路并立即返回 <strong>true</strong>。</p>
     *
     * @param ruleDTO 包含要评估的值的数据传输对象。
     * @return 如果至少有一个规则通过，则返回 <strong>true</strong>，否则返回 <strong>false</strong>。
     */
    @Override
    public boolean execute(RuleDTO<T> ruleDTO) {
        for (AbstractRule<T> rule : rules) {
            if (rule.execute(ruleDTO)) {
                return true;
            }
        }
        return false;
    }
}
