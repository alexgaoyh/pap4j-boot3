package cn.net.pap.example.admin.controller;

import cn.net.pap.example.admin.config.jackson.view.JacksonViews;
import cn.net.pap.example.admin.config.validator.SignCheck;
import cn.net.pap.example.admin.config.validator.dto.ValidationDTO;
import cn.net.pap.example.admin.controller.dto.ExampleAdminDTO;
import cn.net.pap.example.admin.dto.GitCommitInfo;
import cn.net.pap.example.admin.util.TimestampCryptoUtil;
import cn.net.pap.example.bean.config.dto.ExampleBeanDTO;
import cn.net.pap.example.user.config.dto.ExampleUserDTO;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
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
@Tag(name = "Bean 演示接口", description = "提供 Bean 相关的示例接口，包括提交信息、参数校验、流式数据等")
public class BeanController {

    private static final Logger log = LoggerFactory.getLogger(BeanController.class);

    private final ExampleBeanDTO exampleBeanDTO;

    private final ExampleUserDTO exampleUserDTO;

    private final ThreadPoolTaskExecutor taskExecutor;

    public BeanController(ExampleBeanDTO exampleBeanDTO, ExampleUserDTO exampleUserDTO, @Qualifier("processExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.exampleBeanDTO = exampleBeanDTO;
        this.exampleUserDTO = exampleUserDTO;
        this.taskExecutor = taskExecutor;
    }

    @Operation(summary = "获取 Git 提交信息", description = "从编译生成的属性文件中读取并返回当前项目的 Git 提交元数据信息。")
    @GetMapping(value = "gitCommitInfo", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public GitCommitInfo gitCommitInfo() throws Exception {
        try {
            GitCommitInfo gitCommitInfo = GitCommitInfo.loadFromProperties();
            return gitCommitInfo;
        } catch (Exception e) {
            return new GitCommitInfo();
        } finally {
            Thread.sleep(2000L);
        }

    }

    @Operation(summary = "最终结果检查", description = "返回经过漂亮排版的示例 Admin DTO JSON 数据。")
    @ApiResponse(responseCode = "200", description = "成功返回 DTO", content = @Content(schema = @Schema(implementation = ExampleAdminDTO.class)))
    @GetMapping(value = "checkFinal", produces = "application/json;charset=UTF-8")
    public String checkFinal() throws Exception {
        try {
            ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
            exampleAdminDTO.setCode(0);
            exampleAdminDTO.setMsg("field");

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exampleAdminDTO);
        } catch (Exception e) {
            return e.getMessage();
        } finally {
            Thread.sleep(2000L);
        }

    }

    @Operation(summary = "表单/载荷数据验证", description = "接收一个被校验的 Validation DTO，对其内部字段进行 JSR-380 标准验证。")
    @PostMapping("validation")
    public Map<String, String> validation(@Valid @RequestBody ValidationDTO validationDTO) {
        Map<String, String> result = new HashMap<>();
        result.put("code", "200");
        result.put("err_msg", "");
        return result;
    }

    @Operation(summary = "生成签名", description = "生成当前时间戳的加密字符串，供签名校验接口测试使用。")
    @GetMapping("validation-sign1")
    public Map<String, String> validationSign() {
        Map<String, String> result = new HashMap<>();
        result.put("code", "200");
        result.put("err_msg", TimestampCryptoUtil.encryptNow());
        return result;
    }

    @Operation(summary = "校验签名", description = "接收并校验客户端传入的签名字符串，支持指定的时间容差度。")
    @GetMapping("validation-sign2")
    public Map<String, String> validationSign(@Parameter(description = "签名字符串") @SignCheck(timeTolerance = 6001) @RequestParam(required = false) String sign) {
        Map<String, String> result = new HashMap<>();
        result.put("code", "200");
        result.put("err_msg", "");
        return result;
    }

    @Operation(summary = "获取 Bean 示例 DTO", description = "返回预置注入的全局 Bean 属性示例 DTO。")
    @GetMapping("bean")
    public ExampleBeanDTO exampleBeanDTO() {
        return exampleBeanDTO;
    }

    @Operation(summary = "获取用户示例 DTO", description = "返回注入的全局 User 属性示例 DTO。")
    @GetMapping("user")
    public ExampleUserDTO exampleUserDTO() {
        return exampleUserDTO;
    }

    @Operation(summary = "获取默认 Admin DTO", description = "构建并返回一个默认状态 of ExampleAdminDTO 实例。")
    @GetMapping("dto")
    public ExampleAdminDTO exampleAdminDTO() {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");
        return exampleAdminDTO;
    }

    @Operation(summary = "获取漂亮排版 of Admin DTO 字符串", description = "构建 ExampleAdminDTO 实例并使用 Jackson 转换为美化排版后的 JSON 字符串。")
    @ApiResponse(responseCode = "200", description = "成功返回 DTO", content = @Content(schema = @Schema(implementation = ExampleAdminDTO.class)))
    @GetMapping(value = "dto2", produces = "application/json;charset=UTF-8")
    public String exampleAdminDTO2() throws Exception {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exampleAdminDTO);
    }

    @Operation(summary = "通过视图获取基础 DTO 属性", description = "利用 Jackson 视图 @JsonView 限定只序列化 Basic 基础字段的属性。")
    @GetMapping("dto3")
    @JsonView(JacksonViews.Basic.class)
    public ExampleAdminDTO exampleAdminDTO3() {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");
        return exampleAdminDTO;
    }

    @Operation(summary = "通过视图获取包含消息的 DTO 属性", description = "利用 Jackson 视图 @JsonView 限定只序列化 BasicWithMsg 基础和消息字段的属性。")
    @GetMapping("dto4")
    @JsonView(JacksonViews.BasicWithMsg.class)
    public ExampleAdminDTO exampleAdminDTO4() {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");
        return exampleAdminDTO;
    }

    @Operation(summary = "通过视图手动转化基础 DTO 字符串", description = "手动调用 ObjectMapper 配合 Jackson 视图 Basic 序列化 DTO 并返回 JSON 字符串。")
    @ApiResponse(responseCode = "200", description = "成功返回 DTO", content = @Content(schema = @Schema(implementation = ExampleAdminDTO.class)))
    @GetMapping(value = "dto5", produces = "application/json;charset=UTF-8")
    public String exampleAdminDTO5() throws Exception {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithView(JacksonViews.Basic.class).writeValueAsString(exampleAdminDTO);
    }

    @Operation(summary = "通过视图手动转化包含消息的 DTO 字符串", description = "手动调用 ObjectMapper 配合 Jackson 视图 BasicWithMsg 序列化 DTO 并返回 JSON 字符串。")
    @ApiResponse(responseCode = "200", description = "成功返回 DTO", content = @Content(schema = @Schema(implementation = ExampleAdminDTO.class)))
    @GetMapping(value = "dto6", produces = "application/json;charset=UTF-8")
    public String exampleAdminDTO6() throws Exception {
        ExampleAdminDTO exampleAdminDTO = new ExampleAdminDTO();
        exampleAdminDTO.setCode(0);
        exampleAdminDTO.setMsg("field");

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithView(JacksonViews.BasicWithMsg.class).writeValueAsString(exampleAdminDTO);
    }

    /**
     * /getArray?arrays=1&arrays=2
     *
     * @param arrays
     * @return
     * @throws IOException
     */
    @Operation(summary = "获取数组参数", description = "接收一个以 Query 参数传递的 List 集合并将其 toString 形式返回。")
    @GetMapping("/getArray")
    public String getArray(@Parameter(description = "数组参数列表") @RequestParam(value = "arrays") List<String> arrays) throws IOException {
        return arrays.toString();
    }

    @Operation(summary = "测试 SSE 流式输出", description = "使用 SseEmitter 异步且逐秒向客户端推送 10 次消息。")
    @GetMapping(value = "/test-stream", produces = "text/event-stream")
    @CrossOrigin
    public SseEmitter conversation(HttpServletRequest request) {
        final SseEmitter emitter = new SseEmitter();
        taskExecutor.execute(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    try {
                        // 模拟某些耗时操作
                        Thread.sleep(1000L);
                        emitter.send("这是第" + i + "次往服务端发送内容");
                    } catch (Exception e) {
                        log.error("SSE send error", e);
                        emitter.completeWithError(e);
                        return;
                    }
                }
                emitter.send(SseEmitter.event().name("end").data("数据发送完毕"));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE error", e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * stream string
     *
     * @param response
     * @throws IOException
     */
    @Operation(summary = "通过 HttpServletResponse 输出 SSE 流", description = "利用 PrintWriter 手动实现 SSE 格式数据输出，展示逐字/逐行推送能力。")
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
                Thread.sleep(1000L);
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
     *
     * @param response
     * @throws IOException
     */
    @Operation(summary = "内部转发 SSE 流", description = "通过 RestTemplate 转发并消费本地的 stream-strings 端点数据，再以流的形式吐给客户端。")
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
            log.error("streamStringsAPI", e);
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
