package cn.net.pap.common.datastructure.rule;

import java.util.List;

/**
 * <p><strong>AndCompositeRule</strong> 表示多个规则的逻辑 AND 组合。</p>
 *
 * <p>仅当其所有组件规则都评估为 <strong>true</strong> 时，此规则才评估为 <strong>true</strong>。</p>
 *
 * @param <T> 规则评估的数据类型。
 */
public class AndCompositeRule<T> extends CompositeRule<T> {

    /**
     * <p>从规则列表构造一个 AND 组合规则。</p>
     *
     * @param rules 要进行逻辑 AND 操作的 {@link AbstractRule} 实例的 {@link List}。
     */
    public AndCompositeRule(List<AbstractRule<T>> rules) {
        super(rules);
    }

    /**
     * <p>按顺序执行所有嵌入的规则。</p>
     *
     * <p>如果任何规则失败，它会短路并立即返回 <strong>false</strong>。</p>
     *
     * @param ruleDTO 包含要评估的值的数据传输对象。
     * @return 如果所有规则都通过，则返回 <strong>true</strong>，如果至少有一个失败，则返回 <strong>false</strong>。
     */
    @Override
    public boolean execute(RuleDTO<T> ruleDTO) {
        for (AbstractRule<T> rule : rules) {
            if (!rule.execute(ruleDTO)) {
                return false;
            }
        }
        return true;
    }
}
