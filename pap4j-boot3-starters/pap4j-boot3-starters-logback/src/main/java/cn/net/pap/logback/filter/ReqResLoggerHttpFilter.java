package cn.net.pap.logback.filter;

import java.io.IOException;
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
 * 操作日志的记录，持久化到日志文件中
 *
 * 调用的时候，增加如下Bean 定义
 *
 *     @Bean
 *     public FilterRegistrationBean<ReqResLoggerHttpFilter> requestLogFilter (){
 *         FilterRegistrationBean<ReqResLoggerHttpFilter> registrationBean = new FilterRegistrationBean<ReqResLoggerHttpFilter>();
 *         registrationBean.setFilter(new ReqResLoggerHttpFilter());
 *         registrationBean.addUrlPatterns("/*");
 *         registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
 *         return registrationBean;
 *     }
 */
public class ReqResLoggerHttpFilter extends HttpFilter {

    private static final Logger logger = PapLogbackLoggerFactory.getLogger(ReqResLoggerHttpFilter.class.getSimpleName());

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if(shouldSkip(request)) {
            // 不需要记录请求体和响应体
            chain.doFilter(request, response);
        } else {
            // Wrapper 封装 Request 和 Response
            ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);

            // 继续执行请求链
            chain.doFilter(cachingRequest, cachingResponse);

            // 在请求完成后记录请求/响应日志   请求方法 URI 请求体 响应状态 响应体
            String reqMethod = request.getMethod();
            String reqUri = request.getRequestURI();
            String queryString = request.getQueryString();
            String reqContent = getRequestContent(cachingRequest);
            int resStatus = response.getStatus();
            byte[] resContent = cachingResponse.getContentAsByteArray();
            logger.info("ReqResLoggerHttpFilter : reqMethod={}, reqUri={}, queryString={}, reqContent={}, resStatus={}, resContent={}",
                    reqMethod, reqUri, queryString, reqContent, resStatus, new String(resContent));

            // 把缓存的响应数据，响应给客户端
            cachingResponse.copyBodyToResponse();
        }
    }

    /**
     * 跳过指定 URL
     * @param request
     * @return
     */
    private boolean shouldSkip (HttpServletRequest request) {
        if (request.getRequestURI().contains("/upload") ||
                request.getRequestURI().contains("/download") ||
                request.getRequestURI().contains("/static")) {
            // 跳过 上传 下载 静态 等
            return true;
        } else {
            return false;
        }
    }

    private String getRequestContent(ContentCachingRequestWrapper request) {
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            return new String(request.getContentAsByteArray());
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
