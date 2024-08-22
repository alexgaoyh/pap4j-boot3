package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.rule.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class RuleTest {

    @Test
    public void test() {
        RuleDTO<String> ruleDTO = new RuleDTO<>();
        ruleDTO.setValue("alexgaoyh");

        AbstractRule<String> equalsTestRule = new EqualsRule("alexgaoyh");
        AbstractRule<String> equalsExampleRule = new EqualsRule("pap.net.cn");

        CompositeRule<String> andRule = new AndCompositeRule<>(Arrays.asList(equalsTestRule, equalsExampleRule));

        CompositeRule<String> orRule = new OrCompositeRule<>(Arrays.asList(equalsTestRule, equalsExampleRule));

        System.out.println("AND Rule Result: " + andRule.execute(ruleDTO));

        System.out.println("OR Rule Result: " + orRule.execute(ruleDTO));

        CompositeRule<String> nestedRule = new AndCompositeRule<>(Arrays.asList(
                orRule, new EqualsRule("alexgaoyh")
        ));

        System.out.println("Nested Rule Result: " + nestedRule.execute(ruleDTO));

    }
}
