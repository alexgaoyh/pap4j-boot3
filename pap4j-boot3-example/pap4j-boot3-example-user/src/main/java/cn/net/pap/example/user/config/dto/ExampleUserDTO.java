package cn.net.pap.example.user.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "示例用户配置信息 DTO")
public class ExampleUserDTO implements Serializable {

    @Schema(description = "用户名称", example = "adminUser")
    private String userName;

    @Schema(description = "性别描述", example = "male")
    private String sex;
}
