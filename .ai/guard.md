# 项目守卫：技术标准与约束 (Project Guard)

本文件定义了 `pap4j-boot3` 项目的严格技术边界和安全规则。AI 代理必须遵守这些规则，以确保系统的稳定性和安全性。

## 1. 核心技术栈与运行环境
* **宿主环境**: Windows 操作系统 (Windows 10/11 或 Windows Server)。
* **执行终端**: PowerShell。AI 在调用 `[Shell]` 时必须**直接且仅生成**适用于 PowerShell 的命令。
* **运行环境**: Java 17+ & Spring Boot 3.x (Jakarta EE)。
* **构建工具**: Maven。
    * ⚠️ **PowerShell 下的 Maven 执行约束**:
        * 优先使用 `mvn`，若不存在则使用项目自带的包装器 `.\mvnw`。
        * **参数包裹规则**: 在 PowerShell 中传递复杂的 Maven 参数（如测试类名、多模块指定等）时，**必须使用双引号 `"` 包裹参数**（例如 `"-Dtest=..."`），严禁使用单引号，防止参数被 PowerShell 引擎解析截断。
        * **文件编码与插件跳过强制**: 运行任何构建、编译或测试验证命令时，**必须**同时显式附加 `"-Dfile.encoding=UTF-8"` 以及 `"-Dmaven.gitcommitid.skip=true"` 参数，以防止 Windows 默认的 GBK 环境导致控制台乱码，并严防因为 `git-commit-id-maven-plugin` 插件的重复扫描拖慢自动化验证流程。
* **命名空间**: 仅使用 `jakarta.*`。严禁使用 `javax.*`。

## 2. Java 17 现代模式
* **文本块 (Text Blocks)**: 对于多行字符串（SQL、JSON），必须使用 `"""`。
* **记录类 (Records)**: 对于 DTO/VO（不可变性），优先使用 `record`。
* **模式匹配**: 使用 `instanceof` 模式匹配和 `switch` 表达式 (`->`)。
* **密封类 (Sealed Classes)**: 在 DDD 或状态机中，使用 `sealed` 限制继承。

## 3. 并发与线程规范（严格执行）
* **禁止显式线程**: 严禁使用 `new Thread()`。
* **禁止默认执行器**: 严禁使用 `Executors.newFixedThreadPool()` 或其他产生无界队列的工厂方法。
* **显式 ThreadPoolExecutor**: 必须使用显式构造函数，配合有界队列（如指定容量的 `LinkedBlockingQueue`）和明确的拒绝策略 (`RejectedExecutionHandler`)。
* **命名规范**: 必须使用 `ThreadFactory` 为线程命名（如 `task-worker-%d`）。
* **ThreadLocal**: 必须在 `finally` 块中调用 `.remove()`。
* **中断处理**: 捕获 `InterruptedException` 后，必须通过 `Thread.currentThread().interrupt()` 恢复中断状态。

## 4. 异常处理与安全
* **禁止生吞异常**: 严禁 catch 块为空或仅使用 `e.printStackTrace()`。
* **状态安全**: 使用 `volatile` 保证可见性。在可能的情况下，优先使用原子 API（`AtomicReference`, `compute`）而非手动加锁。
* **防内存泄漏**: 任何内存缓存/注册表必须有清理机制（Caffeine, 定时任务）。
* **BigDecimal**: 使用 `String` 构造函数或 `BigDecimal.valueOf(double)`。严禁使用 `new BigDecimal(double)`。值比对必须使用 `compareTo()`，严禁使用 `equals()`。

## 5. 代码整洁与 OOP
* **依赖注入**: 强制使用构造器注入，严禁字段注入 (`@Autowired`)。
* **单一职责与重构边界**:
    * 全新开发的方法，其行数若超过 50 行必须重构分拆。
    * **【老代码保护】**：对于历史遗留方法或既有代码的微小修改/修复，必须严格遵循“外科手术式修改”原则，**绝对禁止对未涉及的历史老代码进行顺带重构**，以防引入回归风险或污染提交历史。
* **禁止魔法值**: 使用 `Enum` 或 `static final` 常量。
* **Optional**: 仅用于返回值。严禁作为参数或字段。
* **比较规范**: 使用 `Objects.equals(a, b)` 进行空安全比对。
* **格式约束**: 严禁使用通配符导入 (`import java.util.*`)。所有重写方法必须标注 `@Override`。
* **集合**:
    * 指定初始容量（如 `new HashMap<>(16)`）。
    * 返回 `Collections.emptyList()` 而非 `new ArrayList<>()`。
    * 包装类 (Long/Integer) 的等值判断必须使用 `.equals()`。

## 6. 日志规范 (SLF4J)
* **禁止标准输出**: 使用 `org.slf4j.Logger`。严禁使用 `System.out` 或 `System.err`。
* **占位符**: 使用 `log.info("msg: {}", arg)`。禁止字符串拼接。
* **异常日志**: 异常对象 `e` 必须作为最后一个参数传递：`log.error("Failed: ", e)`。

## 7. 典型反面教材 (Anti-Patterns)
为了确保规则的绝对清晰，以下列出绝对禁止的写法及其对应的正确做法：

*   **错误注入 (依赖注入)**
    *   ❌ 错误: `@Autowired private UserService userService;` 
    *   ✅ 正确: 优先使用构造器注入（Lombok 的 `@RequiredArgsConstructor` 或显式构造函数）。
*   **错误并发 (线程管理)**
    *   ❌ 错误: `new Thread(() -> { ... }).start();` 或 `Executors.newCachedThreadPool();`
    *   ✅ 正确: 使用显式创建的 `ThreadPoolExecutor`，并配置有界队列和拒绝策略。
*   **错误日志 (异常处理)**
    *   ❌ 错误: `log.error("发生错误：" + e.getMessage());`
    *   ✅ 正确: `log.error("发生错误：", e);` （不要拼接字符串，将异常对象独立作为最后的参数传入）。
*   **错误判断 (对象比对)**
    *   ❌ 错误: `if (userId == 1000L) { ... }` (Long 超过 127 的缓存池)
    *   ✅ 正确: `if (Long.valueOf(1000L).equals(userId)) { ... }`
*   **错误金额 (精度丢失)**
    *   ❌ 错误: `new BigDecimal(0.1)`
    *   ✅ 正确: `new BigDecimal("0.1")` 或 `BigDecimal.valueOf(0.1)`
