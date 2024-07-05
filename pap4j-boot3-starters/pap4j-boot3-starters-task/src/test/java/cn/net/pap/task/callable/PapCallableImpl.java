package cn.net.pap.task.callable;

import cn.net.pap.task.callable.dto.TaskDTO;

public class PapCallableImpl implements PapCallable<TaskDTO> {

    private TaskDTO taskDTO;

    public PapCallableImpl(TaskDTO taskDTO) {
        this.taskDTO = taskDTO;
    }

    @Override
    public TaskDTO call() throws Exception {
        System.out.println(taskDTO.print());
        return taskDTO;
    }

}
