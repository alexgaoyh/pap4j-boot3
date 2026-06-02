# 项目守卫：技术标准与约束 (Project Guard)

本文件定义了 `pap4j-boot3` 项目的严格技术边界和安全规则。AI 代理必须遵守这些规则，以确保系统的稳定性和安全性。

## 1. 核心技术栈
* **运行环境**: Java 17+ & Spring Boot 3.x (Jakarta EE)。
* **构建工具**: Maven。始终使用 `-Dfile.encoding=UTF-8` 参数，并使用双引号包裹 Maven 命令以防止截断。
* **命名空间**: 仅使用 `jakarta.*`。严禁使用 `javax.*`。

## 2. Java 17 现代模式
* **文本块 (Text Blocks)**: 对于多行字符串（SQL、JSON），使用 `"""`。
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
* **BigDecimal**: 使用 `String` 构造函数或 `BigDecimal.valueOf(double)`。严禁使用 `new BigDecimal(double)`。

## 5. 代码整洁与 OOP
* **构造器注入**: 优于字段注入 (`@Autowired`)。
* **单一职责**: 方法超过 50 行应进行重构。
* **禁止魔法值**: 使用 `Enum` 或 `static final` 常量。
* **Optional**: 仅用于返回值。严禁作为参数或字段。
* **集合**:
    * 指定初始容量（如 `new HashMap<>(16)`）。
    * 返回 `Collections.emptyList()` 而非 `new ArrayList<>()`。
    * 包装类 (Long/Integer) 的等值判断必须使用 `.equals()`。

## 6. 日志规范 (SLF4J)
* **禁止标准输出**: 使用 `org.slf4j.Logger`。
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
