package cn.net.pap.common.datastructure.rule;

/**
 * equals 规则执行器
 */
public class EqualsRule extends AbstractRule<String> {

    private final String expectedValue;

    public EqualsRule(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    @Override
    public boolean execute(RuleDTO<String> ruleDTO) {
        return expectedValue.equals(ruleDTO.getValue());
    }

}
