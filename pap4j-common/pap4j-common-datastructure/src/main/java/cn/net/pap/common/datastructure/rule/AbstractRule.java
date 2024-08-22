package cn.net.pap.common.datastructure.rule;

/**
 * 规则执行器 抽象类
 *
 * @param <T>
 */
public abstract class AbstractRule<T> {

    /**
     * 执行
     *
     * @param ruleDTO 规则dto
     * @return {@link Boolean}
     */
    public abstract boolean execute(RuleDTO<T> ruleDTO);
}
