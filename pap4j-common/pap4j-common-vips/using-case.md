# 使用 `pap4j-common-vips` 实现高性能 IIIF 图像服务器

本教程介绍了如何基于 `pap4j-common-vips` 模块，为 Spring Boot 项目快速实现一个高性能、堆内存安全的 IIIF (International Image Interoperability Framework) 图像服务与超大图（例如 1.8GB）平滑缩放预览界面。

---

## 🚀 核心架构与原理
- **零堆内存溢出 (OOM-Proof)**：对于超大图像文件，传统的 Java ImageIO 会将整张图读入堆内存解压像素，极易导致 `java.lang.OutOfMemoryError: Java heap space`。而 `pap4j-common-vips` 底层基于高性能 C 动态库 `libvips`，采用**惰性像素流管道 (Lazy Pixel Pipeline)**，仅在请求特定瓦片 (Tile) 时，流式执行 native 层的裁剪 (`vips_crop`) 与缩放 (`vips_resize`)，每次返回给 Java 堆的只有输出的单个小瓦片字节数组（约几 KB 到几十 KB），从而实现极佳 of JVM 稳定性。
- **自动本地缓存重定向**：大图在转码过程中会产生临时缓存，模块已在 `ensureInitialized` 初始化时，通过 C 层的 `g_setenv` 自动将临时缓存重定向到本地临时工作目录（默认使用当前用户的系统临时目录，支持通过 `PAP_VIPS_TEMP_DIR` 环境变量进行自定义配置）。

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

    // TODO: 底层 VipsImageProcessor 处理超大图片时会自动生成中间临时缓存，默认使用当前用户的系统临时目录（如 C 盘）。
    // TODO: 为了防止系统盘空间不足导致转码失败或响应缓慢，请务必在部署或启动前配置 PAP_VIPS_TEMP_DIR JVM 系统属性或环境变量，将其重定向到空间充足的非系统盘（例如 D:/knowledge/temp）。
    // 示例代码实现思路（在类加载最早期，或者在 Spring Boot 启动类的 main 方法中设置）：
    // static {
    //     System.setProperty("PAP_VIPS_TEMP_DIR", "D:/knowledge/temp");
    // }

    /**
     * IIIF 规范接口 1：图像信息请求 (Image Information Request)，返回图片的尺寸、支持的格式等元数据信息。
     */
    @Operation(summary = "获取 IIIF 图像元数据 (info.json)", description = "符合 IIIF 规范的默认信息接口，动态提取图片高宽并返回标准的 JSON 元数据")
    @GetMapping(value = "/iiif/2/{identifier}/info.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getIiifInfo(
            // 生产环境下建议将 identifier 设计为"物理路径"对称加密（如AES）后的密文，用以规避目录穿越、写锁冲突及多瓦片画面撕裂。
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
                    "tiles", List.of(
                            Map.of(
                                    "width", 256,
                                    "scaleFactors", List.of(1, 2, 4, 8, 16, 32)
                            )
                    ),
                    "profile", List.of(
                            "http://iiif.io/api/image/2/level2.json",
                            Map.of(
                                    "formats", List.of("jpg", "png"),
                                    "qualities", List.of("default", "color", "gray", "bitonal")
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
     *
     * <p><b>关于旋转（Rotation）参数的集成规约与客户端适配机制：</b></p>
     * <ul>
     *   <li><b>单图或整图请求 (Single Image Request)</b>：若请求 region 为 <code>full</code>，后端会利用 libvips 对整图进行物理旋转，
     *       旋转 90/270 度后输出图像的宽高物理尺寸会正确对调，以展示符合预期的旋转后整图。</li>
     *   <li><b>瓦片流式加载 (Tiled/Deep Zoom Request)</b>：在配合 OpenSeadragon 等标准 IIIF 瓦片加载客户端使用时，
     *       客户端会以 unrotated 的 <code>info.json</code> 原始宽高为基准布局瓦片。若在瓦片请求 URL 中直接传入非零 rotation 参数，
     *       会导致后端对每个局部瓦片单独旋转，而在前端由于拼装坐标未变，会产生整体长宽不变（宽高没变）且瓦片拼装内容严重挤压变形（图像做了压缩）的问题。
     *       因此，对于瓦片加载，<b>客户端请求 of URL 瓦片旋转参数必须保持为 0</b>，而整图的旋转与翻转应由前端通过视口 API（如
     *       <code>viewer.viewport.setRotation(angle)</code> 和 <code>setFlip</code>）进行客户端 Canvas 级的统一旋转呈现。</li>
     * </ul>
     */
    @Operation(summary = "获取 IIIF 图像预览", description = "解析符合 IIIF Image API 规范 the URL，并调用 libvips 进行高性能格式转换与输出")
    @GetMapping("/iiif/2/{identifier}/{region}/{size}/{rotation}/{quality}.{format}")
    public ResponseEntity<byte[]> getIiifImage(
            // 生产环境下建议将 identifier 设计为"物理路径"对称加密（如AES）后的密文，用以规避目录穿越、写锁冲突及多瓦片画面撕裂。
            @Parameter(description = "图像唯一标识符 (包含文件后缀的文件名)", example = "input_test.png")
            @PathVariable("identifier") String identifier,
            @Parameter(description = "裁剪区域，当前默认全图：full", example = "full")
            @PathVariable("region") String region,
            @Parameter(description = "缩放比例，当前默认全尺寸：full", example = "full")
            @PathVariable("size") String size,
            @Parameter(description = "旋转角度，当前默认不旋转：0", example = "0")
            @PathVariable("rotation") String rotation,
            @Parameter(description = "输出色彩品质 (支持 default, color, gray, bitonal)", example = "default")
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

            if ("square".equalsIgnoreCase(region)) {
                int minDim = Math.min(imgWidth, imgHeight);
                left = (imgWidth - minDim) / 2;
                top = (imgHeight - minDim) / 2;
                width = minDim;
                height = minDim;
            } else if (!"full".equalsIgnoreCase(region)) {
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

            // 4. 解析 size 参数并计算横向与纵向的缩放比例
            Double hScale = null;
            Double vScale = null;
            if (!"full".equalsIgnoreCase(size) && !"max".equalsIgnoreCase(size)) {
                if (size.startsWith("pct:")) {
                    double pct = Double.parseDouble(size.substring(4));
                    hScale = pct / 100.0;
                } else if (size.startsWith("!")) {
                    String[] parts = size.substring(1).split(",");
                    if (parts.length == 2) {
                        int w = Integer.parseInt(parts[0]);
                        int h = Integer.parseInt(parts[1]);
                        hScale = Math.min((double) w / cropWidth, (double) h / cropHeight);
                    }
                } else {
                    String[] parts = size.split(",");
                    if (parts.length == 2) {
                        if (size.endsWith(",")) {
                            int w = Integer.parseInt(parts[0]);
                            hScale = (double) w / cropWidth;
                        } else if (size.startsWith(",")) {
                            int h = Integer.parseInt(parts[1]);
                            hScale = (double) h / cropHeight;
                        } else {
                            int w = Integer.parseInt(parts[0]);
                            int h = Integer.parseInt(parts[1]);
                            hScale = (double) w / cropWidth;
                            vScale = (double) h / cropHeight;
                        }
                    } else if (parts.length == 1) {
                        int w = Integer.parseInt(parts[0]);
                        hScale = (double) w / cropWidth;
                    }
                }
            }

            // 5. 基于 libvips 执行流式图像处理
            byte[] convertedBytes = VipsImageProcessor.processImage(
                    imageFile.getAbsolutePath(),
                    left, top, width, height,
                    hScale, vScale,
                    rotation,
                    quality,
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
                    <label class="form-label" for="rotation">旋转与镜像 (Rotation)</label>
                    <input class="form-input" type="text" id="rotation" value="0" placeholder="例如: 90, !180, 45">
                </div>
                <div class="form-group">
                    <label class="form-label" for="format">输出格式 (通过 vips 动态转码)</label>
                    <select class="form-select" id="format">
                        <option value="jpeg">JPEG (经典兼容)</option>
                        <option value="png">PNG (无损画质)</option>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label" for="quality">色彩品质 (通过 vips 动态处理)</label>
                    <select class="form-select" id="quality">
                        <option value="default">Default (默认/不改变)</option>
                        <option value="color">Color (彩色)</option>
                        <option value="gray">Gray (灰度)</option>
                        <option value="bitonal">Bitonal (二值化/黑白)</option>
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
        var cachedInfoData = null;
        var cachedKey = null;

        document.getElementById('load-btn').addEventListener('click', function() {
            loadIiifViewer(true); // 按钮点击强制重新获取元数据
        });

        document.getElementById('format').addEventListener('change', function() {
            loadIiifViewer(false); // 仅修改格式时使用缓存的元数据加载
        });

        document.getElementById('quality').addEventListener('change', function() {
            loadIiifViewer(false); // 仅修改色彩品质时使用缓存的元数据加载
        });

        document.getElementById('rotation').addEventListener('change', function() {
            loadIiifViewer(false); // 仅修改旋转/镜像时使用缓存的元数据加载
        });

        function loadIiifViewer(forceFetch) {
            var identifier = document.getElementById('identifier').value.trim();
            var baseUri = document.getElementById('server-url').value.trim();
            if (baseUri.endsWith('/')) {
                baseUri = baseUri.substring(0, baseUri.length - 1);
            }

            if (!identifier) {
                alert('请先输入图像标识符！');
                return;
            }

            var infoUrl = baseUri + '/iiif/2/' + identifier + '/info.json';
            var currentKey = baseUri + '||' + identifier;

            if (!forceFetch && cachedInfoData && cachedKey === currentKey) {
                // 使用已缓存的元数据直接重新载入视图
                renderViewer(cachedInfoData, baseUri, identifier);
            } else {
                // 首次载入或标识符变更时，先获取 info.json
                fetch(infoUrl)
                    .then(response => {
                        if (!response.ok) {
                            throw new Error('无法获取图像元数据 (info.json)');
                        }
                        return response.json();
                    })
                    .then(infoData => {
                        cachedInfoData = infoData;
                        cachedKey = currentKey;
                        renderViewer(infoData, baseUri, identifier);
                    })
                    .catch(error => {
                        document.getElementById('viewer-placeholder').style.display = 'flex';
                        document.getElementById('viewer-placeholder').querySelector('span').innerText = '获取元数据或载入图像失败！请检查图片是否存在，以及服务端是否正常运行。';
                        alert(error.message);
                    });
            }
        }

        function renderViewer(infoData, baseUri, identifier) {
            // 动态解析 info.json 中声明支持的 formats 与 qualities
            var formats = [];
            var qualities = [];

            if (infoData.profile) {
                var profileList = Array.isArray(infoData.profile) ? infoData.profile : [infoData.profile];
                for (var i = 0; i < profileList.length; i++) {
                    var p = profileList[i];
                    if (typeof p === 'object' && p !== null) {
                        if (p.formats) formats = p.formats;
                        if (p.qualities) qualities = p.qualities;
                    }
                }
            }

            // 元数据未提供时的降级默认值
            if (formats.length === 0) formats = ['jpg', 'png'];
            if (qualities.length === 0) qualities = ['default'];

            var formatSelect = document.getElementById('format');
            var qualitySelect = document.getElementById('quality');
            var rotation = document.getElementById('rotation').value.trim() || '0';

            // 暂存当前的选择，待列表刷新后尝试恢复
            var prevFormat = formatSelect.value;
            var prevQuality = qualitySelect.value;

            // 动态重建格式下拉选项
            formatSelect.innerHTML = '';
            formats.forEach(function(fmt) {
                var option = document.createElement('option');
                option.value = fmt;
                var displayLabel = fmt.toUpperCase();
                if (fmt === 'jpg' || fmt === 'jpeg') displayLabel = 'JPEG (经典兼容)';
                if (fmt === 'png') displayLabel = 'PNG (无损画质)';
                option.text = displayLabel;
                formatSelect.appendChild(option);
            });

            // 动态色彩品质下拉选项
            qualitySelect.innerHTML = '';
            qualities.forEach(function(q) {
                var option = document.createElement('option');
                option.value = q;
                var displayLabel = q.charAt(0).toUpperCase() + q.slice(1);
                if (q === 'default') displayLabel = 'Default (默认/不改变)';
                if (q === 'color') displayLabel = 'Color (彩色)';
                if (q === 'gray') displayLabel = 'Gray (灰度)';
                if (q === 'bitonal') displayLabel = 'Bitonal (二值化/黑白)';
                option.text = displayLabel;
                qualitySelect.appendChild(option);
            });

            // 恢复先前的选择 (若在新支持列表中)
            if (formats.includes(prevFormat)) {
                formatSelect.value = prevFormat;
            } else {
                formatSelect.selectedIndex = 0;
            }

            if (qualities.includes(prevQuality)) {
                qualitySelect.value = prevQuality;
            } else {
                qualitySelect.selectedIndex = 0;
            }

            var format = formatSelect.value;
            var quality = qualitySelect.value;

            // 更新监控看板的接口 URL
            var infoUrl = baseUri + '/iiif/2/' + identifier + '/info.json';
            var imageUrl = baseUri + '/iiif/2/' + identifier + '/full/full/' + rotation + '/' + quality + '.' + format;
            document.getElementById('url-info').innerText = infoUrl;
            document.getElementById('url-image').innerText = imageUrl;

            // 隐藏占位符
            document.getElementById('viewer-placeholder').style.display = 'none';

            // 销毁旧的 OSD 实例防止容器冲突
            if (viewer) {
                viewer.destroy();
            }

            // 初始化 OpenSeadragon 并加载符合 IIIF 规范的 info.json 数据源
            viewer = OpenSeadragon({
                id: "openseadragon-viewer",
                prefixUrl: "https://cdnjs.cloudflare.com/ajax/libs/openseadragon/4.1.0/images/",
                tileSources: infoData, // 直接传入预先获取的 info.json 数据对象
                showNavigator: true,
                navigatorPosition: "BOTTOM_RIGHT",
                gestureSettingsMouse: {
                    clickToZoom: true,
                    dblClickToZoom: true,
                    pinchToZoom: true
                }
            });

            // 动态拦截瓦片请求，注入选择的 quality 与 format 参数，以及 rotation 参数
            viewer.addHandler('open', function() {
                // 解析角度与翻转状态，并通过 OpenSeadragon 视口进行展示，避免旋转 90 度等非 0 视角瓦片导致图像拉伸与压缩
                var cleanAngle = parseFloat(rotation.replace('!', '')) || 0;
                viewer.viewport.setRotation(cleanAngle);
                if (rotation.startsWith('!')) {
                    viewer.viewport.setFlip(true);
                } else {
                    viewer.viewport.setFlip(false);
                }

                var tileSource = viewer.world.getItemAt(0).source;
                var originalGetTileUrl = tileSource.getTileUrl;
                tileSource.getTileUrl = function(level, x, y) {
                    var url = originalGetTileUrl.call(this, level, x, y);
                    if (url) {
                        var parts = url.split('/');
                        if (parts.length >= 4) {
                            var lastPart = parts[parts.length - 1];
                            var params = '';
                            var qIndex = lastPart.indexOf('?');
                            if (qIndex !== -1) {
                                params = lastPart.substring(qIndex);
                                lastPart = lastPart.substring(0, qIndex);
                            }
                            parts[parts.length - 1] = quality + '.' + format + params;
                            parts[parts.length - 2] = '0'; // 强制瓦片请求为 0 旋转，由前端 OpenSeadragon 视口负责整体旋转与镜像翻转
                        }
                        url = parts.join('/');
                    }
                    return url;
                };
            });

            // 异常监听，提示找不到文件等错误
            viewer.addHandler('open-failed', function(event) {
                document.getElementById('viewer-placeholder').style.display = 'flex';
                document.getElementById('viewer-placeholder').querySelector('span').innerText = '图像载入失败！请检查 D:/knowledge/ 是否存在该图片，或查看后台服务日志。';
                alert('载入失败：未能获取图像元数据 (info.json)，请确保 D:/knowledge/' + identifier + ' 文件存在。');
            });
        }
    </script>
</body>
</html>
```

---

## ⚡ 性能调优：使用金字塔 TIFF (Pyramid TIFF) 解决高频 I/O 写入问题

### 1. 现象与原理分析
对于普通的超大图片（如未切片/未分块的 `.png`、`.jpg` 或普通的 `.tif` 文件），当用户在浏览器（如 OpenSeadragon）中进行高频缩放、拖拽预览时，后端会接收到大量的并发切片（Tile）请求。
- **高频写入问题**：由于这类格式不支持随机像素读取，`libvips` 在处理 `vips_crop`（裁剪）时必须先对图像进行解压。对于 1.8GB 以上的超大图像，为防止 Java 堆内存溢出 (OOM) 并限制 Native 内存占用，`libvips` 会自动采用 **Spill-to-disk（溢出到磁盘）** 机制，在本地临时文件夹（默认为系统默认临时目录，可以通过 `PAP_VIPS_TEMP_DIR` 环境变量指定为空间充足的磁盘分区）下生成一个原始解压的临时中间文件。这会导致在刚开始浏览大图时，任务管理器中的硬盘写入 IO 瞬间飙升。
- **无感延迟与归零**：一旦该临时文件生成并被打开，后续由于浏览器的 HTTP 缓存、`libvips` 内部的算子缓存（VipsCache）以及操作系统的页面缓存（Page Cache）的共同作用，多次并发请求将直接走内存命中，磁盘写入 IO 随即归零。

### 2. 终极解决方案：引入金字塔 TIFF (Pyramid TIFF)
要彻底消除首次预览时的 SSD 写入 IO 瓶颈，并实现毫秒级的切片响应，推荐将源图片转换为**金字塔 TIFF（Pyramid TIFF，也称分块式多级分辨率 TIFF）**格式。

- **工作原理**：金字塔 TIFF 内部已经将图像分块（Tiled，例如 $256 \times 256$ 大小），并预先保存了多级分辨率的图像（金字塔层级）。`libvips` 打开此类文件时，可以直接通过文件指针以零拷贝方式随机定位并读取所需的分块，**在内存中流式处理并输出为 `.jpg`/`.png` 返回，全程 0 临时磁盘写入**。
- **零代码改动**：由于 `pap4j-common-vips` 的接口及 OpenSeadragon 的 IIIF 协议是完全格式自适应的，您不需要修改任何 Java 或 HTML 代码，仅需输入金字塔 TIFF 的文件名（如 `input_test.tif`）即可完美运行。

### 3. 生成金字塔 TIFF 转换命令
在 `D:/knowledge/` 下通过 `libvips` 提供的命令行工具（或者集成的转换工具）执行以下转换：
```bash
# 将 1.8GB 的大图转换为 Pyramid TIFF 格式
vips tiffsave D:/knowledge/qmsht.tif D:/knowledge/qmsht_pyramid.tif --tile --pyramid --tile-width 256 --tile-height 256
```
- `--tile`：开启分块化存储（支持随机快速定位）。
- `--pyramid`：开启多级分辨率金字塔。
- `--tile-width` 和 `--tile-height`：分块瓦片宽高设为 $256 \times 256$。

> [!NOTE]
> 转换后的金字塔 TIFF 包含了各个分辨率级别的图层，文件大小相比普通压缩格式可能会有轻微增长（例如从 1.8GB 变为 2.4GB），但这能极大提升大图并发切片加载性能，并使磁盘物理写入 IO 彻底归零。

---

## 🛡️ 生产架构方案：解决高并发下的图片更新、锁冲突与画面撕裂问题

在真实的生产环境下，如果采用“直接同名文件覆盖 + 后端 304 缓存校验”的思路，会面临两个重大底层漏洞：
1. **Windows 平台的文件锁冲突**：若 `libvips` 并发读取某张图片，该文件在内核中会被锁定。此时如果 Java 尝试直接覆盖或通过 `Files.move` 原子移动覆盖，会直接抛出 `AccessDeniedException` 导致覆盖失败。
2. **多瓦片并发请求的画面撕裂**：高并发下同名覆盖图片的瞬间，当前在线用户继续请求新区域的瓦片，由于旧文件已被替换，后端只能切割新文件并返回。这会导致用户端呈现出“一部分老图，一部分新图”的打补丁式撕裂画面（甚至由于分辨率不一致发生变形与报错）。

要彻底解决上述生产漏洞，推荐采用 **“物理文件版本化 + 优雅下线清理”** 的闭环技术方案：

### 1. 核心架构设计

#### 1) 物理存储层：版本化命名与原子写入（规避写锁冲突）
- **规则**：更新图片时，不要同名覆盖，而是为文件名加上唯一的版本后缀（例如时间戳或 MD5 哈希），如 `input_test_1719918240.png`。
- **流程**：将上传的数据先写入临时文件，写入完成后原子重命名。由于生成的是新文件，**彻底避开了旧文件的文件锁**，不影响 ongoing 用户的读操作。
- **示例**: FileOperUtils.java

#### 2) 路由层：图片 ID 绑定版本（隔离运行期版本）
- **路由形式**：IIIF 服务的图片标识符与文件名完全绑定，例如 `/iiif/2/input_test_1719918240.png/info.json`。
- **流程**：
  - **当前在线用户**：前端 Viewer 绑定的 ID 是 `input_test_1719918239.png`，它所发起的并发瓦片请求依然全部指向旧版本文件，**画面绝不发生撕裂**。
  - **新进入用户**：通过数据库或 API 路由层获取最新的图片物理 ID `input_test_1719918240.png`，加载完整的新版瓦片。

#### 3) 缓存分发层：静态强缓存（性能最大化）
- **规则**：因为文件名和文件内容是绝对绑定且不可变的，响应中可以直接下发永久强缓存：
  ```http
  Cache-Control: public, max-age=31536000, immutable
  ```
- **效果**：浏览器和 CDN 会永久缓存当前版本的瓦片，**完全免去了每次请求都要打回 Java 后端做 304 时间戳探测的开销**，吞吐量获得指数级提升。

#### 4) 资源回收层：后台优雅下线清理机制
- **规则**：为了防止老版本图片无限堆积占用磁盘空间，可以设计一个异步定时任务：
  - 每天凌晨扫描图片存放目录。
  - 对于已经产生新版本、且最后修改时间超过指定阈值（例如超过 24 小时，已无存量用户浏览旧版本）的过往历史版本图片，物理执行删除，实现平滑的空间回收。
  - 示例： FileOperUtils.java
---

## 🔒 生产安全方案：最大可看分辨率限制（前端平滑限制 + 后端安全熔断）

在针对高分辨率文献或大图提供预览时，为了防止整图的高分辨率原图被恶意爬虫/爬图软件拉取，或为了对普通用户与 VIP 用户实现分辨率限度分级，必须在技术上实现对最大可看分辨率的控制：

### 1. 后端安全熔断（拦截超限请求）
在 VipsImageProcessor.java 进行缩放转换时，底层最终会根据前端请求的 `size` 计算出横向缩放比例 `hScale`（如 `1.0` 代表 100% 原图分辨率，`0.25` 代表原图的 25% 比例）。
- **拦截逻辑**：在接口控制器层的 `getIiifImage` 入口处获取当前用户的授权级别（如 Guest 用户允许最高缩放率 `maxAllowedScale = 0.25`，VIP 允许 `1.0`）。
- **拦截操作**：解析得到 `hScale` 后进行比对，若请求的比例大于 `maxAllowedScale`，直接返回 `403 Forbidden`，强行熔断数据流。

### 2. 前端平滑限制（提升用户体验）
如果仅做后端拦截，前端 OpenSeadragon 等预览器在放大到高清瓦片层级时会因为瓦片加载失败返回 403 导致出现大片红叉破损图，用户体验较差。
- **限制逻辑**：在元数据接口 `info.json` 响应中，根据当前用户权限动态下发 `scaleFactors`（金字塔缩放因子，`1` 代表 100%，`2` 代表 50%，`4` 代表 25%）。
- **限制操作**：若为普通用户，则过滤掉 `1` 和 `2`，仅下发 `List.of(4, 8, 16, 32)`。
- **效果**：OpenSeadragon 会自动解析并根据这些因子限制视口的滚轮放大极限。普通用户放大到 25% 比例即无法继续放大，前端过渡自然无死锁报错，实现完全平滑的安全限制。

