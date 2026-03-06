package cn.net.pap.task.callable;

import cn.net.pap.task.callable.dto.TaskDTO;

public class SlowPapCallableImpl implements PapCallable<TaskDTO> {

    private TaskDTO taskDTO;

    public SlowPapCallableImpl(TaskDTO taskDTO) {
        this.taskDTO = taskDTO;
    }

    @Override
    public TaskDTO call() throws Exception {
        // 模拟耗时任务，强制让主线程的 future.get() 发生阻塞等待
        Thread.sleep(2000);
        return taskDTO;
    }

}