package cn.net.pap.example.admin.controller.front;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/front/api")
@Tag(name = "前台接口", description = "前台相关的API接口")
public class FrontController {

    @GetMapping("/hello")
    @Operation(summary = "前台欢迎接口", description = "返回前台欢迎信息")
    public String hello() {
        return "Hello from Front API!";
    }

}
