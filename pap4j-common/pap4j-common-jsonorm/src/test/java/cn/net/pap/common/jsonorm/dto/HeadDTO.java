package cn.net.pap.common.jsonorm.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.io.Serializable;
import java.util.List;

public class HeadDTO implements Serializable {

    @JsonAlias({"id", "序号"})
    private String id;

    private List<ChildDTO> _children;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ChildDTO> get_children() {
        return _children;
    }

    public void set_children(List<ChildDTO> _children) {
        this._children = _children;
    }

}
