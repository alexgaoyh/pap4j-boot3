package cn.net.pap.example.admin.controller;

import cn.net.pap.example.admin.controller.dto.ExampleAdminDTO;
import cn.net.pap.example.bean.config.dto.ExampleBeanDTO;
import cn.net.pap.example.user.config.dto.ExampleUserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BeanController {

    @Autowired
    private ExampleBeanDTO exampleBeanDTO;

    @Autowired
    private ExampleUserDTO exampleUserDTO;

    @GetMapping("bean")
    public ExampleBeanDTO exampleBeanDTO() {
        return exampleBeanDTO;
    }

    @GetMapping("user")
    public ExampleUserDTO exampleUserDTO() {
        return exampleUserDTO;
    }

    @GetMapping("dto")
    public ExampleAdminDTO exampleAdminDTO() {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");
        return exampleAdminDTO;
    }

}
