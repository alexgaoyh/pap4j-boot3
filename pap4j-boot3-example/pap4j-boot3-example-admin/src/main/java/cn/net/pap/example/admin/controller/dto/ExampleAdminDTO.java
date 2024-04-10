package cn.net.pap.example.admin.controller.dto;

import cn.net.pap.example.admin.config.jackson.annotation.PapTokenFilterJacksonComponentAnnotation;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

import java.io.Serializable;

/**
 *
 */
@Data
public class ExampleAdminDTO implements Serializable {

    private Integer code;

    /**
     * 根据 request.header 里面是否有当前字段，从而动态的限制当前序列化的方式.
     */
    @JsonSerialize(using = PapTokenFilterJacksonComponentAnnotation.TokenFilterSerializer.class)
    private String msg;

}
