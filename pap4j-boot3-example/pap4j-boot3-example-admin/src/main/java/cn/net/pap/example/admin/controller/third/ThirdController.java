package cn.net.pap.example.admin.controller.third;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/third/api")
@Tag(name = "第三方接口", description = "对接第三方的API接口")
public class ThirdController {

    @GetMapping("/hello")
    @Operation(summary = "第三方欢迎接口", description = "返回第三方欢迎信息", operationId = "hello")
    public String hello() {
        return "Hello from Third API!";
    }

}
