package cn.net.pap.example.assembly.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "打包部署测试接口", description = "演示通过 Assembly 插件打包部署的示例接口")
public class AssemblyController {

    @Value("${random}")
    private String random;

    @Operation(summary = "获取配置文件中的随机值")
    @GetMapping("random")
    public String random() {
        return random;
    }

}
