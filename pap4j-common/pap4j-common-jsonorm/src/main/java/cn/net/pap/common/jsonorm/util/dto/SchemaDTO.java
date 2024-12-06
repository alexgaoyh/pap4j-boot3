package cn.net.pap.common.jsonorm.util.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public class SchemaDTO implements Serializable {

    @JsonPropertyDescription("Integer")
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Size(min = 1, max = 10)
    private Integer i;

    @JsonPropertyDescription("String")
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.NotEmpty
    @jakarta.validation.constraints.Size(min = 1, max = 32)
    private String s;

    @JsonPropertyDescription("Double")
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.NotEmpty
    @jakarta.validation.constraints.Size(min = 1, max = 32)
    private Double d;

    private Float f;

    private Boolean b;

    private Long l;

    public @NotNull @Size(min = 1, max = 10) Integer getI() {
        return i;
    }

    public void setI(@NotNull @Size(min = 1, max = 10) Integer i) {
        this.i = i;
    }

    public @NotNull @NotEmpty @Size(min = 1, max = 32) String getS() {
        return s;
    }

    public void setS(@NotNull @NotEmpty @Size(min = 1, max = 32) String s) {
        this.s = s;
    }

    public @NotNull @NotEmpty @Size(min = 1, max = 32) Double getD() {
        return d;
    }

    public void setD(@NotNull @NotEmpty @Size(min = 1, max = 32) Double d) {
        this.d = d;
    }

    public Float getF() {
        return f;
    }

    public void setF(Float f) {
        this.f = f;
    }

    public Boolean getB() {
        return b;
    }

    public void setB(Boolean b) {
        this.b = b;
    }

    public Long getL() {
        return l;
    }

    public void setL(Long l) {
        this.l = l;
    }
}
