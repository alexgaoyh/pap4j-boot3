package cn.net.pap.example.admin.controller;

import cn.net.pap.example.admin.config.jackson.view.JacksonViews;
import cn.net.pap.example.admin.controller.dto.ExampleAdminDTO;
import cn.net.pap.example.admin.config.validator.dto.ValidationDTO;
import cn.net.pap.example.bean.config.dto.ExampleBeanDTO;
import cn.net.pap.example.user.config.dto.ExampleUserDTO;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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

    /**
     * /getArray?arrays=1&arrays=2
     * @param arrays
     * @return
     * @throws IOException
     */
    @GetMapping("/getArray")
    public String getArray(@RequestParam(value = "arrays") List<String> arrays) throws IOException {
        return arrays.toString();
    }

    /**
     * stream string
     * @param response
     * @throws IOException
     */
    @GetMapping("/stream-strings")
    public void streamStrings(HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");

        try {
            for (int i = 0; i < 10; i++) {
                String content = "Line " + i + " - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n";
                response.getOutputStream().write(content.getBytes());
                response.getOutputStream().flush(); // 确保内容被立即发送到客户端
                Thread.sleep(1000); // 暂停1秒以模拟延迟
            }
        } catch (IOException e) {
            // 处理可能的IO异常
            e.printStackTrace();
        } catch (InterruptedException e) {
            // 处理线程中断异常
            Thread.currentThread().interrupt();
        }
    }

    /**
     * stream string with http request
     * @param response
     * @throws IOException
     */
    @GetMapping("/stream-strings-api")
    public void streamStringsAPI(HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        try {
            RestTemplate restTemplate = new RestTemplate();
            ClientHttpResponse clientHttpResponse = restTemplate.execute(
                    "http://localhost:8080/stream-strings",
                    HttpMethod.GET,
                    null,
                    responseExtractor -> {
                        InputStream inputStream = responseExtractor.getBody();
                        OutputStream outputStream = response.getOutputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            outputStream.flush();
                        }
                        return null;
                    }
            );
        } catch (ResourceAccessException e) {
            // 处理可能的网络异常
            e.printStackTrace();
        }
    }

}
