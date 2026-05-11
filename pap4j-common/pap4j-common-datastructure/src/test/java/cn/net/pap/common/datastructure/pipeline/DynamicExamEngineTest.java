package cn.net.pap.common.datastructure.pipeline;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * {@link DynamicExamEngine} 的单元测试。
 * <p>
 * 该测试演示了一个<b>轻量级、可扩展的动态流水线引擎（Pipeline Engine）</b>在“考试/考级流程”中的应用。
 * 核心功能演示如下：
 * </p>
 * <ul>
 *     <li><b>流程的按序执行与上下文传递：</b> 引擎按照节点顺序推进，并通过全局的 {@link StudentContext} 实现跨节点的状态（如分数）读写与共享。</li>
 *     <li><b>插件化的动态分发机制：</b> 彻底消除硬编码，采用策略模式与注册表机制。引擎通过 {@link TaskHandler#supports(String)} 动态寻找匹配的处理器执行任务，符合开闭原则。</li>
 *     <li><b>条件分支与节点跳过：</b> 演示了基于上下文规则判断的跳过逻辑（如“免考特权” {@code skipCondition="has_exemption"}）。</li>
 *     <li><b><font color="red">挂起与恢复机制（断点续传/异步回调）</font>：</b> 
 *         引擎最亮眼的能力。当遇到如人工阅卷（{@link ManualGradingHandler}）这类需要外部介入的任务时，
 *         引擎会返回 {@link TaskAction#SUSPEND} 并立即停止执行释放资源，同时返回当前游标位置。
 *         外部条件满足后，可利用保存的游标重新调用引擎，实现精准的断点唤醒。
 *     </li>
 * </ul>
 */
public class DynamicExamEngineTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicExamEngineTest.class);

    @Test
    public void testEngineWithPlugins() {
        // 1. 实例化引擎
        DynamicExamEngine engine = new DynamicExamEngine();

        // 2. 装载插件 (这一步非常灵活，你甚至可以利用反射或 SPI 机制自动扫描)
        engine.registerHandler(new AutoExamHandler());
        engine.registerHandler(new ManualGradingHandler());
        engine.registerHandler(new CertificateHandler());

        // 3. 准备数据并运行
        List<ExamNode> pipeline = List.of(
                new ExamNode("一级考试", "auto_exam", null),
                new ExamNode("二级考试", "auto_exam", "has_exemption"),
                new ExamNode("三级主观题", "manual_grading", null),
                new ExamNode("四级证书", "issue_certificate", null)
        );
        StudentContext context = new StudentContext("instance-001", "Student-LiLei");

        log.info("====== 李雷开始了他的考级之旅 ======");
        EngineResult firstRunResult = engine.run(pipeline, context, 0);

        log.info("第一次运行结果: " + firstRunResult);

        // ---------------- 模拟时间流逝 ----------------
        log.info("... (过了三天，老师在后台看完了李雷的三级作文，点击了“及格”) ...");
        context.data().put("三级主观题_score", 90); // 老师打分落盘，数据写入上下文

        // ---------------- 恢复流程 ----------------
        log.info("====== 收到老师回调，从断点唤醒流水线 ======");
        int savedCursor = firstRunResult.stoppedCursor();

        // 直接使用 savedCursor，不加 1！ 让流程重新进入“三级主观题”节点，让该节点自己完成闭环检查。
        EngineResult secondRunResult = engine.run(pipeline, context, savedCursor);

        log.info("最终运行结果: " + secondRunResult);
        log.info("李雷的最终档案: " + context.data());
    }

}
