# MODIFICATIONS

本文件记录本仓库对第三方组件所做的修改，用于满足 GNU Affero General Public
License v3.0 第 5(a) 条「显著标注修改」的要求。

---

## 1. MuPDF 1.28.2 — Java 绑定（`src/main/java/com/artifex/mupdf/fitz/`）

- 上游来源：https://github.com/ArtifexSoftware/mupdf ，tag `1.28.2`
- 上游路径：`platform/java/src/com/artifex/mupdf/fitz/`
- 上游许可：AGPL-3.0（或 Artifex 商业授权）
- 修改者：alexgaoyh <alexgaoyh@sina.com>
- 修改日期：2026-10-18

### 修改清单（仅 1 处）

| 文件 | 位置 | 修改内容 |
| --- | --- | --- |
| `Context.java` | `static { init(); }` / `public static void init()` | 将上游的 `System.loadLibrary("mupdf_java")`（及其 `UnsatisfiedLinkError` 兜底分支）替换为 `cn.net.pap.common.mupdf.loader.MuPdfNativeLoader.ensureLoaded()`，以支持「平台自动探测 + classpath 资源解压 + 外部目录加载 + 多 ClassLoader 复用」 |

### 未修改的保证

除上述 1 处之外，`com/artifex/mupdf/fitz/` 下的 Java 文件与上游 1.28.2 **逐文件
对照一致**（native 方法声明、字段声明均相同）。这一点很重要，因为 `mupdf_java`
的 JNI 层是按「类名 + 字段名 + 方法签名」解析符号的，任何签名改动都会导致
`UnsatisfiedLinkError` 或参数错位。

> 升级约束：**原生库 `mupdf_java.dll` 与这套 Java 绑定必须成对升级**，
> 只替换其中一方会导致链接失败或未定义行为。

---

## 2. 第三方二进制（`src/main/resources/native/`）

| 文件 | 来源 | 说明 | SHA-256 |
| --- | --- | --- | --- |
| `mupdf_java.dll` | 由 MuPDF 1.28.2 源码构建（`Context.getVersion()` 实测 = 1.28.2） | MuPDF JNI 绑定原生库，AGPL-3.0 | `91DD1420F7B9BB2DCB0C8A627FC1E12485BB0EA98FFF6B15E14021C9B590316E` |
| `libwinpthread-1.dll` | MinGW-w64 运行时（未修改） | 遵循其原始许可，见 NOTICE 第五节 | `863893F1CD495212C487F4E1FDE08D5CD1AA9169A23E90036CA8E28C0E35B7EE` |
| `libgcc_s_seh-1.dll` | MinGW-w64 / GCC Runtime Library（未修改） | 受 GCC Runtime Library Exception 覆盖，见 NOTICE 第五节 | `38CF96743993DBF30D4FFA7E7FC6BF5108921977597D3D80C7281E09EC9F8216` |

构建方式见同目录 `README.md`（对应源码：MuPDF 上游 tag 1.28.2 + 本文件记录的 1 处补丁）。

> 可复现性建议：重新构建原生库时，请同时记录工具链版本（`gcc -v` / `make -v`）与本表 SHA-256，
> 以便满足 AGPL-3.0 「Corresponding Source」中对“用于控制编译与安装的脚本”的要求。

---

## 3. 本项目自有代码（`cn/net/pap/common/mupdf/`）

非第三方修改，无需在此登记；但其与上述 AGPL 组件同进程链接，因此按
`NOTICE` / `REUSE.toml` 的声明整体以 AGPL-3.0 授权。
