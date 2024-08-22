package cn.net.pap.common.datastructure.rule;

import java.util.List;

/**
 * 或 关系
 *
 * @param <T>
 */
public class OrCompositeRule<T> extends CompositeRule<T> {

    public OrCompositeRule(List<AbstractRule<T>> rules) {
        super(rules);
    }

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
