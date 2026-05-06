package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.rule.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class RuleTest {

    private static final Logger log = LoggerFactory.getLogger(RuleTest.class);

    @Test
    public void test() {
        RuleDTO<String> ruleDTO = new RuleDTO<>();
        ruleDTO.setValue("alexgaoyh");

        AbstractRule<String> equalsTestRule = new EqualsRule("alexgaoyh");
        AbstractRule<String> equalsExampleRule = new EqualsRule("pap.net.cn");

        CompositeRule<String> andRule = new AndCompositeRule<>(Arrays.asList(equalsTestRule, equalsExampleRule));

        CompositeRule<String> orRule = new OrCompositeRule<>(Arrays.asList(equalsTestRule, equalsExampleRule));

        log.info("AND Rule Result: {}", andRule.execute(ruleDTO));

        log.info("OR Rule Result: {}", orRule.execute(ruleDTO));

        CompositeRule<String> nestedRule = new AndCompositeRule<>(Arrays.asList(
                orRule, new EqualsRule("alexgaoyh")
        ));

        log.info("Nested Rule Result: {}", nestedRule.execute(ruleDTO));

    }
}
