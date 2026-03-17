package cn.net.pap.common.datastructure.rule;

/**
 * <p><strong>AbstractRule</strong> 是规则执行的基础抽象类。</p>
 *
 * <p>它定义了可以针对特定上下文进行评估的规则的约定。</p>
 *
 * @param <T> 规则评估的数据类型。
 */
public abstract class AbstractRule<T> {

    /**
     * <p>根据提供的 {@link RuleDTO} 执行规则逻辑。</p>
     *
     * @param ruleDTO 包含要评估的值的数据传输对象。
     * @return 如果规则通过则返回 <strong>true</strong>，否则返回 <strong>false</strong>。
     */
    public abstract boolean execute(RuleDTO<T> ruleDTO);
}
