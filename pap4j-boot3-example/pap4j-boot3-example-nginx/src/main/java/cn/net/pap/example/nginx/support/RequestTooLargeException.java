package cn.net.pap.example.nginx.support;

/**
 * <p>请求体超过配置上限时抛出的异常。</p>
 * <p>由 {@code NginxProxyWebFilter} 识别并转换为 413 响应，避免与上游故障的 502 混淆。</p>
 *
 * @author alexgaoyh
 */
public class RequestTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用指定消息构造异常。
     *
     * @param message 异常消息
     */
    public RequestTooLargeException(String message) {
        super(message);
    }

}
