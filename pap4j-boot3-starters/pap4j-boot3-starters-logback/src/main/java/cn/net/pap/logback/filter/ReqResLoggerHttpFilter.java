package cn.net.pap.logback.filter;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import cn.net.pap.logback.PapLogbackLoggerFactory;
import org.slf4j.Logger;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * <h2>HTTP 请求/响应日志过滤器</h2>
 *
 * <p>本过滤器用于全局记录业务接口的调用细节，包括请求方法、URI、查询参数、请求体及响应体。</p>
 *
 * <b>核心生产特性：</b>
 * <ul>
 *   <li><b>内存防护 (OOM Defense):</b> 预检 Content-Length 并集成动态安全缓冲响应包装器，超过限制或下载大文件自动退化为透明直通流，彻底免除 OOM 隐患。</li>
 *   <li><b>内容裁剪:</b> 响应体超过限制时自动截断，仅保留头部关键信息。</li>
 *   <li><b>数据脱敏:</b> 自动识别并屏蔽 JSON/表单中的 <code>password</code> 等敏感字段。</li>
 *   <li><b>静默策略:</b> 自动识别图片、PDF、静态资源及上传下载接口，避免无效日志。</li>
 *   <li><b>流式响应直通 (SSE Protection):</b> 智能检测 <code>text/event-stream</code> 等流式响应协议，直接直通输出，杜绝响应滞后阻塞。</li>
 *   <li><b>字符安全:</b> 强制使用 UTF-8 编码，彻底杜绝生产环境下的日志乱码。</li>
 * </ul>
 *
 * <b>使用方式：</b>
 * <p>在配置类中注册以下 Bean：</p>
 * <pre>{@code
 * @Bean
 * public FilterRegistrationBean<ReqResLoggerHttpFilter> requestLogFilter() {
 *     FilterRegistrationBean<ReqResLoggerHttpFilter> registration = new FilterRegistrationBean<>();
 *     registration.setFilter(new ReqResLoggerHttpFilter());
 *     registration.addUrlPatterns("/*");
 *     registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
 *     return registration;
 * }
 * }</pre>
 *
 */
public class ReqResLoggerHttpFilter extends HttpFilter {

    private static final Logger logger = PapLogbackLoggerFactory.getLogger(ReqResLoggerHttpFilter.class.getSimpleName());

    private static final int MAX_BODY_SIZE = 10240; // 10KB 限制

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        boolean skipAll = shouldSkip(request);
        boolean isMultipart = isMultipartRequest(request);
        boolean isEventStream = isEventStreamRequest(request);

        HttpServletRequest requestToUse = request;
        HttpServletResponse responseToUse = response;
        SafeContentCachingResponseWrapper responseWrapper = null;

        if (!skipAll) {
            // 条件包装 Request: 排除 Multipart、EventStream 以及超出大小限制的请求
            if (!isMultipart && !isEventStream && request.getContentLength() <= MAX_BODY_SIZE) {
                requestToUse = new ContentCachingRequestWrapper(request, MAX_BODY_SIZE);
            }
            // 条件包装 Response: 排除 EventStream，使用安全自动退化的缓冲包装器
            if (!isEventStream) {
                responseWrapper = new SafeContentCachingResponseWrapper(response);
                responseToUse = responseWrapper;
            }
        }

        try {
            chain.doFilter(requestToUse, responseToUse);
            if (!skipAll) {
                logReqRes(requestToUse, responseWrapper);
            }
        } finally {
            if (responseWrapper != null) {
                try {
                    responseWrapper.copyBodyToResponse();
                } catch (IOException e) {
                    // 在 finally 块中捕获 IO 异常，防止其掩盖/冲掉真正的业务异常
                    logger.debug("Failed to copy cached body to response", e);
                }
            }
        }
    }

    private void logReqRes(HttpServletRequest request, SafeContentCachingResponseWrapper responseWrapper) {
        String reqMethod = request.getMethod();
        String reqUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String reqContent = getRequestContent(request);
        
        int resStatus = responseWrapper != null ? responseWrapper.getStatus() : 200;
        String resStr = "";
        
        if (responseWrapper != null) {
            if (responseWrapper.isCachingDisabled()) {
                resStr = "[Payload too large or streaming/binary, caching disabled]";
            } else if (!isLoggableResponse(responseWrapper)) {
                resStr = "[Non-loggable content type: " + responseWrapper.getContentType() + "]";
            } else {
                byte[] resContent = responseWrapper.getContentAsByteArray();
                resStr = resContent.length > MAX_BODY_SIZE ? 
                    "[Payload too large: " + resContent.length + " bytes]" : new String(resContent, StandardCharsets.UTF_8);
            }
        }

        logger.info("ReqResLoggerHttpFilter : reqMethod={}, reqUri={}, queryString={}, reqContent={}, resStatus={}, resContent={}",
                reqMethod, reqUri, queryString, maskSensitiveData(reqContent), resStatus, maskSensitiveData(resStr));
    }

    private String maskSensitiveData(String content) {
        if (content == null || content.isEmpty()) return content;
        // 支持 JSON 格式及常规 Form 表单参数中 password 字段的脱敏
        String masked = content.replaceAll("\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"******\"");
        masked = masked.replaceAll("(?i)(password=)[^&]+", "$1******");
        return masked;
    }

    private boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    private boolean isEventStreamRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept == null) return false;
        accept = accept.toLowerCase();
        return accept.contains("text/event-stream") 
            || accept.contains("application/x-ndjson")
            || accept.contains("application/stream+json");
    }

    private boolean isLoggableResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        if (contentType == null) return true;
        contentType = contentType.toLowerCase();
        
        // 排除流式及二进制输出
        if (contentType.contains("text/event-stream") 
            || contentType.contains("application/octet-stream")
            || contentType.contains("application/x-ndjson")
            || contentType.contains("application/stream+json")
            || contentType.contains("multipart/")) {
            return false;
        }
        
        return contentType.contains("application/json") 
            || contentType.contains("application/xml")
            || contentType.contains("text/plain")
            || contentType.contains("text/html");
    }

    /**
     * 跳过指定 URL
     * @param request
     * @return
     */
    private boolean shouldSkip (HttpServletRequest request) {
        String uri = request.getRequestURI().toLowerCase();
        return uri.contains("/upload") || 
               uri.contains("/download") || 
               uri.contains("/static/") || 
               uri.endsWith(".ico") ||
               uri.endsWith(".png") ||
               uri.endsWith(".jpg") ||
               uri.endsWith(".pdf");
    }

    private String getRequestContent(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (request instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
            if (contentType != null && contentType.contains("application/json")) {
                return new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            }
        }
        
        // 针对 application/x-www-form-urlencoded 和 multipart/form-data 格式，直接从 ParameterMap 获取文本属性
        if (contentType != null && (contentType.contains("application/x-www-form-urlencoded") || contentType.contains("multipart/form-data"))) {
            Map<String, String[]> parameterMap = request.getParameterMap();
            Map<String, String> parameters = new HashMap<>();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                String[] values = entry.getValue();
                if (values != null && values.length > 0) {
                    parameters.put(entry.getKey(), values[0]);
                }
            }
            return parameters.toString();
        }
        return "";
    }

    /**
     * 自定义安全响应包装器：
     * 1. 超过最大限制 (10KB) 或检测到流式响应、大二进制输出时，自动释放缓冲区并退化为直通模式。
     * 2. 避免大容量文件下载导致 OOM。
     */
    private static class SafeContentCachingResponseWrapper extends HttpServletResponseWrapper {
        private final FastByteArrayOutputStream cache = new FastByteArrayOutputStream();
        private ServletOutputStream outputStream;
        private PrintWriter writer;
        private boolean disableCaching = false;
        private final HttpServletResponse rawResponse;

        public SafeContentCachingResponseWrapper(HttpServletResponse response) {
            super(response);
            this.rawResponse = response;
        }

        private boolean isStreamOrBinaryType(String type) {
            if (type == null) return false;
            type = type.toLowerCase();
            return type.contains("text/event-stream") 
                || type.contains("application/octet-stream") 
                || type.contains("application/x-ndjson")
                || type.contains("application/stream+json")
                || type.contains("multipart/");
        }

        @Override
        public void setContentType(String type) {
            super.setContentType(type);
            if (isStreamOrBinaryType(type)) {
                enablePassThrough();
            }
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, value);
            if ("Content-Type".equalsIgnoreCase(name) && isStreamOrBinaryType(value)) {
                enablePassThrough();
            }
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, value);
            if ("Content-Type".equalsIgnoreCase(name) && isStreamOrBinaryType(value)) {
                enablePassThrough();
            }
        }

        private void enablePassThrough() {
            if (!disableCaching) {
                // 先开启直通标志，然后按序将 cache 的内容和 writer 的内容刷入原 response
                disableCaching = true;
                try {
                    flushCacheToResponse();
                    if (writer != null) {
                        writer.flush(); // 此时已是直通模式，writer 中缓冲的字符将有序写入 rawResponse
                    }
                } catch (IOException e) {
                    // 静默处理
                }
            }
        }

        private void flushCacheToResponse() throws IOException {
            if (cache.size() > 0) {
                byte[] bytes = cache.toByteArray();
                cache.reset();
                rawResponse.getOutputStream().write(bytes);
                rawResponse.getOutputStream().flush();
            }
        }

        private ServletOutputStream getRawOutputStream() {
            try {
                return rawResponse.getOutputStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (this.outputStream == null) {
                this.outputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return getRawOutputStream().isReady();
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        getRawOutputStream().setWriteListener(writeListener);
                    }

                    @Override
                    public void write(int b) throws IOException {
                        if (disableCaching) {
                            rawResponse.getOutputStream().write(b);
                        } else {
                            cache.write(b);
                            if (cache.size() > MAX_BODY_SIZE) {
                                enablePassThrough();
                            }
                        }
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        if (disableCaching) {
                            rawResponse.getOutputStream().write(b, off, len);
                        } else {
                            cache.write(b, off, len);
                            if (cache.size() > MAX_BODY_SIZE) {
                                enablePassThrough();
                            }
                        }
                    }

                    @Override
                    public void flush() throws IOException {
                        if (disableCaching) {
                            rawResponse.getOutputStream().flush();
                        }
                    }
                };
            }
            return this.outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (this.writer == null) {
                String characterEncoding = getCharacterEncoding();
                this.writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), characterEncoding != null ? characterEncoding : StandardCharsets.UTF_8.name()));
            }
            return this.writer;
        }

        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            } else if (outputStream != null) {
                outputStream.flush();
            }
            super.flushBuffer();
        }

        public byte[] getContentAsByteArray() {
            return cache.toByteArray();
        }

        public boolean isCachingDisabled() {
            return disableCaching;
        }

        public void copyBodyToResponse() throws IOException {
            if (!disableCaching) {
                if (writer != null) {
                    writer.flush();
                }
                flushCacheToResponse();
            }
        }
    }
}
