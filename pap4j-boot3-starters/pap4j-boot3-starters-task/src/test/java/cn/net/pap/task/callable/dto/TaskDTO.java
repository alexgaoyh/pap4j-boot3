package cn.net.pap.task.callable.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class TaskDTO implements Serializable {

    private String id;

    private String taskId;

    private Date startTime;

    private Long duration;

    private Integer state;

    private String message;

    public String print() {
        return toString();
    }

}
