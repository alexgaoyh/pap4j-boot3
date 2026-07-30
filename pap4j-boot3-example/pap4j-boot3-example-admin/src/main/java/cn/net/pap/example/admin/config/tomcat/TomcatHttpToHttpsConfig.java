package cn.net.pap.example.admin.config.tomcat;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * <h3>Tomcat HTTPS 强制重定向与自适应多端口配置类</h3>
 *
 * <p>本类提供了自适应 SSL 监听和端口自动降级功能。如果证书文件不存在，系统将自动回退到普通 HTTP 服务。</p>
 *
 * <p>支持在 application.yml 中配置以下参数以启用 SSL：</p>
 * <pre>{@code
 * app:
 *   ssl:
 *     port: 443
 *     certificate: "D:/pap/ssl/server.crt"
 *     private-key: "D:/pap/ssl/server.key"
 * }</pre>
 */
@Configuration
@EnableConfigurationProperties(TomcatHttpToHttpsConfig.SslProperties.class)
public class TomcatHttpToHttpsConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private static final Logger log = LoggerFactory.getLogger(TomcatHttpToHttpsConfig.class);

    /**
     * 自定义 SSL 配置属性记录类.
     * 绑定前缀：app.ssl
     */
    @ConfigurationProperties(prefix = "app.ssl")
    public record SslProperties(int port, String certificate, String privateKey) {
        public boolean isConfigured() {
            return certificate != null && !certificate.trim().isEmpty() && privateKey != null && !privateKey.trim().isEmpty();
        }
    }

    private final int httpPort;
    private final SslProperties sslProperties;

    // 遵循项目技术守卫规范：强制使用构造器注入，严禁字段注入 (@Autowired)
    public TomcatHttpToHttpsConfig(@Value("${server.port:8080}") int httpPort, SslProperties sslProperties) {
        this.httpPort = httpPort;
        this.sslProperties = sslProperties;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (sslProperties.isConfigured()) {
            File certFile = new File(sslProperties.certificate());
            File keyFile = new File(sslProperties.privateKey());
            int httpsPort = sslProperties.port();

            if (certFile.exists() && keyFile.exists()) {
                log.info("【SSL 提示】检测到证书与私钥文件，启用 HTTPS ({}) 并配置 HTTP ({}) 端口强制重定向。", httpsPort, httpPort);

                // 1. 设置主服务端口为 HTTPS 端口
                factory.setPort(httpsPort);

                // 2. 配置主连接器的 SSL 属性
                factory.addConnectorCustomizers((Connector connector) -> {
                    if (connector.getPort() == httpsPort) {
                        connector.setScheme("https");
                        connector.setSecure(true);

                        if (connector.getProtocolHandler() instanceof Http11NioProtocol protocol) {
                            protocol.setSSLEnabled(true);

                            SSLHostConfig sslHostConfig = new SSLHostConfig();
                            SSLHostConfigCertificate cert = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
                            cert.setCertificateFile(sslProperties.certificate());
                            cert.setCertificateKeyFile(sslProperties.privateKey());
                            sslHostConfig.addCertificate(cert);
                            connector.addSslHostConfig(sslHostConfig);
                        }
                    }
                });

                // 3. 配置安全约束以强制所有 HTTP 请求进行 HTTPS 重定向
                factory.addContextCustomizers((Context context) -> {
                    SecurityConstraint securityConstraint = new SecurityConstraint();
                    securityConstraint.setUserConstraint("CONFIDENTIAL");
                    SecurityCollection collection = new SecurityCollection();
                    collection.addPattern("/*");
                    securityConstraint.addCollection(collection);
                    context.addConstraint(securityConstraint);
                });

                // 4. 添加额外的 HTTP 监听端口，自动跳转到 HTTPS 端口
                factory.addAdditionalTomcatConnectors(createHttpConnector());
                return;
            }
        }

        // 如果证书配置为空或证书文件不存在，自适应降级为普通 HTTP 服务
        log.warn("【SSL 警告】未配置证书或未探测到证书文件。自适应降级为普通 HTTP 服务（监听端口: {}），忽略 HTTPS！", httpPort);
        factory.setPort(httpPort);
    }

    private Connector createHttpConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(sslProperties.port());
        return connector;
    }
}
