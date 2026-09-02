package cn.net.pap.example.dynamic.form.controller;

import cn.net.pap.example.dynamic.form.dto.MockApiDTO;
import cn.net.pap.example.dynamic.form.entity.MockApi;
import cn.net.pap.example.dynamic.form.service.MockApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 在线 API Mock 转发控制与配置管理类。
 * 支持 cURL 的快速解析导入与多维高精度全等特征匹配。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "API Mock 管理与代理接口", description = "支持在线 API 规则配置、管理、模拟转发与二进制文件测试")
public class JSONAPIController {

    private final MockApiService mockApiService;
    private final ObjectMapper objectMapper;

    // ==========================================
    // 1. Mock 配置管理 API (DTO 隔离)
    // ==========================================

    @GetMapping("/api/mock-config/list")
    @Operation(summary = "获取所有 Mock API 规则列表")
    public List<MockApiDTO> listConfig() {
        return mockApiService.listAll();
    }

    @PostMapping("/api/mock-config/save")
    @Operation(summary = "保存或更新 Mock API 配置规则")
    public MockApiDTO saveConfig(@RequestBody MockApiDTO dto) {
        return mockApiService.save(dto);
    }

    @DeleteMapping("/api/mock-config/{id}")
    @Operation(summary = "删除指定的 Mock API 配置规则")
    public void deleteConfig(@Parameter(description = "配置规则ID") @PathVariable Long id) {
        mockApiService.delete(id);
    }

    // ==========================================
    // 2. 动态 Mock 请求转发与代理端点 (支持 cURL 精确匹配)
    // ==========================================

    @RequestMapping("/api/mock/**")
    @Operation(summary = "Mock 请求动态代理转发端点", description = "拦截 /api/mock/** 的所有请求，根据配置规则进行高精度特征匹配并返回预设响应")
    public ResponseEntity<String> handleMock(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        String prefix = contextPath + "/api/mock";
        String mockPath = fullPath.substring(prefix.length());

        if (!mockPath.startsWith("/")) {
            mockPath = "/" + mockPath;
        }

        String method = request.getMethod().toUpperCase();
        Map<String, String> headers = getRequestHeaders(request);
        String rawQuery = request.getQueryString();
        String requestBody = getRequestBody(request, method);

        Optional<MockApi> mockApiOpt = mockApiService.matchRequest(mockPath, method, headers, rawQuery, requestBody);
        if (mockApiOpt.isPresent()) {
            MockApi mockApi = mockApiOpt.get();

            // 1. 模拟网络延迟
            if (mockApi.getDelayMs() != null && mockApi.getDelayMs() > 0) {
                try {
                    Thread.sleep(mockApi.getDelayMs());
                } catch (InterruptedException e) {
                    log.error("[Mock-Controller] 延时响应被中断: ", e);
                    Thread.currentThread().interrupt();
                }
            }

            // 2. 构建响应，装配自定义响应头
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(mockApi.getResponseStatus())
                    .contentType(MediaType.parseMediaType(mockApi.getContentType()));

            String responseHeadersJson = mockApi.getResponseHeaders();
            if (responseHeadersJson != null && !responseHeadersJson.trim().isEmpty()) {
                try {
                    Map<String, String> responseHeadersMap = objectMapper.readValue(responseHeadersJson, new TypeReference<Map<String, String>>() {});
                    if (responseHeadersMap != null) {
                        for (Map.Entry<String, String> entry : responseHeadersMap.entrySet()) {
                            builder.header(entry.getKey(), entry.getValue());
                        }
                    }
                } catch (Exception e) {
                    log.error("[Mock-Controller] 解析自定义响应头 JSON 失败: ", e);
                }
            }

            return builder.body(mockApi.getResponseBody());
        }

        String errorMsg = String.format(
                "{\"error\": \"Mock API 匹配失败，未在 SQLite 中找到符合条件的匹配规则。\", \"path\": \"%s\", \"method\": \"%s\", \"query\": \"%s\", \"body_length\": %d}",
                mockPath, method, rawQuery != null ? rawQuery : "", requestBody.length()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorMsg);
    }

    private Map<String, String> getRequestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName.toLowerCase(), request.getHeader(headerName));
            }
        }
        return headers;
    }

    private String getRequestBody(HttpServletRequest request, String method) {
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) {
            return "";
        }
        try (BufferedReader reader = request.getReader()) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (Exception e) {
            log.error("[Mock-Controller] 读取请求 Body 发生异常: ", e);
            return "";
        }
    }

    @GetMapping("/api/mock/binary")
    @Operation(summary = "统一文件与二进制 Mock 测试端点", description = "支持模拟生成文本、图片、PDF、Excel等文件流，支持浏览器直接预览或强制另存下载")
    public void handleBinaryMock(
            @Parameter(description = "资源文件类型: txt, png, pdf, xlsx", example = "txt")
            @org.springframework.web.bind.annotation.RequestParam(value = "type", defaultValue = "txt") String type,
            @Parameter(description = "浏览器处置行为: attachment (强制下载), inline (内联渲染/预览)", example = "attachment")
            @org.springframework.web.bind.annotation.RequestParam(value = "disposition", defaultValue = "attachment") String disposition,
            @Parameter(description = "下载文件的默认名称", example = "test.xlsx")
            @org.springframework.web.bind.annotation.RequestParam(value = "filename", required = false) String filename,
            @Parameter(description = "动态生成指定大小的测试文件空字节流（字节数）", example = "1024")
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size,
            HttpServletResponse response) {
        
        String resourcePath = "mock-files/test." + type.toLowerCase();
        org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource(resourcePath);

        String mimeType;
        switch (type.toLowerCase()) {
            case "png":
                mimeType = "image/png";
                break;
            case "pdf":
                mimeType = "application/pdf";
                break;
            case "xlsx":
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                break;
            case "txt":
            default:
                mimeType = "text/plain;charset=UTF-8";
                break;
        }

        response.setContentType(mimeType);

        String finalFilename = (filename != null && !filename.trim().isEmpty())
                ? filename.trim()
                : "mock-file." + type.toLowerCase();

        if ("attachment".equalsIgnoreCase(disposition)) {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + finalFilename + "\"");
        } else {
            response.setHeader("Content-Disposition", "inline; filename=\"" + finalFilename + "\"");
        }

        // 支持通过 size 参数输出指定大小的空二进制流（测试大文件传输）
        if (size != null && size > 0) {
            response.setContentLength(size);
            try (java.io.OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[Math.min(size, 8192)];
                int remaining = size;
                while (remaining > 0) {
                    int toWrite = Math.min(remaining, buffer.length);
                    os.write(buffer, 0, toWrite);
                    remaining -= toWrite;
                }
                os.flush();
            } catch (Exception e) {
                log.error("[Mock-Controller] 输出指定大小二进制字节流失败: ", e);
            }
            return;
        }

        if (!resource.exists()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            try {
                response.getWriter().write("{\"error\": \"未找到对应类型的 Mock 资源文件: " + type + "\"}");
            } catch (Exception e) {
                log.error("[Mock-Controller] 输出 404 错误详情失败: ", e);
            }
            return;
        }

        try (java.io.InputStream is = resource.getInputStream();
             java.io.OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        } catch (Exception e) {
            log.error("[Mock-Controller] 读取并输出 mock 资源文件发生异常: ", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }
}
