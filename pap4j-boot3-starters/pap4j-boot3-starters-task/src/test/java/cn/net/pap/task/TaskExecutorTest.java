package cn.net.pap.task;

import cn.net.pap.task.callable.PapCallable;
import cn.net.pap.task.callable.PapCallableImpl;
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

        TaskEnums taskEnums = TaskExecutorUtil.executeTasks("PAP-THREAD-BEAN_NAME", tasks);
        System.out.println(taskEnums);
    }

}
