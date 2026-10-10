package cn.net.pap.example.dynamic.form.dto;

/**
 * Mock API 配置传输 Record。
 * @param id
 * @param url
 * @param method
 * @param responseBody
 * @param responseStatus
 * @param contentType
 * @param requestHeaders
 * @param requestParams
 * @param requestBody
 * @param responseHeaders
 * @param delayMs
 * @param curlCommand
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
