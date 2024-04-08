package cn.net.pap.example.assembly.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssemblyController {

    @Value("${random}")
    private String random;

    @GetMapping("random")
    public String random() {
        return random;
    }

}
