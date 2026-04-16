package cn.net.pap.example.admin.controller.back;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/back/api")
@Tag(name = "后台接口", description = "后台管理相关的API接口")
public class BackController {

    @GetMapping("/hello")
    @Operation(summary = "后台欢迎接口", description = "返回后台欢迎信息", operationId = "hello")
    public String hello() {
        return "Hello from Back API!";
    }

}
