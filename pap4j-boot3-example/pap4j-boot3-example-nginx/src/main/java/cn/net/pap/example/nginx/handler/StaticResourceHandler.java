package cn.net.pap.example.nginx.handler;

import cn.net.pap.example.nginx.config.NginxProperties;
import cn.net.pap.example.nginx.support.PathPrefixSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>静态资源处理器，等价于 Nginx 的 {@code location /} + {@code root} + {@code try_files $uri $uri/ /index.html}。</p>
 * <p>支持目录默认索引、SPA 回退、{@code Last-Modified}/304 条件请求与单段 Range/206。</p>
 * <p>所有文件系统元数据与读取均在 {@code boundedElastic} 调度器执行，避免阻塞 Netty 事件循环。</p>
 *
 * @author alexgaoyh
 */
public class StaticResourceHandler implements HandlerFunction<ServerResponse> {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceHandler.class);

    private static final int BUFFER_SIZE = 8192;

    /**
     * 静态资源位置配置。
     */
    private final NginxProperties.Location location;

    /**
     * 静态文件根目录（已绝对化与归一化）。
     */
    private final Path root;

    /**
     * 归一化后的匹配路径前缀。
     */
    private final String locationPath;

    /**
     * 响应字符集，配置为空时为 null。
     */
    private final Charset charset;

    /**
     * 构造静态资源处理器。
     *
     * @param location 静态资源位置配置
     */
    public StaticResourceHandler(NginxProperties.Location location) {
        this.location = location;
        this.root = Paths.get(location.getRoot()).toAbsolutePath().normalize();
        this.locationPath = PathPrefixSupport.normalize(location.getPath());
        this.charset = StringUtils.hasText(location.getCharset()) ? Charset.forName(location.getCharset()) : null;
    }

    /**
     * 处理静态资源请求，命中文件则返回文件，否则回退 SPA 首页。
     *
     * @param request 请求
     * @return 静态资源响应
     */
    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        if (!isReadableMethod(request.method())) {
            return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .header(HttpHeaders.ALLOW, "GET, HEAD")
                    .build();
        }
        return Mono.fromCallable(() -> resolve(request.uri().getPath()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(resolution -> respond(resolution, request));
    }

    /**
     * 根据解析结果返回 403 / 404 或文件响应。
     *
     * @param resolution 解析结果
     * @param request    请求
     * @return 响应
     */
    private Mono<ServerResponse> respond(Resolution resolution, ServerRequest request) {
        if (resolution.forbidden()) {
            log.warn("[Nginx-Static-Forbidden] 路径穿越被拦截, uri: {}", request.path());
            return ServerResponse.status(HttpStatus.FORBIDDEN).build();
        }
        if (resolution.redirect()) {
            String target = request.path() + "/";
            if (StringUtils.hasText(request.uri().getRawQuery())) {
                target = target + "?" + request.uri().getRawQuery();
            }
            return ServerResponse.status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, target)
                    .build();
        }
        if (resolution.file() == null) {
            log.debug("[Nginx-Static-NotFound] 静态资源与 SPA 回退均未命中, uri: {}", request.path());
            return ServerResponse.notFound().build();
        }
        return serve(resolution.file(), request);
    }

    /**
     * 读取文件元信息并以流式方式输出响应体。
     *
     * @param file    文件路径
     * @param request 请求
     * @return 文件响应
     */
    private Mono<ServerResponse> serve(Path file, ServerRequest request) {
        return Mono.fromCallable(() -> metadata(file))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(metadata -> respondFile(file, metadata, request));
    }

    /**
     * 按条件请求 / Range 语义构建文件响应。
     *
     * @param file     文件路径
     * @param metadata 文件元信息
     * @param request  请求
     * @return 文件响应
     */
    private Mono<ServerResponse> respondFile(Path file, FileMetadata metadata, ServerRequest request) {
        if (isNotModified(request, metadata)) {
            return notModified(metadata);
        }
        if (HttpMethod.HEAD.equals(request.method())) {
            return head(metadata);
        }
        Optional<HttpRange> range = parseRange(request);
        if (range.isPresent() && !ignoreRange(request, metadata)) {
            try {
                return partialContent(file, metadata, range.get());
            } catch (IllegalArgumentException e) {
                log.error("[Nginx-Static-Range] Range 不合法或超出文件大小, length: {}", metadata.length(), e);
                return ServerResponse.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + metadata.length())
                        .build();
            }
        }
        return fullContent(file, metadata);
    }

    /**
     * 构建 304 未修改响应。
     *
     * @param metadata 文件元信息
     * @return 304 响应
     */
    private Mono<ServerResponse> notModified(FileMetadata metadata) {
        ServerResponse.BodyBuilder builder = ServerResponse.status(HttpStatus.NOT_MODIFIED);
        builder.eTag(metadata.etag());
        builder.header(HttpHeaders.LAST_MODIFIED, formatDate(metadata.lastModified()));
        applyCacheControl(builder);
        return builder.build();
    }

    /**
     * 构建 HEAD 响应（仅头部，不读取文件内容）。
     *
     * @param metadata 文件元信息
     * @return HEAD 响应
     */
    private Mono<ServerResponse> head(FileMetadata metadata) {
        ServerResponse.BodyBuilder builder = baseBuilder(metadata);
        builder.contentLength(metadata.length());
        builder.header(HttpHeaders.ACCEPT_RANGES, "bytes");
        return builder.build();
    }

    /**
     * 构建完整内容（200）响应。
     *
     * @param file     文件路径
     * @param metadata 文件元信息
     * @return 200 响应
     */
    private Mono<ServerResponse> fullContent(Path file, FileMetadata metadata) {
        ServerResponse.BodyBuilder builder = baseBuilder(metadata);
        builder.contentLength(metadata.length());
        builder.header(HttpHeaders.ACCEPT_RANGES, "bytes");
        return builder.body(BodyInserters.fromDataBuffers(read(file, 0L, metadata.length())));
    }

    /**
     * 构建单段 Range（206）响应。
     *
     * @param file     文件路径
     * @param metadata 文件元信息
     * @param range    请求范围
     * @return 206 响应
     */
    private Mono<ServerResponse> partialContent(Path file, FileMetadata metadata, HttpRange range) {
        long start = range.getRangeStart(metadata.length());
        long end = range.getRangeEnd(metadata.length());
        long count = end - start + 1;
        ServerResponse.BodyBuilder builder = ServerResponse.status(HttpStatus.PARTIAL_CONTENT);
        builder.contentType(metadata.mediaType());
        builder.contentLength(count);
        builder.eTag(metadata.etag());
        builder.header(HttpHeaders.ACCEPT_RANGES, "bytes");
        builder.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + metadata.length());
        builder.header(HttpHeaders.LAST_MODIFIED, formatDate(metadata.lastModified()));
        applyCacheControl(builder);
        return builder.body(BodyInserters.fromDataBuffers(read(file, start, count)));
    }

    /**
     * 构建带公共头的基础响应构建器。
     *
     * @param metadata 文件元信息
     * @return 响应构建器
     */
    private ServerResponse.BodyBuilder baseBuilder(FileMetadata metadata) {
        ServerResponse.BodyBuilder builder = ServerResponse.ok().contentType(metadata.mediaType());
        builder.eTag(metadata.etag());
        builder.header(HttpHeaders.LAST_MODIFIED, formatDate(metadata.lastModified()));
        applyCacheControl(builder);
        return builder;
    }

    /**
     * 判断是否命中 ETag 或 If-Modified-Since 条件。
     *
     * @param request  请求
     * @param metadata 文件元信息
     * @return 是否未修改
     */
    private static boolean isNotModified(ServerRequest request, FileMetadata metadata) {
        List<String> ifNoneMatch = request.headers().asHttpHeaders().getIfNoneMatch();
        if (!ifNoneMatch.isEmpty()) {
            return matchesEtag(ifNoneMatch, metadata.etag());
        }
        long ifModifiedSince = request.headers().asHttpHeaders().getIfModifiedSince();
        return ifModifiedSince > 0 && metadata.lastModified() / 1000 * 1000 <= ifModifiedSince;
    }

    /**
     * 判断 If-None-Match 是否命中（弱比较，忽略 W/ 弱校验前缀）。
     *
     * @param ifNoneMatch 请求携带的 ETag 列表
     * @param etag        当前资源 ETag
     * @return 是否命中
     */
    private static boolean matchesEtag(List<String> ifNoneMatch, String etag) {
        String strong = stripWeak(etag);
        for (String candidate : ifNoneMatch) {
            String trimmed = candidate == null ? "" : candidate.trim();
            if ("*".equals(trimmed) || strong.equals(stripWeak(trimmed))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去除 ETag 的弱校验前缀 {@code W/}。
     *
     * @param etag ETag
     * @return 去掉 W/ 前缀的 ETag
     */
    private static String stripWeak(String etag) {
        return etag.startsWith("W/") ? etag.substring(2) : etag;
    }

    /**
     * 判断是否应忽略 Range 请求（If-Range 不匹配时返回完整内容）。
     *
     * @param request  请求
     * @param metadata 文件元信息
     * @return 是否忽略 Range
     */
    private boolean ignoreRange(ServerRequest request, FileMetadata metadata) {
        String ifRange = request.headers().firstHeader(HttpHeaders.IF_RANGE);
        if (!StringUtils.hasText(ifRange)) {
            return false;
        }
        String value = ifRange.trim();
        if (value.startsWith("\"") || value.startsWith("W/")) {
            return !value.equals(metadata.etag());
        }
        try {
            ZonedDateTime date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            return metadata.lastModified() / 1000 * 1000 != date.toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            log.error("[Nginx-Static-IfRange] 解析 If-Range 失败, header: {}", value, e);
            return true;
        }
    }

    /**
     * 解析单段 Range 请求头，多段或非法时返回空（退回 200）。
     *
     * @param request 请求
     * @return 单段范围
     */
    private Optional<HttpRange> parseRange(ServerRequest request) {
        String rangeHeader = request.headers().firstHeader(HttpHeaders.RANGE);
        if (!StringUtils.hasText(rangeHeader)) {
            return Optional.empty();
        }
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            return ranges.size() == 1 ? Optional.of(ranges.get(0)) : Optional.empty();
        } catch (IllegalArgumentException e) {
            log.error("[Nginx-Static-Range] 解析 Range 失败, header: {}", rangeHeader, e);
            return Optional.empty();
        }
    }

    /**
     * 读取文件元信息（媒体类型、最后修改时间、大小、ETag）。
     *
     * @param file 文件路径
     * @return 文件元信息
     * @throws IOException 读取失败
     */
    private FileMetadata metadata(Path file) throws IOException {
        MediaType mediaType = determineMediaType(file);
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        long size = Files.size(file);
        String etag = String.format("\"%x-%x\"", lastModified / 1000, size);
        return new FileMetadata(withCharset(mediaType), lastModified, size, etag);
    }

    /**
     * 根据文件扩展名推断媒体类型，对现代 Web 类型提供原生支持。
     *
     * @param file 文件路径
     * @return 媒体类型
     */
    private MediaType determineMediaType(Path file) {
        Path fileNamePath = file.getFileName();
        if (fileNamePath != null) {
            String filename = fileNamePath.toString().toLowerCase(Locale.ROOT);
            if (filename.endsWith(".mjs")) {
                return MediaType.parseMediaType("text/javascript");
            }
            if (filename.endsWith(".wasm")) {
                return MediaType.parseMediaType("application/wasm");
            }
            if (filename.endsWith(".woff2")) {
                return MediaType.parseMediaType("font/woff2");
            }
            if (filename.endsWith(".webmanifest")) {
                return MediaType.parseMediaType("application/manifest+json");
            }
        }
        Resource resource = new FileSystemResource(file);
        return MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * 为文本类媒体类型追加字符集。
     *
     * @param mediaType 媒体类型
     * @return 追加字符集后的媒体类型
     */
    private MediaType withCharset(MediaType mediaType) {
        if (charset == null || mediaType.getCharset() != null || !isTextual(mediaType)) {
            return mediaType;
        }
        return new MediaType(mediaType, charset);
    }

    /**
     * 判断媒体类型是否为文本类。
     *
     * @param mediaType 媒体类型
     * @return 是否文本类
     */
    private static boolean isTextual(MediaType mediaType) {
        String type = mediaType.getType();
        String subtype = mediaType.getSubtype();
        if ("text".equalsIgnoreCase(type)) {
            return true;
        }
        if ("image".equalsIgnoreCase(type)) {
            return false;
        }
        return subtype.contains("json") || subtype.contains("javascript") || subtype.contains("xml");
    }

    /**
     * 追加 Cache-Control 响应头（配置非空时）。
     *
     * @param builder 响应构建器
     */
    private void applyCacheControl(ServerResponse.BodyBuilder builder) {
        if (StringUtils.hasText(location.getCacheControl())) {
            builder.header(HttpHeaders.CACHE_CONTROL, location.getCacheControl());
        }
    }

    /**
     * <p>以非阻塞方式读取文件内容，并在 {@code boundedElastic} 上执行读取。</p>
     * <p>当 {@code count} 为正数时仅读取 {@code [position, position + count)} 区间。</p>
     *
     * @param file     文件路径
     * @param position 起始偏移
     * @param count    读取字节数
     * @return 数据缓冲流
     */
    private static Flux<DataBuffer> read(Path file, long position, long count) {
        if (count <= 0) {
            return Flux.empty();
        }
        Resource resource = new FileSystemResource(file);
        AtomicLong remaining = new AtomicLong(count);
        return DataBufferUtils.read(resource, position, DefaultDataBufferFactory.sharedInstance, BUFFER_SIZE)
                .map(buffer -> truncate(buffer, remaining))
                .takeUntil(buffer -> remaining.get() <= 0)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 按剩余字节数截断数据缓冲。
     *
     * @param buffer    数据缓冲
     * @param remaining 剩余可读字节数
     * @return 截断后的数据缓冲
     */
    private static DataBuffer truncate(DataBuffer buffer, AtomicLong remaining) {
        long left = remaining.get();
        int readable = buffer.readableByteCount();
        if (readable <= left) {
            remaining.set(left - readable);
            return buffer;
        }
        byte[] tail = new byte[(int) Math.max(left, 0)];
        buffer.read(tail);
        DataBufferUtils.release(buffer);
        remaining.set(0);
        return DefaultDataBufferFactory.sharedInstance.wrap(tail);
    }

    /**
     * 将毫秒时间戳格式化为 RFC 1123。
     *
     * @param millis 毫秒时间戳
     * @return RFC 1123 时间字符串
     */
    private static String formatDate(long millis) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC));
    }

    /**
     * 将请求路径解析为根目录下的安全物理路径。
     *
     * @param requestPath 请求路径
     * @return 安全物理路径，越界时返回 null
     */
    private Path resolveRequestPath(String requestPath) {
        String relative;
        if ("/".equals(locationPath)) {
            relative = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        } else if (requestPath.equals(locationPath)) {
            relative = "";
        } else if (requestPath.startsWith(locationPath + "/")) {
            relative = requestPath.substring(locationPath.length() + 1);
        } else {
            relative = requestPath;
        }
        return resolveFromRoot(relative);
    }

    /**
     * 将相对路径解析为根目录下的安全物理路径。
     *
     * @param relative 相对路径
     * @return 安全物理路径，越界时返回 null
     */
    private Path resolveFromRoot(String relative) {
        if (relative == null || relative.isEmpty()) {
            return root;
        }
        String normalizedRelative = relative.startsWith("/") ? relative.substring(1) : relative;
        Path resolved = root.resolve(normalizedRelative).normalize();
        return resolved.startsWith(root) ? resolved : null;
    }

    /**
     * 解析请求路径：命中文件则返回文件，目录无斜杠则重定向，未命中且启用时回退 SPA 首页。
     *
     * @param requestPath 请求路径
     * @return 解析结果
     */
    private Resolution resolve(String requestPath) {
        Path candidate = resolveRequestPath(requestPath);
        if (candidate == null) {
            return new Resolution(null, false, true);
        }
        if (Files.isDirectory(candidate) && StringUtils.hasText(requestPath) && !requestPath.endsWith("/")) {
            return new Resolution(null, true, false);
        }
        Path file = findFile(candidate);
        if (file == null && StringUtils.hasText(location.getSpaFallback())) {
            file = findFile(resolveFromRoot(location.getSpaFallback()));
        }
        return new Resolution(file, false, false);
    }

    /**
     * 定位可用文件：直接命中文件，或目录下依次尝试索引文件。
     *
     * @param target 目标路径
     * @return 可用文件路径，未命中时返回 null
     */
    private Path findFile(Path target) {
        if (target == null) {
            return null;
        }
        if (Files.isRegularFile(target)) {
            return target;
        }
        if (Files.isDirectory(target)) {
            for (String indexFile : location.getIndex()) {
                Path candidate = target.resolve(indexFile);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * 判断请求方法是否允许读取静态资源。
     *
     * @param method 请求方法
     * @return 是否允许
     */
    private static boolean isReadableMethod(HttpMethod method) {
        return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method);
    }

    /**
     * 文件解析结果。
     *
     * @param file      命中文件，未命中时为 null
     * @param redirect  是否需要 301 重定向（目录补全末尾斜杠）
     * @param forbidden 是否为越界路径
     */
    private record Resolution(Path file, boolean redirect, boolean forbidden) {
    }

    /**
     * 文件元信息。
     *
     * @param mediaType    媒体类型
     * @param lastModified 最后修改时间毫秒值
     * @param length       文件大小
     * @param etag         实体标签 ETag
     */
    private record FileMetadata(MediaType mediaType, long lastModified, long length, String etag) {
    }

}
