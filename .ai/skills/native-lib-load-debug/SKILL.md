---
name: native-lib-load-debug
description: [诊断/环境] JVM 下 native DLL 加载失败排查 SOP（UnsatisfiedLinkError / DLL 初始化例程失败）。覆盖 onnxruntime / OpenCV / Tesseract / DJL 等 JNI 库在特定 JDK 下无法加载的根因（JVM 自带 MSVC CRT 过旧）与七步诊断阶梯。
globs: "*.java"
---

# 🧩 native-lib-load-debug 技能 SOP (JVM native DLL 加载失败诊断)

## 📌 触发机制

- **🤖 自动触发**：出现以下任一签名时：
  - `java.lang.UnsatisfiedLinkError: ...xxx.dll: 动态链接库(DLL)初始化例程失败`（Windows `ERROR_DLL_INIT_FAILED`）
  - `java.lang.UnsatisfiedLinkError: ...xxx.dll: 找不到指定的模块`（`ERROR_MOD_NOT_FOUND`）
  - `ExceptionInInitializerError` / `NoClassDefFoundError`（原生库加载失败被类初始化包装）
  - 关键词：`native` / `DLL` / `JNI` 加载失败、`OrtEnvironment.getEnvironment()`、`System.loadLibrary`、
    `onnxruntime` / `OpenCV` / `Tesseract` / `DJL` 报错。
- **💬 显式触发**：`native-lib-load-debug`、`native 库加载失败`、`DLL 初始化失败`。

---

## 🛠️ 执行流程 (七步诊断阶梯)

1. **确认环境差异（最重要）**：同一代码 Maven 过 / IDEA 挂（或反之）→ 对比**实际运行的 JDK**：
   - Maven 看 `JAVA_HOME`；IDEA 看 Project SDK 与 Run 配置的 JRE（项目 SDK 常是 JBR，与 JAVA_HOME 不一致）。
   - 打印 `java.home` / `java.vendor` / `java.version` 定位真实 JVM。
2. **IDE 等价方式复现**：不经 mvn，用 JUnit Console Launcher 直跑（=IDEA 测试运行器同机制），在候选 JDK 间 A/B：
   `java -cp <模块classpath> org.junit.platform.console.ConsoleLauncher --select-class <Test>`。
3. **读 native DLL 导入表**：解析 `.dll` 的 PE 导入表（`objdump -p` / Python onnx / 自写 Java PE parser），
   列出依赖 DLL，重点看 `MSVCP140.dll` / `MSVCP140_1.dll` / `VCRUNTIME140.dll` / `VCRUNTIME140_1.dll`（MSVC C++ 运行库）。
4. **对比 CRT 版本**：列各 JDK `bin/` 下的 `msvcp140*.dll` / `vcruntime140*.dll`，与 native DLL 构建年代比对（如 2023 vs 2025）。
5. **版本矩阵实验**：换 native 库旧版在目标 JDK 下试 → 判断「降级依赖」是否可行。
6. **staging 实验（证伪不可绕过）**：把新版 CRT 与 DLL staging 同目录 + 设置加载路径（onnxruntime 为
   `onnxruntime.native.path` 目录属性）强制加载。若仍失败 → 由下方「核心机制」解释 → 结论：必须换 JDK。
7. **工程化兜底**：代码包一层「加载失败 → 输出 JVM 版本 + 根因 + 修复方案的诊断」，并把结论沉淀到类 Javadoc / 本技能，避免下次裸报错。

## 🔑 核心机制（决定结论走向）

- `java.exe` / `jvm.dll` 启动时即从 JDK bin 加载 `vcruntime140*.dll` → **进程内锁定该版本**，native DLL 必然绑定它。
- Windows 对依赖 DLL 的搜索顺序：**应用目录(JRE bin) → System32 → … → PATH**，JRE bin 自带 CRT **永远优先命中**。
- **已加载的 DLL 不会二次加载** → 无法用 staging 新版 CRT 覆盖 JVM 自带旧 CRT。
- 因此 `UnsatisfiedLinkError: DLL 初始化例程失败` 第一嫌疑 = **JVM 自带 MSVC CRT 过旧**，优先换 JDK / 升级 JBR，
  不要浪费时间去改依赖版本或尝试 staging。

## ⚡ 命令行与验证规范（Git Bash / PowerShell 双环境兼容）

- 复现（Git Bash）：
  ```bash
  "<JDK_17_HOME>/bin/java.exe" -cp "<模块classpath>" \
    org.junit.platform.console.ConsoleLauncher --select-class <Test> --disable-banner
  ```
- 复现（PowerShell）：
  ```powershell
  & "<JDK_17_HOME>\bin\java.exe" -cp "<模块classpath>" `
    org.junit.platform.console.ConsoleLauncher --select-class <Test> --disable-banner
  ```
- ✅ 正确：诊断先打印 `java.home`/`java.vendor`；用 `onnxruntime.native.path`（Java 侧系统属性）控制加载。
- ⚠️ 反模式：把 Rust ort 的 `ORT_DYLIB_PATH` 环境变量当 Java 侧配置（**二者无关**，Java 版不读它）。

## 📋 具体案例与实测经验

**案例签名**
- `pap4j-boot3-example-spring-ai` 模块 `LayoutAnalysisOnnxTest`（onnxruntime 本地版式分析）：
  Maven 下通过，IDEA 下 `OrtEnvironment.getEnvironment()` 抛
  `java.lang.UnsatisfiedLinkError: ...onnxruntime.dll: 动态链接库(DLL)初始化例程失败`。

**根因（两套 JVM 差异）**
- Maven 走环境变量 `JAVA_HOME`（如标准 Adoptium JDK 17+，CRT 兼容）✅；
- IDEA 项目 SDK 若设为旧版 JBR（如 `jbr-17`，JetBrains Runtime 2023-04 构建 → CRT 过旧）❌；
- PE 导入表确认 onnxruntime.dll 依赖 `MSVCP140 / MSVCP140_1 / VCRUNTIME140 / VCRUNTIME140_1` + UCRT。

**版本矩阵实测（排除「降级依赖」方案）**
- JBR 17.0.7 下 onnxruntime 1.19.2 / 1.20.0 / 1.22.0 **全部失败**；openjdk-21.0.2（2024-01）也失败；
  标准 JDK 17+（如 Adoptium 17+）通过 → 旧版 onnxruntime 并非 CRT 兼容的出路。

**staging 实验结论（证明纯 Java 不可绕过）**
- 把新版 CRT + onnxruntime.dll staging 同目录 + 设置 `onnxruntime.native.path` 强制加载 → **仍失败**，
  由「核心机制」两条解释（JRE bin 优先命中 + 已加载 DLL 不二次加载）。

**修复（任选其一）**
1. IDEA：Run ▸ Edit Configurations ▸ Modify options ▸ JRE 选已安装的标准 JDK 17+（只影响该测试），或 Project SDK 切换；
2. 升级 JBR 到 2025 年后的新版（自带新版 CRT）；
3. 命令行：用兼容 JDK + JUnit Console Launcher 直跑。

**环境关键注意事项**
- 校验 `PATH` 中的 `java` 版本与当前项目所要求的 JDK 版本差异；
- Maven 本地仓库位置以 `conf/settings.xml` 配置为准；
- 根 pom git-commit-id 插件校验「工作区无未提交修改」，有改动时命令行执行 mvn 须显式带上 `"-Ddefault.skip=true"`。

## 📎 落地实例

- `pap4j-boot3-example-spring-ai` 模块 `LayoutAnalysisOnnxTest`：`createOrtEnvironment()` 已内置
  「原生库加载失败 → 输出 JVM 版本 + 根因 + 修复方案」诊断；类 Javadoc 含完整模型契约与运行说明。
