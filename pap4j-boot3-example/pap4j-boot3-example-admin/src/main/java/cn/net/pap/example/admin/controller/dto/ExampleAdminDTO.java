package cn.net.pap.example.admin.controller.dto;

import cn.net.pap.example.admin.config.jackson.annotation.PapTokenFilterJacksonComponentAnnotation;
import cn.net.pap.example.admin.config.jackson.view.JacksonViews;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jdk.jfr.Description;
import lombok.Data;

import java.io.Serializable;

/**
 *
 */
@Data
public class ExampleAdminDTO implements Serializable {

    @JsonView(JacksonViews.Basic.class)
    private Integer code;

    /**
     * 根据 request.header 里面是否有当前字段，从而动态的限制当前序列化的方式.
     */
    @JsonSerialize(using = PapTokenFilterJacksonComponentAnnotation.TokenFilterSerializer.class)
    @Description("This is a description")
    @JsonView(JacksonViews.BasicWithMsg.class)
    private String msg;

}
