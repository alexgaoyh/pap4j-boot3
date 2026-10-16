package cn.net.pap.example.nginx.config;

import cn.net.pap.example.nginx.handler.StaticResourceHandler;
import cn.net.pap.example.nginx.support.PathPrefixSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>静态资源路由装配。</p>
 * <p>按配置为每个启用的 location 注册静态资源处理器；反向代理由 {@code NginxProxyWebFilter} 在过滤器层完成。</p>
 *
 * @author alexgaoyh
 */
@Configuration
public class NginxStaticConfig {

    /**
     * 注册 Nginx 替代的静态资源路由函数。
     *
     * @param properties 配置属性
     * @return 路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> nginxRouterFunction(NginxProperties properties) {
        RouterFunctions.Builder builder = RouterFunctions.route();
        NginxProperties.Resources resources = properties.getResources();
        if (resources.isEnabled()) {
            List<NginxProperties.Location> locations = new ArrayList<>(resources.getLocations());
            locations.sort((a, b) -> Integer.compare(
                    PathPrefixSupport.normalize(b.getPath()).length(),
                    PathPrefixSupport.normalize(a.getPath()).length()));
            for (NginxProperties.Location location : locations) {
                if (location.isEnabled()) {
                    builder.route(pathPrefix(location.getPath()), new StaticResourceHandler(location));
                }
            }
        }
        return builder.build();
    }

    /**
     * 构建「路径段前缀」匹配谓词，避免 {@code /apixxx} 误命中 {@code /api}。
     *
     * @param path 路径前缀
     * @return 请求谓词
     */
    private static RequestPredicate pathPrefix(String path) {
        String normalized = PathPrefixSupport.normalize(path);
        return request -> {
            String requestPath = request.path();
            if ("/".equals(normalized)) {
                return true;
            }
            return requestPath.equals(normalized) || requestPath.startsWith(normalized + "/");
        };
    }

}
