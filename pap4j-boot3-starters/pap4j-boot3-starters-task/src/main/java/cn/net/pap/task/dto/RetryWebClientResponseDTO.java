package cn.net.pap.task.dto;

import org.springframework.http.HttpHeaders;

import java.io.Serializable;

/**
 * 在 WebClient 中使用重试工具，统一的返回 DTO
 */
public class RetryWebClientResponseDTO implements Serializable {

    /**
     * 响应值
     */
    private int code;

    /**
     * 重试状态标识 headers.asHttpHeaders().get("pap-retry-code").toString()
     */
    private String retryCode;

    /**
     * 响应值
     */
    private String message;

    /**
     * 响应头信息
     */
    private org.springframework.http.HttpHeaders headers;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getRetryCode() {
        return retryCode;
    }

    public void setRetryCode(String retryCode) {
        this.retryCode = retryCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public void setHeaders(HttpHeaders headers) {
        this.headers = headers;
    }

}
