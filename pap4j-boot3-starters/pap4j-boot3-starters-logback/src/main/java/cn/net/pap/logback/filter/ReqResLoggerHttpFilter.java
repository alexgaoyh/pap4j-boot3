package cn.net.pap.logback.filter;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern PASSWORD_JSON_PATTERN = Pattern.compile("\"password\"\\s*:\\s*\"[^\"]+\"");

    private static final Pattern PASSWORD_FORM_PATTERN = Pattern.compile("(?i)(password=)[^&]+");

    private final boolean enableLogReqRes;

    private final boolean enableBugRecorder;

    public ReqResLoggerHttpFilter() {
        this(true, true);
    }

    public ReqResLoggerHttpFilter(boolean enableLogReqRes, boolean enableBugRecorder) {
        this.enableLogReqRes = enableLogReqRes;
        this.enableBugRecorder = enableBugRecorder;
    }

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

        Throwable exception = null;
        try {
            chain.doFilter(requestToUse, responseToUse);
        } catch (Throwable t) {
            logger.error("Request processing failed", t);
            exception = t;
            throw t;
        } finally {
            if (!skipAll) {
                try {
                    int status = responseWrapper != null ? responseWrapper.getStatus() : 200;
                    if (enableBugRecorder && (exception != null || status >= 400)) {
                        recordBugSnapshot(requestToUse, responseWrapper, exception);
                    }
                    if (enableLogReqRes) {
                        logReqRes(requestToUse, responseWrapper);
                    }
                } catch (Exception e) {
                    logger.error("Failed to process logs or recorded bugs", e);
                }
            }
            if (responseWrapper != null) {
                try {
                    responseWrapper.copyBodyToResponse();
                } catch (IOException e) {
                    // 在 finally 块中捕获 IO 异常，防止其掩盖/冲掉真正的业务异常
                    logger.error("Failed to copy cached body to response", e);
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

    private static boolean containsIgnoreCase(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        int len = searchStr.length();
        int max = str.length() - len;
        for (int i = 0; i <= max; i++) {
            if (str.regionMatches(true, i, searchStr, 0, len)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithIgnoreCase(String str, String suffix) {
        if (str == null || suffix == null) {
            return false;
        }
        int suffixLen = suffix.length();
        int strLen = str.length();
        if (suffixLen > strLen) {
            return false;
        }
        return str.regionMatches(true, strLen - suffixLen, suffix, 0, suffixLen);
    }

    private static boolean startsWithIgnoreCase(String str, String prefix) {
        if (str == null || prefix == null) {
            return false;
        }
        int prefixLen = prefix.length();
        if (prefixLen > str.length()) {
            return false;
        }
        return str.regionMatches(true, 0, prefix, 0, prefixLen);
    }

    private String maskSensitiveData(String content) {
        if (content == null || content.isEmpty()) return content;
        // 支持 JSON 格式及常规 Form 表单参数中 password 字段的脱敏
        String masked = PASSWORD_JSON_PATTERN.matcher(content).replaceAll("\"password\":\"******\"");
        return PASSWORD_FORM_PATTERN.matcher(masked).replaceAll("$1******");
    }

    private boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return startsWithIgnoreCase(contentType, "multipart/");
    }

    private boolean isEventStreamRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept == null) return false;
        return containsIgnoreCase(accept, "text/event-stream") 
            || containsIgnoreCase(accept, "application/x-ndjson")
            || containsIgnoreCase(accept, "application/stream+json");
    }

    private boolean isLoggableResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        if (contentType == null) return true;
        
        // 排除流式及二进制输出
        if (containsIgnoreCase(contentType, "text/event-stream") 
            || containsIgnoreCase(contentType, "application/octet-stream")
            || containsIgnoreCase(contentType, "application/x-ndjson")
            || containsIgnoreCase(contentType, "application/stream+json")
            || containsIgnoreCase(contentType, "multipart/")) {
            return false;
        }
        
        return containsIgnoreCase(contentType, "application/json") 
            || containsIgnoreCase(contentType, "application/xml")
            || containsIgnoreCase(contentType, "text/plain")
            || containsIgnoreCase(contentType, "text/html");
    }

    /**
     * 跳过指定 URL
     * @param request
     * @return
     */
    private boolean shouldSkip (HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return true;
        return containsIgnoreCase(uri, "/upload") || 
               containsIgnoreCase(uri, "/download") || 
               containsIgnoreCase(uri, "/static/") || 
               endsWithIgnoreCase(uri, ".ico") ||
               endsWithIgnoreCase(uri, ".png") ||
               endsWithIgnoreCase(uri, ".jpg") ||
               endsWithIgnoreCase(uri, ".pdf");
    }

    private String getRequestContent(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (request instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
            if (contentType != null && containsIgnoreCase(contentType, "application/json")) {
                return new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            }
        }
        
        // 针对 application/x-www-form-urlencoded 和 multipart/form-data 格式，直接从 ParameterMap 获取文本属性
        if (contentType != null && (containsIgnoreCase(contentType, "application/x-www-form-urlencoded") || containsIgnoreCase(contentType, "multipart/form-data"))) {
            Map<String, String[]> parameterMap = request.getParameterMap();
            Map<String, String> parameters = new HashMap<>(Math.max((int) (parameterMap.size() / 0.75f) + 1, 16));
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

    private static volatile String targetDir;

    private static String getTargetDir() {
        String dir = targetDir;
        if (dir == null) {
            synchronized (ReqResLoggerHttpFilter.class) {
                dir = targetDir;
                if (dir == null) {
                    String logHome = null;
                    if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context) {
                        logHome = context.getProperty("LOG_HOME");
                        if (logHome != null) {
                            try {
                                logHome = ch.qos.logback.core.util.OptionHelper.substVars(logHome, context);
                            } catch (Exception e) {
                                logger.error("Failed to substitute LOG_HOME variable", e);
                                // Ignore
                            }
                        }
                    }
                    if (logHome == null || logHome.trim().isEmpty()) {
                        logHome = "logs";
                    }
                    dir = logHome + "/recorded-bugs";
                    targetDir = dir;
                }
            }
        }
        return dir;
    }

    /**
     * 自动向 LOG_HOME/recorded-bugs/ 写入异常接口快照
     */
    private void recordBugSnapshot(HttpServletRequest request, SafeContentCachingResponseWrapper responseWrapper, Throwable ex) {
        try {
            Map<String, Object> snapshot = new HashMap<>(4);
            
            // 1. 请求上下文
            Map<String, Object> reqInfo = new HashMap<>(8);
            reqInfo.put("method", request.getMethod());
            reqInfo.put("uri", request.getRequestURI());
            reqInfo.put("queryString", request.getQueryString());
            reqInfo.put("contentType", request.getContentType());
            reqInfo.put("headers", getHeadersMap(request));
            reqInfo.put("body", getRequestContent(request));
            snapshot.put("request", reqInfo);
            
            // 2. 响应上下文
            Map<String, Object> resInfo = new HashMap<>(4);
            int status = responseWrapper != null ? responseWrapper.getStatus() : 500;
            resInfo.put("status", status);
            if (responseWrapper != null) {
                resInfo.put("contentType", responseWrapper.getContentType());
                resInfo.put("headers", getHeadersMap(responseWrapper));
                if (!responseWrapper.isCachingDisabled() && isLoggableResponse(responseWrapper)) {
                    resInfo.put("body", new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8));
                } else {
                    resInfo.put("body", "[Payload non-loggable or too large]");
                }
            }
            snapshot.put("response", resInfo);
            
            // 3. 异常调用栈
            if (ex != null) {
                Map<String, Object> exInfo = new HashMap<>(4);
                exInfo.put("className", ex.getClass().getName());
                exInfo.put("message", ex.getMessage());
                
                StackTraceElement[] trace = ex.getStackTrace();
                if (trace != null && trace.length > 0) {
                    int traceLen = Math.min(trace.length, 15);
                    List<String> traceList = new ArrayList<>(traceLen);
                    for (int i = 0; i < traceLen; i++) {
                        traceList.add(trace[i].toString());
                    }
                    exInfo.put("stackTrace", traceList);
                }
                snapshot.put("exception", exInfo);
            }
            
            snapshot.put("timestamp", System.currentTimeMillis());
            
            String targetPath = getTargetDir();
            java.io.File dir = new java.io.File(targetPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String fileName = String.format("bug_%d_%d.json", System.currentTimeMillis(), ThreadLocalRandom.current().nextInt(1000));
            java.io.File file = new java.io.File(dir, fileName);
            
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, snapshot);
            
            logger.error("ReqResLoggerHttpFilter: Error detected (status={}), snapshot written to: {}", status, file.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to write bug snapshot", e);
        }
    }

    private Map<String, String> getHeadersMap(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>(16);
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                map.put(name, request.getHeader(name));
            }
        }
        return map;
    }

    private Map<String, String> getHeadersMap(HttpServletResponse response) {
        Map<String, String> map = new HashMap<>(16);
        Collection<String> headerNames = response.getHeaderNames();
        if (headerNames != null) {
            for (String name : headerNames) {
                map.put(name, response.getHeader(name));
            }
        }
        return map;
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
            return containsIgnoreCase(type, "text/event-stream") 
                || containsIgnoreCase(type, "application/octet-stream") 
                || containsIgnoreCase(type, "application/x-ndjson")
                || containsIgnoreCase(type, "application/stream+json")
                || containsIgnoreCase(type, "multipart/");
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
                    logger.error("Failed to flush cache to response", e);
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
                logger.error("Failed to get raw response output stream", e);
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
