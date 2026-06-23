package cn.net.pap.example.bean.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "示例 Bean 配置信息 DTO")
public class ExampleBeanDTO implements Serializable {

    @Schema(description = "Bean 名称", example = "myExampleBean")
    private String beanName;

}
