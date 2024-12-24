package cn.net.pap.example.admin.controller;

import cn.net.pap.example.admin.config.jackson.view.JacksonViews;
import cn.net.pap.example.admin.controller.dto.ExampleAdminDTO;
import cn.net.pap.example.admin.config.validator.dto.ValidationDTO;
import cn.net.pap.example.bean.config.dto.ExampleBeanDTO;
import cn.net.pap.example.user.config.dto.ExampleUserDTO;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
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

    @GetMapping(value = "/test-stream", produces = "text/event-stream")
    @CrossOrigin
    public SseEmitter conversation(HttpServletRequest request)  {
        final SseEmitter emitter = new SseEmitter();
        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    try {
                        // 模拟某些耗时操作
                        Thread.sleep(1000L);
                        emitter.send("这是第" + i +"次往服务端发送内容");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                emitter.send(SseEmitter.event().name("end").data("数据发送完毕"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                emitter.complete();
            }
        }).start();
        return emitter;
    }

    /**
     * stream string
     * @param response
     * @throws IOException
     */
    @GetMapping("/stream-strings")
    @CrossOrigin
    public void streamStrings(HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        PrintWriter writer = response.getWriter();

        try {
            for (int i = 0; i < 10; i++) {
                String content = "Line " + i + " - 中文 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n";
                writer.write("data: " + content + "\n\n");
                writer.flush();
                Thread.sleep(1000);
            }
            // 发送结束标志
            writer.write("event: end\n"); // 特定事件名称
            writer.write("data: [Stream Completed]\n\n");
            writer.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            writer.close();
        }
    }

    /**
     * stream string with http request
     * @param response
     * @throws IOException
     */
    @GetMapping("/stream-strings-api")
    @CrossOrigin
    public void streamStringsAPI(HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream;charset=UTF-8");
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
        } finally {
            response.getWriter().close(); // 关闭输出流，从而关闭连接
        }
    }

    /*
    <!DOCTYPE html>
    <html>
        <head>
            <meta charset="utf-8">
            <title></title>
        </head>
        <body>
            <div id="app">
                <div>
                    <textarea type="text" v-model="fullContent" rows="20" cols="100"></textarea>
                </div>
            </div>
        </body>
        <script src="js/Vue-v2.6.14.js"></script>
        <script src="js/axios.js"></script>
        <script>
            new Vue({
                el: '#app',
                data: {
                    eventSource: null,
                    fullContent: '',
                    displayedContent: '',
                    typingTimer: null,
                    typingIndex: 0
                },
                created() {
                    this.startStream();
                },
                methods: {
                    startStream() {
                      const eventSource = new EventSource("http://localhost:8080/stream-strings-api");
                      eventSource.onmessage = (event) => {
                        const message = event.data;
                        this.printCharacters(message);
                      };

                      // 监听自定义结束事件
                      eventSource.addEventListener("end", () => {
                        this.completed = true; // 更新状态为完成
                        eventSource.close(); // 关闭连接
                      });

                      eventSource.onerror = () => {
                        console.error("连接错误，关闭流。");
                        eventSource.close();
                      };
                    },
                    async printCharacters(message) {
                        for (const char of message) {
                            await this.delay(10); // 模拟逐字打印延迟
                            this.fullContent += char;
                        }
                            this.fullContent += "\n"; // 添加换行符，确保每条消息单独显示
                    },
                    delay(ms) {
                      return new Promise((resolve) => setTimeout(resolve, ms));
                    },
                },
                beforeDestroy() {
                    if (this.eventSource) {
                        this.eventSource.close();
                    }
                    if (this.typingTimer) {
                        clearInterval(this.typingTimer);
                    }
                }
            });
        </script>
    </html>
     */

}
