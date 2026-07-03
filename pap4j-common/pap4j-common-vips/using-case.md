# 使用 `pap4j-common-vips` 实现高性能 IIIF 图像服务器

本教程介绍了如何基于 `pap4j-common-vips` 模块，为 Spring Boot 项目快速实现一个高性能、堆内存安全的 IIIF (International Image Interoperability Framework) 图像服务与超大图（例如 1.8GB）平滑缩放预览界面。

---

## 🚀 核心架构与原理
- **零堆内存溢出 (OOM-Proof)**：对于超大图像文件，传统的 Java ImageIO 会将整张图读入堆内存解压像素，极易导致 `java.lang.OutOfMemoryError: Java heap space`。而 `pap4j-common-vips` 底层基于高性能 C 动态库 `libvips`，采用**惰性像素流管道 (Lazy Pixel Pipeline)**，仅在请求特定瓦片 (Tile) 时，流式执行 native 层的裁剪 (`vips_crop`) 与缩放 (`vips_resize`)，每次返回给 Java 堆的只有输出的单个小瓦片字节数组（约几 KB 到几十 KB），从而实现极佳 of JVM 稳定性。
- **自动本地缓存重定向**：大图在转码过程中会产生临时缓存，模块已在 `ensureInitialized` 初始化时，通过 C 层的 `g_setenv` 自动将临时缓存重定向到 `D:/knowledge/temp` 目录，防范 C 盘空间不足（如 `No space left on device`）导致的底层写入失败。

---

## 🛠️ 第一步：引入依赖 (Maven `pom.xml`)
在需要实现 IIIF Image Server 的子模块的 `pom.xml` 中引入 `pap4j-common-vips` 的依赖：

```xml
<!-- 引入本地高吞吐 libvips 图像处理依赖 -->
<dependency>
    <groupId>cn.net.pap</groupId>
    <artifactId>pap4j-common-vips</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

---

## ☕ 第二步：编写 IIIF Image API 控制器 (`IiifController.java`)
根据 IIIF Image API v2 规范，核心需要提供两个默认的接口：
1. 获取图片尺寸与支持格式的元数据接口 (`info.json`)。
2. 根据请求坐标裁剪、缩放的瓦片转换接口 (`/iiif/2/{identifier}/{region}/{size}/{rotation}/{quality}.{format}`)。

新增以下控制器，其内部完全通过 `VipsImageProcessor` 完成流式像素处理：

```java

import cn.net.pap.common.vips.VipsImageProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 满足 IIIF Image API 标准的图像预览与信息查询控制器。
 *
 * <p>根据 IIIF v2 规范，包含以下两个默认的核心接口：
 * <ul>
 *   <li>1. 图像信息接口 (Image Information): <code>/iiif/2/{identifier}/info.json</code></li>
 *   <li>2. 图像预览与转换接口 (Image Request): <code>/iiif/2/{identifier}/{region}/{size}/{rotation}/{quality}.{format}</code></li>
 * </ul>
 * </p>
 */
@Tag(name = "IIIF Image API", description = "基于 libvips 高性能图片转码及 ImageIO 元数据实现的 IIIF 图像服务器预览与信息接口")
@CrossOrigin
@RestController
public class IiifController {
    private static final Logger log = LoggerFactory.getLogger(IiifController.class);

    // 设置图像文件的基础物理路径，指向 D 盘的存放目录
    private static final String BASE_DIR = "D:/knowledge/";

    /**
     * IIIF 规范接口 1：图像信息请求 (Image Information Request)，返回图片的尺寸、支持的格式等元数据信息。
     */
    @Operation(summary = "获取 IIIF 图像元数据 (info.json)", description = "符合 IIIF 规范的默认信息接口，动态提取图片高宽并返回标准的 JSON 元数据")
    @GetMapping(value = "/iiif/2/{identifier}/info.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getIiifInfo(
            @Parameter(description = "图像唯一标识符 (包含文件后缀的文件名)", example = "input_test.png")
            @PathVariable("identifier") String identifier,
            HttpServletRequest request
    ) {
        log.info("[IIIF-Info] 请求获取图片信息: {}", identifier);

        // 安全校验：防目录穿越
        if (isUnsafeIdentifier(identifier)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        File imageFile = new File(BASE_DIR, identifier);
        if (!imageFile.exists() || !imageFile.isFile()) {
            log.warn("[IIIF-Info] 图片未找到: {}", imageFile.getAbsolutePath());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        try {
            // 利用 libvips 惰性提取头部尺寸元数据，避免把整张图解压到 Java 堆内存，保护性能
            VipsImageProcessor.ImageMetadata metadata = VipsImageProcessor.getImageMetadata(imageFile.getAbsolutePath());

            String host = request.getHeader("Host");
            String scheme = request.getScheme();
            String baseUri = scheme + "://" + host + "/iiif/2/" + identifier;

            // 组装符合 IIIF Image API v2.1 规范的 JSON 响应体
            Map<String, Object> info = Map.of(
                    "@context", "http://iiif.io/api/image/2/context.json",
                    "@id", baseUri,
                    "protocol", "http://iiif.io/api/image",
                    "width", metadata.width(),
                    "height", metadata.height(),
                    "sizes", List.of(Map.of("width", metadata.width(), "height", metadata.height())),
                    "profile", List.of(
                            "http://iiif.io/api/image/2/level2.json",
                            Map.of(
                                    "formats", List.of("jpg", "png"),
                                    "qualities", List.of("default")
                            )
                    )
            );

            return ResponseEntity.ok(info);

        } catch (IOException e) {
            log.error("[IIIF-Info-Error] 读取图片尺寸失败: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * IIIF 规范接口 2：图像转换请求 (Image Request)，返回转换后（支持 jpg/png）的图像二进制流。
     */
    @Operation(summary = "获取 IIIF 图像预览", description = "解析符合 IIIF Image API 规范 the URL，并调用 libvips 进行高性能格式转换与输出")
    @GetMapping("/iiif/2/{identifier}/{region}/{size}/{rotation}/{quality}.{format}")
    public ResponseEntity<byte[]> getIiifImage(
            @Parameter(description = "图像唯一标识符 (包含文件后缀的文件名)", example = "input_test.png")
            @PathVariable("identifier") String identifier,
            @Parameter(description = "裁剪区域，当前默认全图：full", example = "full")
            @PathVariable("region") String region,
            @Parameter(description = "缩放比例，当前默认全尺寸：full", example = "full")
            @PathVariable("size") String size,
            @Parameter(description = "旋转角度，当前默认不旋转：0", example = "0")
            @PathVariable("rotation") String rotation,
            @Parameter(description = "输出色彩品质：default", example = "default")
            @PathVariable("quality") String quality,
            @Parameter(description = "目标输出格式：png, jpg 等", example = "jpg")
            @PathVariable("format") String format
    ) {
        log.info("[IIIF-Request] 请求预览图片: {}, region: {}, size: {}, rotation: {}, quality: {}, format: {}",
                identifier, region, size, rotation, quality, format);

        // 安全校验：防目录穿越
        if (isUnsafeIdentifier(identifier)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        File imageFile = new File(BASE_DIR, identifier);
        if (!imageFile.exists() || !imageFile.isFile()) {
            log.warn("[IIIF-Request] 图片未找到: {}", imageFile.getAbsolutePath());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        try {
            // 1. 获取图片尺寸元数据
            VipsImageProcessor.ImageMetadata metadata = VipsImageProcessor.getImageMetadata(imageFile.getAbsolutePath());
            int imgWidth = metadata.width();
            int imgHeight = metadata.height();

            // 2. 解析 region 参数
            Integer left = null;
            Integer top = null;
            Integer width = null;
            Integer height = null;

            if (!"full".equalsIgnoreCase(region) && !"square".equalsIgnoreCase(region)) {
                if (region.startsWith("pct:")) {
                    String[] parts = region.substring(4).split(",");
                    if (parts.length == 4) {
                        double rx = Double.parseDouble(parts[0]);
                        double ry = Double.parseDouble(parts[1]);
                        double rw = Double.parseDouble(parts[2]);
                        double rh = Double.parseDouble(parts[3]);
                        left = (int) Math.round(imgWidth * rx / 100.0);
                        top = (int) Math.round(imgHeight * ry / 100.0);
                        width = (int) Math.round(imgWidth * rw / 100.0);
                        height = (int) Math.round(imgHeight * rh / 100.0);
                    }
                } else {
                    String[] parts = region.split(",");
                    if (parts.length == 4) {
                        left = Integer.parseInt(parts[0]);
                        top = Integer.parseInt(parts[1]);
                        width = Integer.parseInt(parts[2]);
                        height = Integer.parseInt(parts[3]);
                    }
                }
            }

            // 安全边界处理
            if (left != null && top != null && width != null && height != null) {
                left = Math.max(0, Math.min(left, imgWidth - 1));
                top = Math.max(0, Math.min(top, imgHeight - 1));
                width = Math.max(1, Math.min(width, imgWidth - left));
                height = Math.max(1, Math.min(height, imgHeight - top));
            }

            // 3. 计算裁剪后区域的宽高以进行缩放比计算
            int cropWidth = (width != null) ? width : imgWidth;
            int cropHeight = (height != null) ? height : imgHeight;

            // 4. 解析 size 参数并计算缩放比例
            Double scale = null;
            if (!"full".equalsIgnoreCase(size) && !"max".equalsIgnoreCase(size)) {
                if (size.startsWith("pct:")) {
                    double pct = Double.parseDouble(size.substring(4));
                    scale = pct / 100.0;
                } else if (size.startsWith("!")) {
                    String[] parts = size.substring(1).split(",");
                    if (parts.length == 2) {
                        int w = Integer.parseInt(parts[0]);
                        int h = Integer.parseInt(parts[1]);
                        scale = Math.min((double) w / cropWidth, (double) h / cropHeight);
                    }
                } else {
                    String[] parts = size.split(",");
                    if (parts.length == 2) {
                        if (size.endsWith(",")) {
                            int w = Integer.parseInt(parts[0]);
                            scale = (double) w / cropWidth;
                        } else if (size.startsWith(",")) {
                            int h = Integer.parseInt(parts[1]);
                            scale = (double) h / cropHeight;
                        } else {
                            int w = Integer.parseInt(parts[0]);
                            int h = Integer.parseInt(parts[1]);
                            scale = (double) w / cropWidth;
                        }
                    } else if (parts.length == 1) {
                        int w = Integer.parseInt(parts[0]);
                        scale = (double) w / cropWidth;
                    }
                }
            }

            // 5. 基于 libvips 执行流式图像处理
            byte[] convertedBytes = VipsImageProcessor.processImage(
                    imageFile.getAbsolutePath(),
                    left, top, width, height,
                    scale,
                    format
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(getMediaType(format));
            headers.setContentLength(convertedBytes.length);
            headers.setCacheControl("max-age=3600"); // 浏览器缓存一小时

            return new ResponseEntity<>(convertedBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            log.warn("[IIIF-Validation] 参数转换不合规: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (IOException e) {
            log.error("[IIIF-IO] 读取或处理图像时发生 IO 错误: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } catch (Throwable t) {
            log.error("[IIIF-Fatal] 发生底层系统级致命错误: ", t);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * 判断是否是具有目录穿越安全隐患的非安全标识符
     */
    private boolean isUnsafeIdentifier(String identifier) {
        return identifier.contains("..") || identifier.contains("/") || identifier.contains("\\");
    }

    /**
     * 辅助解析输出格式的 MediaType。
     */
    private MediaType getMediaType(String format) {
        String lowerFormat = format.toLowerCase();
        switch (lowerFormat) {
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "png":
                return MediaType.IMAGE_PNG;
            case "gif":
                return MediaType.IMAGE_GIF;
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
```

---

## 🎨 第三步：前端 zoomable 图像预览页面 (`iiif-preview.html`)
OpenSeadragon 是一个非常流行的能够渲染 IIIF Image API 标准的深度缩放（Deep Zoom）前端组件。

新建以下 HTML 页面，即可实时拉取后台的 `info.json` 并以瓦片图渲染模式动态平移与缩放超大图片：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>IIIF Image API 高性能图像预览服务</title>
    <!-- 引入 Google Fonts Inter 与 Outfit -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Outfit:wght@400;500;600;700&display=swap" rel="stylesheet">
    <!-- 引入 OpenSeadragon 核心库 -->
    <script src="https://cdnjs.cloudflare.com/ajax/libs/openseadragon/4.1.0/openseadragon.min.js"></script>
    <style>
        :root {
            --bg-color: #0d1117;
            --panel-color: #161b22;
            --border-color: #30363d;
            --accent-color: #1f6feb;
            --accent-glow: rgba(31, 111, 235, 0.4);
            --text-primary: #c9d1d9;
            --text-secondary: #8b949e;
            --text-white: #ffffff;
            --success-color: #238636;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-primary);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            overflow-x: hidden;
        }

        header {
            background-color: var(--panel-color);
            border-bottom: 1px solid var(--border-color);
            padding: 1.5rem 2rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .header-title {
            font-family: 'Outfit', sans-serif;
            font-size: 1.5rem;
            font-weight: 700;
            color: var(--text-white);
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .header-title span {
            background: linear-gradient(135deg, #58a6ff, var(--accent-color));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .header-badge {
            background-color: rgba(56, 139, 253, 0.15);
            color: #58a6ff;
            font-size: 0.75rem;
            font-weight: 600;
            padding: 0.25rem 0.6rem;
            border-radius: 12px;
            border: 1px solid rgba(56, 139, 253, 0.3);
        }

        main {
            flex: 1;
            display: grid;
            grid-template-columns: 350px 1fr;
            height: calc(100vh - 73px);
        }

        .sidebar {
            background-color: var(--panel-color);
            border-right: 1px solid var(--border-color);
            padding: 2rem;
            display: flex;
            flex-direction: column;
            gap: 1.5rem;
            overflow-y: auto;
        }

        .section-title {
            font-family: 'Outfit', sans-serif;
            font-size: 1rem;
            font-weight: 600;
            color: var(--text-white);
            margin-bottom: 0.75rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .form-group {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            margin-bottom: 1rem;
        }

        .form-label {
            font-size: 0.85rem;
            font-weight: 500;
            color: var(--text-secondary);
        }

        .form-input {
            background-color: var(--bg-color);
            border: 1px solid var(--border-color);
            border-radius: 6px;
            color: var(--text-white);
            padding: 0.6rem 0.8rem;
            font-size: 0.9rem;
            transition: border-color 0.2s, box-shadow 0.2s;
            outline: none;
            width: 100%;
        }

        .form-input:focus {
            border-color: var(--accent-color);
            box-shadow: 0 0 0 3px var(--accent-glow);
        }

        .form-select {
            background-color: var(--bg-color);
            border: 1px solid var(--border-color);
            border-radius: 6px;
            color: var(--text-white);
            padding: 0.6rem 0.8rem;
            font-size: 0.9rem;
            outline: none;
            cursor: pointer;
            transition: border-color 0.2s;
        }

        .form-select:focus {
            border-color: var(--accent-color);
        }

        .btn {
            background-color: var(--accent-color);
            color: var(--text-white);
            border: none;
            border-radius: 6px;
            padding: 0.75rem 1rem;
            font-size: 0.95rem;
            font-weight: 600;
            cursor: pointer;
            transition: background-color 0.2s, transform 0.1s;
            display: flex;
            justify-content: center;
            align-items: center;
            gap: 0.5rem;
            box-shadow: 0 4px 12px rgba(31, 111, 235, 0.2);
        }

        .btn:hover {
            background-color: #2f81f7;
        }

        .btn:active {
            transform: scale(0.98);
        }

        .info-card {
            background-color: rgba(255, 255, 255, 0.03);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 1rem;
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
        }

        .info-item {
            display: flex;
            flex-direction: column;
            gap: 0.25rem;
        }

        .info-label {
            font-size: 0.75rem;
            color: var(--text-secondary);
            font-weight: 500;
        }

        .info-value {
            font-size: 0.85rem;
            font-family: monospace;
            word-break: break-all;
            color: #58a6ff;
            background-color: rgba(0, 0, 0, 0.2);
            padding: 0.25rem 0.4rem;
            border-radius: 4px;
            border: 1px solid rgba(255, 255, 255, 0.05);
        }

        .viewer-container {
            padding: 2rem;
            display: flex;
            flex-direction: column;
            gap: 1.5rem;
            background-color: var(--bg-color);
            position: relative;
        }

        .osd-canvas-wrapper {
            flex: 1;
            background-color: #000;
            border: 1px solid var(--border-color);
            border-radius: 12px;
            overflow: hidden;
            position: relative;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
            display: flex;
            justify-content: center;
            align-items: center;
        }

        #openseadragon-viewer {
            width: 100%;
            height: 100%;
        }

        .placeholder-text {
            color: var(--text-secondary);
            font-size: 1.1rem;
            text-align: center;
            display: flex;
            flex-direction: column;
            gap: 1rem;
            align-items: center;
            z-index: 10;
        }

        .placeholder-text svg {
            width: 64px;
            height: 64px;
            fill: var(--border-color);
        }

        .instructions {
            font-size: 0.85rem;
            color: var(--text-secondary);
            line-height: 1.5;
        }

        .instructions ul {
            margin-left: 1.2rem;
            margin-top: 0.5rem;
            display: flex;
            flex-direction: column;
            gap: 0.25rem;
        }

        /* 动画与状态指示 */
        .glowing-dot {
            width: 8px;
            height: 8px;
            background-color: var(--success-color);
            border-radius: 50%;
            display: inline-block;
            box-shadow: 0 0 8px var(--success-color);
        }
    </style>
</head>
<body>

    <header>
        <div class="header-title">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#58a6ff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>
            <span>IIIF Image API</span> 图像服务器预览
        </div>
        <div style="display: flex; align-items: center; gap: 1rem;">
            <div style="display: flex; align-items: center; gap: 0.5rem; font-size: 0.85rem;">
                <span class="glowing-dot"></span>
                <span>libvips 引擎在线</span>
            </div>
            <span class="header-badge">v2.1 API</span>
        </div>
    </header>

    <main>
        <div class="sidebar">
            <div>
                <h3 class="section-title">图像配置</h3>
                <div class="form-group">
                    <label class="form-label" for="server-url">IIIF 服务端基地址 (如 Proguard 端口)</label>
                    <input class="form-input" type="text" id="server-url" value="http://localhost:30000" placeholder="例如: http://localhost:30000">
                </div>
                <div class="form-group">
                    <label class="form-label" for="identifier">图像标识符 (文件名)</label>
                    <input class="form-input" type="text" id="identifier" value="input_test.png" placeholder="例如: input_test.png">
                </div>
                <div class="form-group">
                    <label class="form-label" for="format">输出格式 (通过 vips 动态转码)</label>
                    <select class="form-select" id="format">
                        <option value="jpeg">JPEG (经典兼容)</option>
                        <option value="png">PNG (无损画质)</option>
                    </select>
                </div>
                <button class="btn" id="load-btn">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg>
                    载入图像预览
                </button>
            </div>

            <div>
                <h3 class="section-title">IIIF 规范接口监测</h3>
                <div class="info-card">
                    <div class="info-item">
                        <span class="info-label">元数据接口 (info.json)</span>
                        <div class="info-value" id="url-info">未载入</div>
                    </div>
                    <div class="info-item">
                        <span class="info-label">当前视图转码接口 (Image Request)</span>
                        <div class="info-value" id="url-image">未载入</div>
                    </div>
                </div>
            </div>

            <div class="instructions">
                <h3 class="section-title" style="margin-bottom: 0.5rem;">使用指南</h3>
                <p>1. 请确保在 <b>D:/knowledge/</b> 目录下已放入目标图片文件（例如默认生成的 <i>input_test.png</i>）。</p>
                <p style="margin-top: 0.5rem;">2. 点击“载入图像预览”后：</p>
                <ul>
                    <li>OpenSeadragon 会自动拉取 `info.json` 元数据获取高宽。</li>
                    <li>加载成功后即可在右侧使用鼠标滚轮平滑缩放、拖拽和平移图片。</li>
                </ul>
            </div>
        </div>

        <div class="viewer-container">
            <div class="osd-canvas-wrapper">
                <div id="openseadragon-viewer"></div>
                <div class="placeholder-text" id="viewer-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>
                    <span>请在左侧配置图像并点击“载入图像预览”按钮</span>
                </div>
            </div>
        </div>
    </main>

    <script>
        var viewer = null;

        document.getElementById('load-btn').addEventListener('click', function() {
            var identifier = document.getElementById('identifier').value.trim();
            var format = document.getElementById('format').value;

            if (!identifier) {
                alert('请先输入图像标识符！');
                return;
            }

            // 隐藏占位符
            document.getElementById('viewer-placeholder').style.display = 'none';

            // 动态生成并更新 IIIF 接口 URL 监控看板
            var baseUri = document.getElementById('server-url').value.trim();
            if (baseUri.endsWith('/')) {
                baseUri = baseUri.substring(0, baseUri.length - 1);
            }

            var infoUrl = baseUri + '/iiif/2/' + identifier + '/info.json';
            var imageUrl = baseUri + '/iiif/2/' + identifier + '/full/full/0/default.' + format;

            document.getElementById('url-info').innerText = infoUrl;
            document.getElementById('url-image').innerText = imageUrl;

            // 销毁旧 of OSD 实例防止容器冲突
            if (viewer) {
                viewer.destroy();
            }

            // 初始化 OpenSeadragon 并加载符合 IIIF 规范的 info.json 配置源
            viewer = OpenSeadragon({
                id: "openseadragon-viewer",
                prefixUrl: "https://cdnjs.cloudflare.com/ajax/libs/openseadragon/4.1.0/images/",
                // 直接指向符合 IIIF 规范的 info.json 服务接口
                tileSources: infoUrl,
                // 可选参数：显示导航小地图
                showNavigator: true,
                navigatorPosition: "BOTTOM_RIGHT",
                // 开启平滑变焦动画
                gestureSettingsMouse: {
                    clickToZoom: true,
                    dblClickToZoom: true,
                    pinchToZoom: true
                }
            });

            // 异常监听，提示找不到文件等错误
            viewer.addHandler('open-failed', function(event) {
                document.getElementById('viewer-placeholder').style.display = 'flex';
                document.getElementById('viewer-placeholder').querySelector('span').innerText = '图像载入失败！请检查 D:/knowledge/ 是否存在该图片，或查看后台服务日志。';
                alert('载入失败：未能获取图像元数据 (info.json)，请确保 D:/knowledge/' + identifier + ' 文件存在。');
            });
        });
    </script>
</body>
</html>
```
