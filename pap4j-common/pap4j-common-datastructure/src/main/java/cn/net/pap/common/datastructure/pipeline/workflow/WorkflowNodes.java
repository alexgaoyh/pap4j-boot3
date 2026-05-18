package cn.net.pap.common.datastructure.pipeline.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例工作流节点集合工厂
 */
public class WorkflowNodes {

    private static final Logger log = LoggerFactory.getLogger(WorkflowNodes.class);

    /**
     * 节点 1：身份风控校验（模拟失败场景）
     *
     * @param mockRiskPass 模拟是否通过风控
     * @return 风控校验节点
     */
    public static WorkflowNode checkRisk(boolean mockRiskPass) {
        return new WorkflowNode() {
            @Override
            public String name() {
                return "风控校验节点";
            }

            @Override
            public void execute(WorkflowContext context) {
                log.info("开始执行 [{}]", name());
                if (!mockRiskPass) {
                    context.interrupt(name(), "风控校验未通过：怀疑是刷单账号");
                    return;
                }
                context.put("riskPassed", true);
            }
        };
    }

    /**
     * 节点 2：计算商品价格
     *
     * @return 价格计算节点
     */
    public static WorkflowNode calculatePrice() {
        return new WorkflowNode() {
            @Override
            public String name() {
                return "计算价格节点";
            }

            @Override
            public void execute(WorkflowContext context) {
                log.info("开始执行 [{}]", name());
                context.put("finalPrice", 99.0);
            }
        };
    }

    /**
     * 节点 3：扣减库存
     *
     * @return 库存扣减节点
     */
    public static WorkflowNode reduceStock() {
        return new WorkflowNode() {
            @Override
            public String name() {
                return "库存扣减节点";
            }

            @Override
            public void execute(WorkflowContext context) {
                log.info("开始执行 [{}]", name());
                // 测试类型安全获取和 null 写入
                Double price = context.get("finalPrice", Double.class);
                if (price != null) {
                    log.info("商品价格为: {}", price);
                }
                context.put("stockReduced", true);
                context.put("tempVar", null); // 测试空值存入机制
            }
        };
    }

    /**
     * 节点 4：异常抛出测试
     *
     * @return 异常抛出节点
     */
    public static WorkflowNode mockException() {
        return new WorkflowNode() {
            @Override
            public String name() {
                return "模拟数据库请求节点";
            }

            @Override
            public void execute(WorkflowContext context) throws Exception {
                log.info("开始执行 [{}]", name());
                // 这里可以直接抛出受检异常，无需封装 RuntimeException
                throw new java.sql.SQLException("模拟数据库连接超时");
            }
        };
    }
}
