package cn.net.pap.common.datastructure.pipeline.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkflowEngineTest {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineTest.class);

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("简单工作流普通测试1")
    public void simple1Test() {
        log.info("================== 案例 1：正常通过流程 ==================");
        WorkflowContext context1 = new WorkflowContext();
        WorkflowEngine.execute(context1,
                WorkflowNodes.checkRisk(true),
                WorkflowNodes.calculatePrice(),
                WorkflowNodes.reduceStock()
        );
        assertEquals(WorkflowStatus.SUCCESS, context1.getStatus());
        assertTrue(context1.get("stockReduced", Boolean.class));
        assertNull(context1.get("tempVar")); // 验证 NULL 安全处理机制
        log.info("案例 1 结果 -> 状态: {}\n", context1.getStatus());


        log.info("================== 案例 2：风控拦截中断流程 ==================");
        WorkflowContext context2 = new WorkflowContext();
        WorkflowEngine.execute(context2,
                WorkflowNodes.checkRisk(false),
                WorkflowNodes.calculatePrice(),
                WorkflowNodes.reduceStock()
        );

        assertEquals(WorkflowStatus.INTERRUPTED, context2.getStatus());
        assertEquals("风控校验节点", context2.getErrorNode());
        if (context2.getStatus() == WorkflowStatus.INTERRUPTED) {
            log.error("【用户提示】中断节点: [{}], 原因：{}", context2.getErrorNode(), context2.getMessage());
        }
        log.info("\n");


        log.info("================== 案例 3：系统异常失败流程 ==================");
        WorkflowContext context3 = new WorkflowContext();
        WorkflowEngine.execute(context3,
                WorkflowNodes.checkRisk(true),
                WorkflowNodes.mockException(),
                WorkflowNodes.reduceStock()
        );
        assertEquals(WorkflowStatus.FAILED, context3.getStatus());
        assertEquals("模拟数据库请求节点", context3.getErrorNode());
        log.error("案例 3 结果 -> 异常节点: [{}], 提示: {}\n", context3.getErrorNode(), context3.getMessage());
    }

    @Test
    @DisplayName("简单工作流持久化测试1")
    public void persistence1Test() throws Exception {
        log.info("================== 案例 4：工作流持久化与断点续传测试 ==================");

        // 1. 初始化上下文
        WorkflowContext originalContext = new WorkflowContext();
        originalContext.put("orderId", "ORDER_123456");

        log.info("--- 阶段 1：模拟机器A执行部分工作流 ---");
        // 假设当前批次只要求执行前两个节点
        WorkflowEngine.execute(originalContext,
                WorkflowNodes.checkRisk(true),
                WorkflowNodes.calculatePrice()
        );

        // 此时状态为 SUCCESS，且前两个节点被记录到了 executedNodes
        log.info("序列化前上下文数据: {}", originalContext);

        // 2. 将工作流状态序列化为 JSON（模拟存入数据库）
        String json = objectMapper.writeValueAsString(originalContext);
        log.info("模拟存入数据库的 JSON 字符串: \n{}", json);

        // 3. 将 JSON 反序列化为对象（模拟机器B从数据库读取）
        WorkflowContext restoredContext = objectMapper.readValue(json, WorkflowContext.class);
        log.info("从数据库恢复后的上下文: {}", restoredContext);

        // 4. 断言验证恢复的正确性
        assertNotNull(restoredContext);
        assertEquals(originalContext.getStatus(), restoredContext.getStatus());
        assertEquals(originalContext.getMessage(), restoredContext.getMessage());
        assertTrue(restoredContext.getExecutedNodes().contains("风控校验节点"));
        assertTrue(restoredContext.getExecutedNodes().contains("计算价格节点"));

        // 5. 接着使用恢复后的上下文继续执行
        log.info("--- 阶段 2：模拟机器B拉起整个工作流引擎进行断点续传 ---");
        // 这次我们将**全部**的三个节点都传递给引擎。
        // 预期行为：引擎会自动把 SUCCESS 唤醒为 RUNNING，并且自动跳过前两个已执行过的节点，仅执行库存扣减。
        WorkflowEngine.execute(restoredContext,
                WorkflowNodes.checkRisk(true),
                WorkflowNodes.calculatePrice(),
                WorkflowNodes.reduceStock()
        );

        assertEquals(WorkflowStatus.SUCCESS, restoredContext.getStatus());
        assertTrue(restoredContext.get("stockReduced", Boolean.class));

        log.info("案例 4 结果 -> 持久化恢复后断点续传执行完毕，最终状态: {}\n", restoredContext.getStatus());
    }

    @Test
    @DisplayName("简单工作流重试测试1")
    public void retryOnFailure1Test() throws Exception {
        log.info("================== 案例 5：异常节点的重试机制测试 ==================");
        WorkflowContext context = new WorkflowContext();

        // 用一个外部变量控制异常是否抛出（模拟网络抖动）
        java.util.concurrent.atomic.AtomicBoolean failNow = new java.util.concurrent.atomic.AtomicBoolean(true);

        WorkflowNode flakyNode = new WorkflowNode() {
            @Override
            public String name() {
                return "不稳定节点";
            }

            @Override
            public void execute(WorkflowContext ctx) throws Exception {
                log.info("开始执行 [{}]", name());
                if (failNow.get()) {
                    throw new RuntimeException("模拟网络抖动");
                }
                ctx.put("flakyResult", "OK");
            }
        };

        log.info("--- 第一次执行：遇到网络抖动 ---");
        WorkflowEngine.execute(context,
                WorkflowNodes.checkRisk(true),
                flakyNode,
                WorkflowNodes.reduceStock()
        );

        // 断言第一次执行的结果
        assertEquals(WorkflowStatus.FAILED, context.getStatus());
        assertEquals("不稳定节点", context.getErrorNode());
        assertTrue(context.getExecutedNodes().contains("风控校验节点"));

        log.info("--- 网络恢复，发起第二次重试 ---");
        failNow.set(false);

        // 再次丢进引擎重试
        WorkflowEngine.execute(context,
                WorkflowNodes.checkRisk(true),
                flakyNode,
                WorkflowNodes.reduceStock()
        );

        // 断言重试的结果
        assertEquals(WorkflowStatus.SUCCESS, context.getStatus());
        assertNull(context.getErrorNode()); // 之前的错误记录应该被自动清理
        assertTrue(context.getExecutedNodes().contains("不稳定节点"));
        assertTrue(context.getExecutedNodes().contains("库存扣减节点"));
        assertEquals("OK", context.get("flakyResult"));
        log.info("案例 5 结果 -> 重试成功，最终状态: {}\n", context.getStatus());
    }

    @Test
    public void testTimeout() throws Exception {
        log.info("================== 案例 6：节点超时熔断控制测试 ==================");
        WorkflowContext context = new WorkflowContext();

        WorkflowNode slowNode = new WorkflowNode() {
            @Override
            public String name() { return "慢查询节点"; }
            
            @Override
            public long timeoutMillis() { return 500L; } // 配置 500ms 超时

            @Override
            public void execute(WorkflowContext ctx) throws Exception {
                log.info("开始执行 [{}]", name());
                try {
                    // 模拟长达 2000ms 的长耗时任务
                    Thread.sleep(2000); 
                } catch (InterruptedException e) {
                    log.warn("[{}] 响应中断信号，已被引擎主动阻断！", name());
                    Thread.currentThread().interrupt(); // 恢复中断标志
                    throw e; // 继续抛出，由引擎拦截
                }
                ctx.put("slowData", "Loaded");
            }
        };

        WorkflowEngine.execute(context, 
                WorkflowNodes.checkRisk(true), 
                slowNode, 
                WorkflowNodes.reduceStock()
        );
        
        // 断言：引擎应该成功拦截并标记状态为 FAILED，且阻断后续节点
        assertEquals(WorkflowStatus.FAILED, context.getStatus());
        assertEquals("慢查询节点", context.getErrorNode());
        assertTrue(context.getMessage().contains("执行超时熔断"));
        
        // 由于被熔断，slowData 未能被放入，库存节点也未被执行
        assertNull(context.get("slowData"));
        assertTrue(!context.getExecutedNodes().contains("库存扣减节点"));
        log.info("案例 6 结果 -> 超时熔断成功，最终状态: {}, 错误信息: {}\n", context.getStatus(), context.getMessage());
    }

}
