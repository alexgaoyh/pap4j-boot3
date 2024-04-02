package cn.net.pap.drools;

import cn.net.pap.drools.dto.OrderDTO;
import cn.net.pap.drools.entity.DroolsRule;
import cn.net.pap.drools.service.IDroolsRuleService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.KieBase;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {DroolsApplication.class})
public class DroolsRuleTest {

    @Autowired
    private IDroolsRuleService droolsRuleService;

    @Test
    public void crudDB() {
        DroolsRule droolsRule = new DroolsRule();
        droolsRule.setRuleId(1l);
        droolsRule.setRuleContent("crudDB");
        droolsRule.setKiePackageName("cn.net.pap.drools");
        droolsRule.setKieBaseName("crudDB");
        droolsRule.setCreatedTime(new Date());

        droolsRuleService.save(droolsRule);

        DroolsRule byIdDB1 = droolsRuleService.findById(1l);
        assertTrue(byIdDB1.getRuleId() == 1l);

        droolsRule.setUpdateTime(new Date());
        droolsRuleService.edit(droolsRule);

        DroolsRule byIdDB2 = droolsRuleService.findById(1l);
        assertTrue(byIdDB2.getRuleId() == 1l);

        List<DroolsRule> all = droolsRuleService.findAll();
        assertTrue(all.size() == 1);

        boolean b = droolsRuleService.deleteById(1l);
        assertTrue(b == true);

        DroolsRule byIdDB3 = droolsRuleService.findById(1l);
        assertTrue(byIdDB3 == null);

    }

    @Test
    public void discountDroolsTest() {
        org.kie.internal.utils.KieHelper kieHelper = new org.kie.internal.utils.KieHelper();
//        Resource resource = ResourceFactory.newClassPathResource("discount.drl");
//        kieHelper.addResource(resource);
        kieHelper.addContent(read("discount.drl"), ResourceType.DRL);
        Results results = kieHelper.verify();
        if (results.hasMessages(Message.Level.ERROR)) {
            assertTrue(false, "drl file error : " + results.getMessages(Message.Level.ERROR));
        } else {
            KieBase kieBase = kieHelper.build();
            KieSession kieSession = kieBase.newKieSession();
            OrderDTO order = new OrderDTO();
            order.setPrice(new BigDecimal(150));
            kieSession.insert(order);
            kieSession.fireAllRules();
            kieSession.dispose();
            System.out.println("指定规则引擎后的结果：" + order.getDiscount());
        }

    }

    @Test
    public void springDroolsTest() {
        org.kie.internal.utils.KieHelper kieHelper = new org.kie.internal.utils.KieHelper();
        kieHelper.addContent(read("spring.drl"), ResourceType.DRL);
        Results results = kieHelper.verify();
        if (results.hasMessages(Message.Level.ERROR)) {
            assertTrue(false, "drl file error : " + results.getMessages(Message.Level.ERROR));
        } else {
            KieBase kieBase = kieHelper.build();
            KieSession kieSession = kieBase.newKieSession();
            OrderDTO order = new OrderDTO();
            order.setPrice(new BigDecimal(150));
            kieSession.setGlobal("droolsRuleService", droolsRuleService);
            kieSession.insert(order);
            kieSession.fireAllRules();
            kieSession.dispose();
            System.out.println("指定规则引擎后的结果：" + order.getMessage());
        }

    }


    private String read(String fileName) {
        StringBuffer sb = new StringBuffer();
        try (InputStream fileIs = this.getClass().getClassLoader().getResourceAsStream(fileName)) {
            final int maxSize = 1024;
            byte[] buff = new byte[maxSize];
            int read = 0;

            while ((read = fileIs.read(buff, 0, maxSize)) > 0) {
                sb.append(new String(buff, 0, read));
            }
        } catch (IOException e) {
        }
        return sb.toString();
    }

}
