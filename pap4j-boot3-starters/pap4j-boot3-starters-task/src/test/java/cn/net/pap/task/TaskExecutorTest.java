package cn.net.pap.task;

import cn.net.pap.task.callable.PapCallable;
import cn.net.pap.task.callable.PapCallableImpl;
import cn.net.pap.task.callable.SlowPapCallableImpl;
import cn.net.pap.task.callable.dto.TaskDTO;
import cn.net.pap.task.enums.TaskEnums;
import cn.net.pap.task.executor.TaskExecutorUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.task.TaskApplication.class})
@TestPropertySource("classpath:application.properties")
public class TaskExecutorTest {

    @Test
    public void test1() throws Exception {
        List<PapCallable<TaskDTO>> tasks = new ArrayList<>();
        for (int idx = 0; idx < 1000; idx++) {
            TaskDTO taskDTO = new TaskDTO();
            taskDTO.setId(idx + "");
            tasks.add(new PapCallableImpl(taskDTO));
        }

        List<TaskExecutorUtil.TaskResult<TaskDTO>> taskEnums = TaskExecutorUtil.executeTasks("PAP-THREAD-BEAN_NAME", tasks);
        System.out.println(taskEnums);
    }

    @Test
    public void testInterruptBreak() throws Exception {
        // 1. 准备 10 个耗时任务
        List<PapCallable<TaskDTO>> tasks = new ArrayList<>();
        int taskCount = 10;
        for (int idx = 0; idx < taskCount; idx++) {
            TaskDTO taskDTO = new TaskDTO();
            taskDTO.setId(idx + "");
            tasks.add(new SlowPapCallableImpl(taskDTO));
        }

        // 声明一个数组用于在内部线程中接收返回值
        final List<TaskExecutorUtil.TaskResult<TaskDTO>>[] resultHolder = new List[1];

        // 2. 另起一个线程（模拟正在处理业务逻辑的 Web 线程），去执行批量任务
        Thread executeThread = new Thread(() -> {
            resultHolder[0] = TaskExecutorUtil.executeTasks("PAP-THREAD-BEAN_NAME", tasks);
        });
        executeThread.start();

        // 3. 主测试线程休眠 500 毫秒，确保 executeThread 已经提交完任务，并且正阻塞在第一个 future.get() 上
        Thread.sleep(500);

        // 4. 关键动作：打断正在阻塞等待的 executeThread
        System.out.println("===== 外部系统发出中断信号 =====");
        executeThread.interrupt();

        // 5. 等待 executeThread 优雅退出
        executeThread.join();

        // 6. 获取结果并验证
        List<TaskExecutorUtil.TaskResult<TaskDTO>> results = resultHolder[0];
        System.out.println("最终的任务状态列表: ");
        for (int i = 0; i < results.size(); i++) {
            System.out.println("任务[" + i + "]: " + results.get(i).getStatus().getException());
        }

        // ===== 核心断言：验证 break 的作用 =====
        int notExecutedCount = 0;
        for (TaskExecutorUtil.TaskResult<TaskDTO> taskResult : results) {
            // 如果 break 生效，跳出循环后，剩余没被 get() 遍历到的任务必定保持初始值 "Task not executed"
            if ("Task not executed".equals(taskResult.getStatus().getException())) {
                notExecutedCount++;
            }
        }

        System.out.println("\n未被执行的剩余初始状态任务数: " + notExecutedCount);

        // 如果没有 break，后续的 future.get() 会瞬间连续抛出 InterruptedException 或 CancellationException
        // 那么 notExecutedCount 将会是 0。
        // 所以只要这个断言通过（存在 >0 个初始状态的任务），就证明 break 完美生效，成功阻断了异常蔓延！
        assertTrue(notExecutedCount > 0, "Break 机制未生效，后续任务状态被异常覆盖了！");
    }

}
