package cn.net.pap.example.admin.controller;

import cn.net.pap.example.admin.config.jackson.view.JacksonViews;
import cn.net.pap.example.admin.controller.dto.ExampleAdminDTO;
import cn.net.pap.example.admin.config.validator.dto.ValidationDTO;
import cn.net.pap.example.bean.config.dto.ExampleBeanDTO;
import cn.net.pap.example.user.config.dto.ExampleUserDTO;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class BeanController {

    @Autowired
    private ExampleBeanDTO exampleBeanDTO;

    @Autowired
    private ExampleUserDTO exampleUserDTO;

    @PostMapping("validation")
    public Map<String, String> validation(@Valid @RequestBody ValidationDTO validationDTO) {
        Map<String, String> result = new HashMap<>();
        result.put("code", "200");
        result.put("err_msg", "");
        return result;
    }

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

    @GetMapping(value = "dto2", produces="application/json;charset=UTF-8")
    public String exampleAdminDTO2() throws Exception {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exampleAdminDTO);
    }

    @GetMapping("dto3")
    @JsonView(JacksonViews.Basic.class)
    public ExampleAdminDTO exampleAdminDTO3() {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");
        return exampleAdminDTO;
    }

    @GetMapping("dto4")
    @JsonView(JacksonViews.BasicWithMsg.class)
    public ExampleAdminDTO exampleAdminDTO4() {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");
        return exampleAdminDTO;
    }

    @GetMapping(value = "dto5", produces="application/json;charset=UTF-8")
    public String exampleAdminDTO5() throws Exception {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithView(JacksonViews.Basic.class).writeValueAsString(exampleAdminDTO);
    }

    @GetMapping(value = "dto6", produces="application/json;charset=UTF-8")
    public String exampleAdminDTO6() throws Exception {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithView(JacksonViews.BasicWithMsg.class).writeValueAsString(exampleAdminDTO);
    }

}
