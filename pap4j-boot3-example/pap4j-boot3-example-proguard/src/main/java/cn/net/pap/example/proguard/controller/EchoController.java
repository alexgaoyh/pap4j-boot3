package cn.net.pap.example.proguard.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 后端接口模拟
 * 
 * 类型	请求方式	URL 示例	Content-Type	请求体/参数
 * JSON	POST	/echo/json	application/json	{"name": "jack"}
 * FORM	POST	/echo/form	application/x-www-form-urlencoded	name=jack&age=20
 * MULTIPART	POST	/echo/multipart	multipart/form-data	key/value + 文件
 * TEXT	POST	/echo/text	text/plain	纯文本
 * GET	GET	/echo/echo?name=jack	-	URL参数
 * PUT	PUT	/echo/json	application/json	JSON内容
 * DELETE	DELETE	/echo/echo?id=123	-	URL参数
 */
@RestController
@RequestMapping("/echo")
@CrossOrigin
public class EchoController {

    private static final Logger log = LoggerFactory.getLogger(EchoController.class);

    // ---------- GET ----------
    @GetMapping("/echo")
    public Map<String, Object> echoGet(@RequestParam Map<String, String> params) {
        log.info("GET /echo params: {}", params);
        return Map.of("echo", "echo", "method", "GET", "params", params, "message", "Received GET request");
    }

    // ---------- POST ----------
    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> postJson(@RequestBody Map<String, Object> body) {
        log.info("POST JSON body: {}", body);
        return Map.of("echo", "echo", "method", "POST", "contentType", "application/json", "body", body);
    }

    @PostMapping(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> postForm(@RequestParam Map<String, String> params) {
        log.info("POST FORM params: {}", params);
        return Map.of("echo", "echo", "method", "POST", "contentType", "application/x-www-form-urlencoded", "params", params);
    }

    @PostMapping(value = "/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> postMultipart(@RequestParam Map<String, String> params, @RequestParam(required = false) List<MultipartFile> files) {
        log.info("POST MULTIPART params: {}", params);
        List<Map<String, Object>> fileInfos = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                fileInfos.add(Map.of("name", file.getName(), "originalFilename", file.getOriginalFilename(), "size", file.getSize()));
            }
        }
        return Map.of("echo", "echo", "method", "POST", "contentType", "multipart/form-data", "params", params, "files", fileInfos);
    }

    @PostMapping(value = "/text", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> postText(@RequestBody byte[] text) {
        String content = new String(text, StandardCharsets.UTF_8);
        log.info("POST TEXT: {}", content);
        return Map.of("echo", "echo", "method", "POST", "contentType", "text/plain", "body", content);
    }

    // ---------- PUT ----------
    @PutMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> putJson(@RequestBody Map<String, Object> body) {
        log.info("PUT JSON body: {}", body);
        return Map.of("echo", "echo", "method", "PUT", "contentType", "application/json", "body", body);
    }

    @PutMapping(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> putForm(@RequestParam Map<String, String> params) {
        log.info("PUT FORM params: {}", params);
        return Map.of("echo", "echo", "method", "PUT", "contentType", "application/x-www-form-urlencoded", "params", params);
    }

    @PutMapping(value = "/text", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> putText(@RequestBody String body) {
        log.info("PUT TEXT: {}", body);
        return Map.of("echo", "echo", "method", "PUT", "contentType", "text/plain", "body", body);
    }

    // ---------- DELETE ----------
    @DeleteMapping("/echo")
    public Map<String, Object> deleteEcho(@RequestParam Map<String, String> params) {
        log.info("DELETE /echo params: {}", params);
        return Map.of("echo", "echo", "method", "DELETE", "params", params, "message", "Received DELETE request");
    }

}
