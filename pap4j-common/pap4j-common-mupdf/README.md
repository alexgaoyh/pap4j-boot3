# pap4j-common-mupdf

MuPDF（JNI）封装：单页文本物理坐标 JSON 提取、PNG / 裸 RGB 点阵 / WebP 渲染，供 PDF 预览类业务使用。

> ## ⚠️ 许可证提示（请先读这一段）
>
> 本模块包含并链接 **MuPDF**（Java 绑定 + 原生二进制），因此：
>
> - **本模块以 GNU AGPL-3.0 授权**（见仓库根目录 `LICENSE-AGPL-3.0.txt`），
>   而不是 Apache-2.0；仓库其余模块仍为 Apache-2.0（见根目录 `NOTICE`）。
> - 若你以 **in-process JNI** 方式链接本模块，你分发（含通过容器/镜像交付）或以
>   网络对外提供服务时，**整体须遵守 AGPL-3.0**，包括第 13 条「通过网络与用户
>   交互时须提供对应源码」的义务。
> - 不愿承担该义务时：不要引入本模块；改用 Apache-2.0 的 PDF 引擎实现；或把
>   MuPDF 放进**独立进程**调用；或向 Artifex 购买商业授权
>   （https://www.artifex.com/licensing/ ）。
>
> 修改记录见 [MODIFICATIONS.md](MODIFICATIONS.md)，SPDX 机器可读声明见根目录 `REUSE.toml`。

---

## 1. 对应源码（Corresponding Source）

AGPL-3.0 要求随二进制提供对应源码，本项目对应关系如下：

| 产物 | 对应源码 | 获取方式 |
| --- | --- | --- |
| `mupdf_java.dll`（MuPDF **1.28.2**） | MuPDF 官方源码 + 本仓库构建参数 | `git clone https://github.com/ArtifexSoftware/mupdf && git checkout 1.28.2` |
| `src/main/java/com/artifex/mupdf/fitz/*` | 上游 `platform/java/src/com/artifex/mupdf/fitz/` + 本项目补丁 | 上游 tag `1.28.2`；补丁见 [MODIFICATIONS.md](MODIFICATIONS.md) |
| `cn/net/pap/common/mupdf/*`（本模块自有代码） | 本仓库 | 本仓库源码 |

### 原生库构建方式

在 Windows（MinGW-w64，x86_64）环境下：

```bash
git clone https://github.com/ArtifexSoftware/mupdf
cd mupdf && git checkout 1.28.2
make -j8 HAVE_X11=no HAVE_GLUT=no HAVE_CURL=no java
# 产物：platform/java/.../mupdf_java.dll（连同 libwinpthread-1.dll、libgcc_s_seh-1.dll）
```

将上述产物放入 `src/main/resources/native/win-amd64/` 即可随 jar 一起分发。
**注意：请保持 Java 绑定与原生库版本一致（同为 1.28.2）。**

---

## 2. 使用方式

```java
try (MuPdfService service = new MuPdfService()) {
    int pages = service.getPageCount("/data/a.pdf");
    String json = service.extractCoordinatesJson("/data/a.pdf", 0);      // 文本物理坐标（PDF Point）
    byte[] png  = service.renderPageToPng("/data/a.pdf", 0, 144);        // PNG
    var raw     = service.renderPageToRawSamples("/data/a.pdf", 0, 144); // 裸 RGB 点阵（拷贝进堆）
    byte[] webp = service.renderPageToWebp("/data/a.pdf", 0, 144, 75);   // WebP（vips 缺失时自动回退 PNG）
}
```

Spring 环境请声明为 Bean 并保证停机时释放原生句柄：

```java
@Bean(destroyMethod = "close")
public MuPdfService muPdfService() {
    return new MuPdfService();
}
```

建议再加一个定时任务周期性调用 `shrinkStore()` 收缩 MuPDF 全局堆外缓存。

### 可调参数（JVM `-D`）

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `pap.mupdf.cache.maxDocs` | `8` | 常驻文档缓存上限；`0` = 关闭缓存（每次即开即关） |
| `pap.mupdf.cache.ttlMillis` | `600000` | 常驻文档空闲过期时间（10 分钟） |
| `pap.mupdf.render.maxPixels` | `100000000` | 单页光栅化像素上限，超出直接报错 |
| `pap.mupdf.raw.maxBytes` | `100663296` | `renderPageToRawSamples` 拷贝进 JVM 堆的字节上限 |
| `pap.mupdf.svg.maxFontBytes` | `33554432` | 单个字体文件字节上限 |
| `pap.mupdf.svg.fontCacheDirs` | `8` | SVG 导出：字体目录缓存条目上限（按目录指纹自动失效） |
| `pap.mupdf.svg.fontCacheMaxBytes` | `134217728` | SVG 导出：单个字体目录允许常驻缓存的字节上限，超出则该目录不缓存 |
| `pap.mupdf.svg.outlineCacheSize` | `200000` | SVG 导出：跨页复用的字形轮廓缓存条目上限 |

另可用 `pap.mupdf.dir`（或环境变量 `PAP_MUPDF_DIR`）指定外部原生库目录。

### 文本坐标 JSON 结构与旋转语义（方案 A 工业级标准）

`extractCoordinatesJson` 的输出在 `mutool draw -F stext` 的基础上补齐了**页面旋转、单字原始旋转与视觉旋转的双轨解耦几何信息**：

```json
{
  "file": "/data/sample.pdf",
  "pages": [
    {
      "number": 1,
      "page_rotation": 90,
      "width": 842.0,
      "height": 595.0,
      "box": {
        "x": 0.0,
        "y": 0.0,
        "w": 842.0,
        "h": 595.0
      },
      "blocks": [
        {
          "type": "text",
          "lines": [
            {
              "wmode": 0,
              "dir": {
                "x": 0.0,
                "y": 1.0
              },
              "rotation": {
                "font_angle": 0.0,
                "visual_angle": 90.0
              },
              "bbox": {
                "x": 496.64,
                "y": 100.0,
                "w": 23.976,
                "h": 180.0,
                "flags": 0
              },
              "quad": {
                "ul": { "x": 520.616, "y": 100.0 },
                "ur": { "x": 520.616, "y": 280.0 },
                "lr": { "x": 496.64, "y": 280.0 },
                "ll": { "x": 496.64, "y": 100.0 }
              },
              "x": 500.0,
              "y": 100.0,
              "text": "页面旋转测试-90",
              "chars": [
                {
                  "c": "页",
                  "origin": {
                    "x": 500.0,
                    "y": 100.0
                  },
                  "bbox": {
                    "x": 496.64,
                    "y": 100.0,
                    "w": 23.976,
                    "h": 24.0
                  },
                  "quad": {
                    "ul": { "x": 520.616, "y": 100.0 },
                    "ur": { "x": 520.616, "y": 124.0 },
                    "lr": { "x": 496.64, "y": 124.0 },
                    "ll": { "x": 496.64, "y": 100.0 }
                  },
                  "rotation": {
                    "font_angle": 0.0,
                    "visual_angle": 90.0
                  },
                  "font": "SimSun",
                  "size": 24.0
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

#### 关键几何字段说明

| 层级 | 字段 | 类型 | 说明 |
| :--- | :--- | :--- | :--- |
| **页面 (Page)** | `page_rotation` | `int` | PDF 页面本身的 `/Rotate` 角度（`0`, `90`, `180`, `270`） |
| | `width` / `height` | `double` | 显示坐标系下的页面有效宽高（已应用页旋转，与渲染底图尺寸完全同源） |
| | `box` | `Bbox` | 显示坐标系下的页面物理边界范围 |
| **行 (Line)** | `dir` | `PointInfo` | 行书写方向单位向量（Y 轴向下显示坐标系，如 `(0, 1)` 表示自上向下） |
| | `quad` | `QuadInfo` | 行级贴合旋转包围四边形（由单字 quad 沿 `dir` 正交投影生成，无多余留白） |
| **字符 (Char)** | `origin` | `PointInfo` | 字符基线绘制物理坐标（显示坐标系） |
| | `bbox` | `Bbox` | 轴对齐外接框（旋转后为外切矩形，保留用于粗筛和向下兼容） |
| | `quad` | `QuadInfo` | 字符精确 4 角点（`ul ➔ ur ➔ lr ➔ ll` 顺时针，`ul ➔ ur` 为字形基线，`ul ➔ ll` 为字高） |
| **旋转 (Rotation)** | `font_angle` | `double` | PDF 内部文本矩阵设定的原始字体旋转角（逆时针，用于逆向回写原始 PDF） |
| | `visual_angle` | `double` | 最终呈现在底图上的视觉旋转角（顺时针，等价 CSS `rotate(deg)`，供前端 0 偏移定位） |

#### 关键消费约定

1. **视觉渲染直接用 `visual_angle` 与 `quad`**：坐标已同源于 MuPDF 渲染点阵（原点左上、Y 向下），前端按 `x / width * 100%`、`y / height * 100%` 定位，**切勿对 `page_rotation` 二次旋转**；
2. **逆向回写直接用 `font_angle` 与 `page_rotation`**：轻松还原 PDF 用户空间文本矩阵，支持跨系统无缝映射；
3. **消除斜排偏移**：对于 45°、90° 等旋转文本，优先使用 `quad` 顺时针多边形角点，彻底消除传统轴对齐 `bbox` 面积虚增与顶点偏移问题。

---

## 3. 平台支持现状

| 平台 | 状态 |
| --- | --- |
| Windows x86_64 | ✅ 已内置 `mupdf_java.dll` |
| Linux x86_64 / arm64 | ⛔ 未内置原生库，需按上文自行构建后放入 `src/main/resources/native/linux-*/` |
| macOS x86_64 / arm64 | ⛔ 同上 |

加载顺序：系统库路径 → classpath `native/{platform}/`（解压到 `~/.pap-mupdf/bin`）→ `PAP_MUPDF_DIR` / `~/.pap-mupdf/bin`。

---

## 4. 资源与并发约定（实现要点）

- 每个常驻文档使用**独占锁**串行化原生调用（MuPDF 明确要求同一 `Document` 同一时刻只能被一个线程访问），不同文档可并行；
- 文档淘汰遵循「缓存表摘除 → 标记 closed → 独占锁内 `fz_drop_document`」协议，杜绝 use-after-free；
- `DocumentWriter` / `NativeDevice` 均显式 `destroy()`，不依赖 GC finalizer；
- `evict()` / `clearCache()` 会等待该文档上在飞的渲染结束；`close()` 后服务拒绝新请求；
- 回归测试见 `src/test/java/cn/net/pap/common/mupdf/MuPdfResourceSafetyTest.java`。
