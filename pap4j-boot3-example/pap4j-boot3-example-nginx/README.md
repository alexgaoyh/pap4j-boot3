# pap4j-boot3-example-nginx

基于 **Spring Boot 3 + WebFlux（Reactor Netty）** 的进程内 Nginx 替代示例，用于替代存在漏洞修复滞后问题的 Nginx 组件。

提供两大能力：

1. **静态资源托管**：对应 `location / { root ...; try_files $uri $uri/ /index.html; index ...; }`，支持目录索引、SPA 回退、
   `Last-Modified`/304 条件请求与单段 Range/206。
2. **反向代理**：对应 `location /xxx { proxy_pass ...; }`，流式透传请求/响应体，支持转发头、跨域（CORS）、请求体大小限制、读取超时与错误页。

## 1. Nginx 配置映射

| Nginx 配置                                                   | 本模块对应项                                                                         |
|------------------------------------------------------------|--------------------------------------------------------------------------------|
| `listen 39001;`                                            | `server.port: 39001`                                                           |
| `root D:/nginx-1.20.2/pap-docs;`                           | `pap4j.nginx.resources.locations[].root`                                       |
| `try_files $uri $uri/ /index.html;`                        | `pap4j.nginx.resources.locations[].spa-fallback`                               |
| `index index.html index.htm;`                              | `pap4j.nginx.resources.locations[].index`                                      |
| `charset utf-8;`                                           | `pap4j.nginx.resources.locations[].charset`                                    |
| `location /api { proxy_pass http://127.0.0.1:8088/api; }`  | `pap4j.nginx.proxies[]` 中 `path: /api` + `target: http://127.0.0.1:8088`       |
| `proxy_set_header X-Real-IP / REMOTE-HOST / X-Forwarded-*` | 代理时自动重建 `X-Real-IP`、`REMOTE-HOST`、`X-Forwarded-For`、`X-Forwarded-Proto`、`Host` |
| `add_header Access-Control-*`                              | `pap4j.nginx.proxies[].cors`（含 `expose-headers`）                               |
| `if ($request_method = 'OPTIONS') { return 204; }`         | 过滤器层对预检请求直接返回 204                                                              |
| `client_max_body_size`                                     | `pap4j.nginx.proxies[].max-request-size`（默认 `1MB`，超限 413）                      |
| 字节范围 / 条件请求                                                | 静态资源原生支持 206 / 304                                                             |
| `error_page 502 ... /50x.html;`                            | `pap4j.nginx.proxies[].error-page`（上游不可用时输出该文件）                                |

> `target` 只写 `scheme://host:port` 时，按原样转发完整原始路径（等价于 `proxy_pass http://host:port/api;`）。
> 若 `target` 带路径（如 `http://host:port/api`），则按 Nginx 语义用该路径替换 location 前缀。

## 2. 配置示例

参见 `src/main/resources/application.yml`。反向代理跨域采用**方案 A**：

- `allowed-origins` 使用**精准域名白名单**；
- 只有请求 `Origin` 命中白名单才回显 `Access-Control-Allow-Origin`；
- 禁止 `*` 与 `allow-credentials: true` 组合（启动时会校验并报错）；
- 需要让前端读取 `Content-Disposition` 等响应头时，配置 `expose-headers`。

其他可选配置：

| 配置项                                             | 说明                                                | 默认                  |
|-------------------------------------------------|---------------------------------------------------|---------------------|
| `pap4j.nginx.proxies[].read-timeout`            | 上游读取（空闲）超时，防慢速 trickle                            | `30s`               |
| `pap4j.nginx.proxies[].max-request-size`        | 请求体上限，超限 413                                      | `1MB`               |
| `pap4j.nginx.proxies[].error-page`              | 上游不可用时的错误页文件（绝对路径）                                | 空（空响应体）             |
| `pap4j.nginx.proxies[].trust-forwarded-headers` | 是否信任客户端传入的 `X-Forwarded-Proto`；边缘代理保持 `false` 防伪造 | `false`             |
| `pap4j.nginx.resources.locations[].charset`     | 文本类响应字符集                                          | `utf-8`             |
| `pap4j.nginx.pool.*`                            | 有界连接池参数                                           | 见 `application.yml` |

## 3. 运行方式

```bash
# 打包（生成可执行 jar）
mvn -pl pap4j-boot3-example/pap4j-boot3-example-nginx package -DskipTests

# 运行
java -jar target/pap4j-boot3-example-nginx-0.0.4.jar

# 覆盖配置示例（注意：list 项建议整体用外置配置文件覆盖）
java -jar target/pap4j-boot3-example-nginx-0.0.4.jar \
  --spring.config.location=file:/path/to/your-application.yml
```

## 4. 目录结构

```
cn.net.pap.example.nginx
├── NginxApplication                    启动类
├── config
│   ├── NginxProperties                 配置属性（pap4j.nginx.*）+ 启动校验
│   ├── NginxProxyConfig                有界连接池 + 每条代理路由的 WebClient（含读取超时）
│   └── NginxStaticConfig               静态资源路由装配
├── handler
│   └── StaticResourceHandler           静态资源 + SPA 回退 + 304/206 + 防路径穿越
└── support
    ├── NginxProxyRoute                 单条代理路由运行时视图（含穿越校验）
    ├── NginxProxyWebFilter             反向代理过滤器（流式回写 + 预检 + 400/413/502）
    ├── ProxyHeaderSupport              hop-by-hop 过滤、转发头、CORS 组装
    ├── NginxVaryWebFilter              开启压缩时补充 Vary: Accept-Encoding
    ├── PathPrefixSupport               路径归一化与前缀匹配（消除重复实现）
    └── RequestTooLargeException        请求体超限异常（映射为 413）
```

## 5. 关键设计说明

- **流式透传**：代理在 `exchangeToMono` 内通过 `ServerHttpResponse.writeWith(...)` 直接回写 `DataBuffer`，
  不在内存中聚合整个 body，避免大文件上传/下载 OOM。
- **非阻塞静态 IO**：文件系统元数据与文件读取均切换到 `Schedulers.boundedElastic()`，不占用 Netty 事件循环。
- **路径安全**：静态路径归一化并校验 `startsWith(root)`；代理转发前对解码路径做 `..`/空字符校验（400），
  避免 `%2e%2e`、`%2f` 等编码绕过前缀匹配。
- **预检处理**：Spring WebFlux 的 HandlerMapping 会在路由前拦截预检请求（无 CORS 配置时直接 403），
  因此预检由 `NginxProxyWebFilter` 在过滤器层短路返回 204。
- **有界连接池**：代理共享一个 `ConnectionProvider`，`max-connections` / `pending-acquire-max-count` 均为有界值。
- **请求体限制与读取超时**：`max-request-size` 对 `Content-Length` 与 chunked 流式体双重校验；`read-timeout` 防止慢速连接占用。
- **CORS**：精准域名白名单，禁止 `*` + 凭证组合；支持 `expose-headers`。
- **转发头防伪造**：`X-Forwarded-Proto` 默认按连接协议取值，不信任客户端传入值（如需信任可开启 `trust-forwarded-headers`）；
  `X-Forwarded-For` 采用追加语义（对齐 Nginx `$proxy_add_x_forwarded_for`）。
- **压缩与缓存**：开启 `server.compression` 时自动补充 `Vary: Accept-Encoding`，避免共享缓存把压缩内容发给不支持的客户端。

## 6. 测试

```bash
./.agent/agent-test.cmd -pl pap4j-boot3-example/pap4j-boot3-example-nginx test
```

覆盖：代理前缀匹配、`proxy_pass` 路径替换、明文/编码路径穿越拒绝、转发头与 hop-by-hop 过滤、CORS 白名单与预检、
静态 200/SPA/405/304/206/路径穿越、代理预检 204、上游不可用 502、请求体超限 413。
