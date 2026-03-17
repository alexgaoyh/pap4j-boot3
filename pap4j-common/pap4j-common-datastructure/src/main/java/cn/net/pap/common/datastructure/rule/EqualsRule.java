package cn.net.pap.common.datastructure.rule;

/**
 * <p><strong>EqualsRule</strong> 是 {@link AbstractRule} 的具体实现。</p>
 *
 * <p>它对预期值执行简单的字符串相等性检查。</p>
 */
public class EqualsRule extends AbstractRule<String> {

    /**
     * <p>预期的目标字符串。</p>
     */
    private final String expectedValue;

    /**
     * <p>使用所需的目标值构造一个新的 <strong>EqualsRule</strong>。</p>
     *
     * @param expectedValue 要比较的目标字符串。
     */
    public EqualsRule(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    /**
     * <p>评估提供的 DTO 的值是否严格等于预期的字符串。</p>
     *
     * @param ruleDTO 包含要检查的字符串值的 {@link RuleDTO}。
     * @return 如果字符串匹配则返回 <strong>true</strong>，否则返回 <strong>false</strong>。
     */
    @Override
    public boolean execute(RuleDTO<String> ruleDTO) {
        return expectedValue.equals(ruleDTO.getValue());
    }

}
