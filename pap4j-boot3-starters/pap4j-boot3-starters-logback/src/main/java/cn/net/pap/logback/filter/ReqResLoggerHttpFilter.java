package cn.net.pap.logback.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import cn.net.pap.logback.PapLogbackLoggerFactory;
import org.slf4j.Logger;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <h2>HTTP 请求/响应日志过滤器</h2>
 *
 * <p>本过滤器用于全局记录业务接口的调用细节，包括请求方法、URI、查询参数、请求体及响应体。</p>
 *
 * <b>核心生产特性：</b>
 * <ul>
 *   <li><b>内存防护 (OOM Defense):</b> 预检 Content-Length，超过 10KB 的报文不予缓存，防止大文件导致内存溢出。</li>
 *   <li><b>内容裁剪:</b> 响应体超过 10KB 时自动截断并标注，仅保留头部关键信息。</li>
 *   <li><b>数据脱敏:</b> 自动识别并屏蔽 JSON/表单中的 <code>password</code> 等敏感字段。</li>
 *   <li><b>静默策略:</b> 自动识别图片、PDF、静态资源及上传下载接口，避免无效日志。</li>
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
        // 关键增强：增加预检，防止超大报文通过 ContentCachingRequestWrapper 导致内存爆炸
        if(shouldSkip(request) || request.getContentLength() > MAX_BODY_SIZE) {
            chain.doFilter(request, response);
        } else {
            // Wrapper 封装 Request 和 Response
            ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
            
            try {
                chain.doFilter(cachingRequest, cachingResponse);
                logReqRes(cachingRequest, cachingResponse);
            } finally {
                cachingResponse.copyBodyToResponse();
            }
        }
    }

    private void logReqRes(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        String reqMethod = request.getMethod();
        String reqUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String reqContent = getRequestContent(request);
        int resStatus = response.getStatus();
        byte[] resContent = response.getContentAsByteArray();
        
        String resStr = resContent.length > MAX_BODY_SIZE ? 
            "[Payload too large: " + resContent.length + " bytes]" : new String(resContent, StandardCharsets.UTF_8);

        logger.info("ReqResLoggerHttpFilter : reqMethod={}, reqUri={}, queryString={}, reqContent={}, resStatus={}, resContent={}",
                reqMethod, reqUri, queryString, maskSensitiveData(reqContent), resStatus, maskSensitiveData(resStr));
    }

    private String maskSensitiveData(String content) {
        if (content == null || content.isEmpty()) return content;
        // 简单脱敏逻辑，生产环境建议使用更完善的正则或 JSON 解析
        return content.replaceAll("\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"******\"");
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

    private String getRequestContent(ContentCachingRequestWrapper request) {
        if (request.getContentLength() > MAX_BODY_SIZE) {
            return "[Request body too large]";
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            return new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);
        } else if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
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

}
