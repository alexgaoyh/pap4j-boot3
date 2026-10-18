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

另可用 `pap.mupdf.dir`（或环境变量 `PAP_MUPDF_DIR`）指定外部原生库目录。

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
