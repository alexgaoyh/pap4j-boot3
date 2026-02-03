package cn.net.pap.common.jsonorm.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

public class HeadDTO implements Serializable {

    @JsonProperty("id")
    @NotBlank
    @Size(min = 1, max = 32)
    @JsonAlias({"id", "序号"})
    private String id;

    @Pattern(regexp = "zh|en")
    @JsonProperty(defaultValue = "zh")
    private String language;

    @Size(max = 200)
    private String remark;

    @JsonProperty("_children")
    @NotEmpty
    @Valid
    private List<ChildDTO> _children;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<ChildDTO> get_children() {
        return _children;
    }

    public void set_children(List<ChildDTO> _children) {
        this._children = _children;
    }

}
