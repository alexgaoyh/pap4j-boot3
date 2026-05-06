package cn.net.pap.task.callable;

import cn.net.pap.task.callable.dto.TaskDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PapCallableImpl implements PapCallable<TaskDTO> {

    private static final Logger log = LoggerFactory.getLogger(PapCallableImpl.class);

    private TaskDTO taskDTO;

    public PapCallableImpl(TaskDTO taskDTO) {
        this.taskDTO = taskDTO;
    }

    @Override
    public TaskDTO call() throws Exception {
        log.info("{}", taskDTO.print());
        return taskDTO;
    }

}
