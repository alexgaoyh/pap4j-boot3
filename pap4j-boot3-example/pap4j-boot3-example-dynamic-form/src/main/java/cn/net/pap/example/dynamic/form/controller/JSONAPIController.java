package cn.net.pap.example.dynamic.form.controller;

import cn.net.pap.example.dynamic.form.dto.MockApiDTO;
import cn.net.pap.example.dynamic.form.entity.MockApi;
import cn.net.pap.example.dynamic.form.service.MockApiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 在线 API Mock 转发控制与配置管理类。
 * 支持 cURL 的快速解析导入与多维高精度全等特征匹配。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class JSONAPIController {

    private final MockApiService mockApiService;

    // ==========================================
    // 1. Mock 配置管理 API (DTO 隔离)
    // ==========================================

    @GetMapping("/api/mock-config/list")
    public List<MockApiDTO> listConfig() {
        return mockApiService.listAll();
    }

    @PostMapping("/api/mock-config/save")
    public MockApiDTO saveConfig(@RequestBody MockApiDTO dto) {
        return mockApiService.save(dto);
    }

    @DeleteMapping("/api/mock-config/{id}")
    public void deleteConfig(@PathVariable Long id) {
        mockApiService.delete(id);
    }

    // ==========================================
    // 2. 动态 Mock 请求转发与代理端点 (支持 cURL 精确匹配)
    // ==========================================

    @RequestMapping("/api/mock/**")
    public ResponseEntity<String> handleMock(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        String prefix = contextPath + "/api/mock";
        String mockPath = fullPath.substring(prefix.length());

        if (!mockPath.startsWith("/")) {
            mockPath = "/" + mockPath;
        }

        String method = request.getMethod().toUpperCase();

        // 1. 提取所有请求头并转换为小写键
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName.toLowerCase(), request.getHeader(headerName));
            }
        }

        // 2. 提取原始 Query 字符串
        String rawQuery = request.getQueryString();

        // 3. 提取请求 Body
        String requestBody = "";
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
            try (BufferedReader reader = request.getReader()) {
                requestBody = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            } catch (Exception e) {
                log.error("[Mock-Controller] 读取请求 Body 发生异常: ", e);
            }
        }

        // 4. 调用业务引擎进行高精度匹配
        Optional<MockApi> mockApiOpt = mockApiService.matchRequest(mockPath, method, headers, rawQuery, requestBody);

        if (mockApiOpt.isPresent()) {
            MockApi mockApi = mockApiOpt.get();
            return ResponseEntity.status(mockApi.getResponseStatus())
                    .contentType(MediaType.parseMediaType(mockApi.getContentType()))
                    .body(mockApi.getResponseBody());
        }

        // 5. 匹配失败返回 404
        String errorMsg = String.format(
                "{\"error\": \"Mock API 匹配失败，未在 SQLite 中找到符合条件的匹配规则。\", \"path\": \"%s\", \"method\": \"%s\", \"query\": \"%s\", \"body_length\": %d}",
                mockPath, method, rawQuery != null ? rawQuery : "", requestBody.length()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorMsg);
    }
}
