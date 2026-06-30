package cn.net.pap.example.dynamic.form.dto;

/**
 * Mock API 配置传输 Record。
 */
public record MockApiDTO(
        Long id,
        String url,
        String method,
        String responseBody,
        Integer responseStatus,
        String contentType,
        String requestHeaders,
        String requestParams,
        String requestBody,
        String responseHeaders,
        Integer delayMs,
        String curlCommand
) {}
