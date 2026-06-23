package cn.net.pap.example.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "域名接口", description = "获取请求的服务器域名")
public class DomainController {

    @Operation(summary = "获取当前域名", description = "拦截所有匹配的请求路径并返回当前请求所使用的服务器主机域名。")
    @GetMapping(value = "*", produces="application/json;charset=UTF-8")
    public String index(HttpServletRequest request) {
        return request.getServerName().toString();
    }

}
