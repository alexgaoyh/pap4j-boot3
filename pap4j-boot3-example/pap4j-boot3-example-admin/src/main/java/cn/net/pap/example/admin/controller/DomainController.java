package cn.net.pap.example.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DomainController {

    @GetMapping(value = "*", produces="application/json;charset=UTF-8")
    public String index(HttpServletRequest request) {
        return request.getServerName().toString();
    }

}
