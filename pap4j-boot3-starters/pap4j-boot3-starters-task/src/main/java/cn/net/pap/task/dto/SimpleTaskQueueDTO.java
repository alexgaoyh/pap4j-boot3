package cn.net.pap.task.dto;

import java.io.Serializable;

public class SimpleTaskQueueDTO implements Serializable {

    private String id;

    private String description;

    public SimpleTaskQueueDTO() {

    }

    public SimpleTaskQueueDTO(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "SimpleTaskQueueDTO{id='" + id + "', description='" + description + "'}";
    }

}
