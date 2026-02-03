package cn.net.pap.common.jsonorm.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public class ChildDTO implements Serializable {

    @JsonProperty("id")
    @NotBlank
    @JsonAlias({"id", "序号"})
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
