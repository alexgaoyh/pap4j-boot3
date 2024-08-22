package cn.net.pap.common.datastructure.rule;

import java.util.List;

/**
 * 与 关系
 *
 * @param <T>
 */
public class AndCompositeRule<T> extends CompositeRule<T> {

    public AndCompositeRule(List<AbstractRule<T>> rules) {
        super(rules);
    }

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
