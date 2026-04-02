package cn.net.pap.drools;

import cn.net.pap.drools.dto.OrderDTO;
import cn.net.pap.drools.entity.DroolsRule;
import cn.net.pap.drools.service.IDroolsRuleService;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {DroolsApplication.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class DroolsRuleTest {

    private static final Logger log = LoggerFactory.getLogger(DroolsRuleTest.class);

    private final IDroolsRuleService droolsRuleService;

    public DroolsRuleTest(IDroolsRuleService droolsRuleService) {
        this.droolsRuleService = droolsRuleService;
    }

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

    @Test
    public void countDownLatchTest() throws Exception {
        ExecutorService executor = new ThreadPoolExecutor(
                100,
                100,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(100),
                r -> new Thread(r, "countdownlatch-test-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch latch = new CountDownLatch(100);

        org.kie.internal.utils.KieHelper kieHelper1 = new org.kie.internal.utils.KieHelper();
        kieHelper1.addContent(read("discount.drl"), ResourceType.DRL);
        KieBase kieBase1 = kieHelper1.build();

        org.kie.internal.utils.KieHelper kieHelper2 = new org.kie.internal.utils.KieHelper();
        kieHelper2.addContent(read("spring.drl"), ResourceType.DRL);
        KieBase kieBase2 = kieHelper2.build();

        try {
            for (int i = 1; i <= 100; i++) {
                int finalI = -1;
                double randomNumber = new Random().nextDouble();
                if (randomNumber < 0.5) {
                    finalI = 1;
                } else {
                    finalI = 2;
                }
                int request = finalI;

                executor.execute(() -> {
                    if(request == 1) {
                        KieSession kieSession = kieBase1.newKieSession();
                        OrderDTO order = new OrderDTO();
                        order.setPrice(new BigDecimal((int)(Math.random() * (100)) + 100));
                        kieSession.insert(order);
                        kieSession.fireAllRules();
                        kieSession.dispose();
                        System.out.println("指定规则引擎后的结果：" + order.getDiscount());
                    } else {
                        KieSession kieSession = kieBase2.newKieSession();
                        OrderDTO order = new OrderDTO();
                        order.setPrice(new BigDecimal((int)(Math.random() * (100)) + 100));
                        kieSession.setGlobal("droolsRuleService", droolsRuleService);
                        kieSession.insert(order);
                        kieSession.fireAllRules();
                        kieSession.dispose();
                        System.out.println("指定规则引擎后的结果：" + order.getMessage());
                    }
                    latch.countDown();
                });
            }

            latch.await();
        } finally {
            executor.shutdown();
            try {
                // 等待 2 秒让未完成的任务结束
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                    log.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
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
