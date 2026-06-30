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
        * **文件编码与插件跳过强制**: 运行任何构建、编译或测试验证命令时，应优先调用 `.agent/` 目录下的专属脚本以自动注入参数；若直接使用 maven 命令行，必须同时显式附加 `"-Dfile.encoding=UTF-8"` 以及 `"-Dmaven.gitcommitid.skip=true"` 参数，以防止 Windows 默认的 GBK 环境导致控制台乱码，并严防冗余校验拖慢流程。
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
* **重用公共线程池**: 执行 **纯 CPU 密集型/无阻塞** 的并行分治任务（如内存计算、字节分块解析）时，必须优先重用 `ForkJoinPool.commonPool()`，严禁在方法内高频创建且未 shutdown 的自定义 `new ForkJoinPool()`。**严禁在公共线程池（含并行流）中执行任何阻塞型 I/O 操作**（如网络请求、数据库查询、阻塞写锁），凡涉及 I/O 阻塞的并发任务，必须使用独立配置的 `ThreadPoolExecutor`。

## 4. 异常处理与安全
* **禁止生吞异常**: 严禁 catch 块为空或仅使用 `e.printStackTrace()`。
* **状态安全**: 使用 `volatile` 保证可见性。在可能的情况下，优先使用原子 API（`AtomicReference`, `compute`）而非手动加锁。
* **防内存泄漏**: 任何内存缓存、缓冲队列或注册表必须实现主动或自动的清理机制（Caffeine, 定时任务）（如设置 TTL、基于容量淘汰，或在数据被同步完成后从容器中移除）。严禁无限期保存已处理、过期或无效的 Key，以防随业务主键 (如用户 ID、商品 ID 等) 的持续增长而导致容器膨胀引发内存泄漏 (OOM)。
* **动态资源生灭安全**: Map 中动态创销锁、连接等资源时，严禁直接执行 `remove(key)`（防并发竞态击穿），也严禁只增不删（防 OOM）。必须使用原子操作（如 `compute`/`computeIfPresent`）配合引用计数在计数归零时安全移除。
* **BigDecimal**: 使用 `String` 构造函数或 `BigDecimal.valueOf(double)`。严禁使用 `new BigDecimal(double)`。值比对必须使用 `compareTo()`，严禁使用 `equals()`。

## 5. 代码整洁与 OOP
* **依赖注入**: 强制使用构造器注入，严禁字段注入 (`@Autowired`)。
* **单一职责与重构边界**:
    * 全新开发的方法，其行数若超过 50 行必须重构分拆。
    * **【老代码保护】**：对于历史遗留方法或既有代码的微小修改/修复，必须严格遵循"外科手术式修改"原则，**绝对禁止对未涉及的历史老代码进行顺带重构**，以防引入回归风险或污染提交历史。
* **禁止魔法值**: 使用 `Enum` 或 `static final` 常量。
* **Optional**: 仅用于返回值。严禁作为参数或字段。
* **比较规范**: 使用 `Objects.equals(a, b)` 进行空安全比对。
* **格式约束**: 严禁使用通配符导入 (`import java.util.*`)。所有重写方法必须标注 `@Override`。
* **集合**:
    * 指定初始容量（如 `new HashMap<>(16)`）。
    * 返回 `Collections.emptyList()` 而非 `new ArrayList<>()`。
    * 包装类 (Long/Integer) 的等值判断必须使用 `.equals()`。
* **海量数据高频解析防抖**: 在海量文本、大文件或字节流的高频循环解析场景中，严禁在循环内部重复创建 `ByteArrayOutputStream` 等缓冲流或调用 `toByteArray()` 产生海量临时数组。应优先使用字节指针偏移量（Slice Reference）在主字节数组上进行逻辑切片，直接传递 `(byte[] bytes, int start, int end)` 等区间参数，将垃圾对象产生和 GC 暂停时间降至最低。
* **Swagger 注解规范**: 若模块含 `springdoc-openapi` 依赖，新增的 Controller 接口及参数必须标注 Swagger 注解（如 `@Tag`、`@Operation`、`@Parameter`），便于 AI 更好理解项目契约。

## 6. 日志规范 (SLF4J)
* **禁止标准输出**: 使用 `org.slf4j.Logger`。严禁使用 `System.out` 或 `System.err`。
* **占位符**: 使用 `log.info("msg: {}", arg)`。禁止字符串拼接。
* **异常日志**: 异常对象 `e` 必须作为最后一个参数传递：`log.error("Failed: ", e)`。
* ⚠️ **严禁吞噬异常堆栈 (Swallowing Exception Stack Trace)**:
    * ❌ 错误: `log.error("Failed: {}", e.getMessage());` 或 `log.error("Failed: " + e.getMessage());`（只会打印错误消息，生产排查极难）。
    * ✅ 正确: `log.error("Failed to execute task: ", e);` 或者 `log.error("Failed to execute task: {}", someParam, e);`（将异常实例本身作为最后一个参数传入，SLF4J 会自动输出完整堆栈）。
* **无行号溯源约束**: 生产环境关闭堆栈收集时（行号输出为 `?`），日志必须具备唯一识别性：
    * **文本去雷同**: 严禁在同类中编写完全一致的静态文本（如多处 `log.info("success")`）。
    * **带唯一标识**: 必须使用特定场景/动作前缀（如 `[Order-Save]`）并携带业务主键（订单号、TraceId 等），确保通过全局搜索日志文本即可精准溯源到代码中的唯一行。

## 7. 持久层规范（JPA/MyBatis 强约束）

> 🚨 **本节规则适用于所有使用 JPA/Hibernate 或 MyBatis 的模块，违反即触发 `[Plan]` 强阻断。**

### 7.1 关联注解禁令（硬性禁止）
*   **严禁使用 JPA 关联映射注解**：禁止在任何 Entity 类中使用以下注解：
    *   ❌ `@OneToMany` / `@ManyToOne` / `@ManyToMany` / `@OneToOne`（含任何 `fetch = FetchType.*` 变体）
    *   ❌ `@JoinColumn` / `@JoinTable`（用于关联映射时）
*   **原因**：上述注解会隐式触发 N+1 查询、笛卡尔积爆炸和不可控的懒加载异常（`LazyInitializationException`），且在多模块复杂场景下极难调试和优化。
*   **替代方案**：所有跨表、跨实体的关联数据获取，**必须在业务服务层（Service）中显式完成**：
    *   通过独立的 Repository/Mapper 分别查询，再在 Service 中手动组装 DTO（结果为 DTO/VO/record，禁止返回 `Map<String, Object>`）。
    *   同一组装逻辑 3 处以上复用时，才允许提取为 `XxxAssembler` 静态工具类；否则写在 Service 方法内部。
    *   复杂聚合查询使用原生 SQL（`@Query(nativeQuery=true)` 或 MyBatis XML）并直接映射为扁平 DTO，禁止直接返回 Entity。

### 7.2 事务规范
*   **事务边界在 Service 层且严禁声明在类级别**：`@Transactional` 只允许标注在 Service 层的方法上，严禁标注在类级别，亦严禁标注在 Repository / Controller 上。
*   **写操作精准声明**：仅在有增删改的方法上单独添加 `@Transactional(rollbackFor = Exception.class)`。
*   **读操作按需精细化声明（防连接池枯竭）**：
    *   常规 MyBatis 单表查询方法保持不加任何注解（“裸奔”），减少 AOP 代理开销。
    *   仅在涉及**多表对账（读一致性）**、**JPA 脏检查优化**、**读写分离路由**这三种特定场景时，才精准在方法上添加 `@Transactional(readOnly = true)`。
    *   严禁在包含远程 RPC/HTTP 接口调用或高耗时非 DB 计算的方法上标注 `@Transactional`，避免长事务导致数据库连接池（如 HikariCP）迅速枯竭。
*   **禁止默认传播行为被滥用**：明确声明传播行为（如 `@Transactional(propagation = Propagation.REQUIRES_NEW)`），不允许在长事务中嵌套大量不必要的子查询。

### 7.3 查询规范
*   **禁止 `SELECT *`**：所有查询必须显式指定字段列表，禁止使用 `SELECT *`（无论 JPQL 还是原生 SQL）。
*   **分页强制**：返回列表的查询接口，**必须**使用分页（`Pageable` 或 MyBatis `PageHelper`），严禁返回全表数据。
*   **N+1 防御**：在循环中严禁调用数据库查询（即 `for` 循环内部不允许出现 Repository/Mapper 调用），应将循环改为批量 IN 查询（`WHERE id IN (...)`）。
*   **禁止在 Entity 上直接暴露给接口层**：Controller 层严禁直接接收或返回 Entity 对象，必须通过 DTO/VO 转换。


## 8. 典型反面教材 (Anti-Patterns)
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
*   **错误日志 (无行号歧义)**
    *   ❌ 错误: 多处编写无差别日志 `log.info("done");`
    *   ✅ 正确: 携带场景与业务主键 `log.info("[Import-Done] Task complete, batchNo: {}", batchNo);`
*   **错误判断 (对象比对)**
    *   ❌ 错误: `if (userId == 1000L) { ... }` (Long 超过 127 的缓存池)
    *   ✅ 正确: `if (Long.valueOf(1000L).equals(userId)) { ... }`
*   **错误金额 (精度丢失)**
    *   ❌ 错误: `new BigDecimal(0.1)`
    *   ✅ 正确: `new BigDecimal("0.1")` 或 `BigDecimal.valueOf(0.1)`
*   **错误并发与内存泄漏 (ForkJoinPool 滥用与重建)**
    *   ❌ 错误: 在 CPU 密集计算方法中写 `ForkJoinPool pool = new ForkJoinPool(); pool.invoke(task);`（未 shutdown 导致僵尸线程泄露）；或者在 `ForkJoinPool.commonPool()` / 并行流中执行数据库查询或 HTTP 请求（导致全局公共池阻塞挂起）。
    *   ✅ 正确: 纯 CPU 计算任务使用 `ForkJoinPool.commonPool().invoke(task)`；含有阻塞型 I/O 的任务使用自定义的、配置了有界队列和饱和策略的 `ThreadPoolExecutor`。
*   **高频循环产生海量垃圾对象 (GC 压力)**
    *   ❌ 错误: 在 for/while 循环体内为每一行数据分配 `new ByteArrayOutputStream()`，并频繁调用 `toByteArray()` 拷贝小字节数组。
*   **内存累加缓冲与内存泄露 (容器键持续积压)**
    *   ❌ 错误: 仅对 Map/Set 中特定 Key 对应的计数值/状态进行置空或重置，但从未从容器中将 Key 本身移除，导致容器大小随业务 ID 的不断累积而无限增长。
    *   ✅ 正确: 在确认数据已同步落库、计数值归零或状态失效后，通过线程安全的方式（如 Map 的 `computeIfPresent` 移除机制）将 Key 彻底从容器中移除，或使用带有淘汰策略的本地缓存库（如 Caffeine）来管理容器的生命周期。


## 9. 代码审计强制清单 (Mandatory Audit Checklist)
在进行代码审查或执行 **`[Edit]`** 前，AI 必须对照以下清单进行深度"自检"，并在回复中列出：
1. **并发安全**: 是否存在 `new Thread()` 或未指定容量的无界 `LinkedBlockingQueue`？
2. **日志规范**: 日志打印是否使用了 `+` 拼接字符串而非 SLF4J 占位符？
3. **方法边界**: 单个方法逻辑是否超过 50 行？
4. **精度安全**: 是否存在 `new BigDecimal(double)`？
5. **生命周期**: 是否硬编码了 `addShutdownHook`？
6. **持久层关联**: 是否存在 `@OneToMany` / `@ManyToOne` / `@ManyToMany` 等关联注解？
7. **持久层 N+1**: 是否存在在循环内部调用 Repository/Mapper 的情况？
8. **持久层事务**: `@Transactional` 是否仅标注在 Service 方法级别（而非类级别或 Repository/Controller 上）？写操作是否添加了 `rollbackFor = Exception.class`？纯查询方法是否避免了无意义的注解（仅在对账、JPA优化或读写分离时使用 `readOnly = true`）？
9. **pap4j-common 公共 API 边界**: 本次修改是否涉及 `pap4j-common` 任意子模块下 `src/main/java` 中的 `public` 方法或 `public` 接口？若是，**无论改动大小，必须触发 `[Plan]` 强阻断**，不得跳过，因为这些方法是跨模块公共契约。
10. **Swagger 注解**: 模块含 `springdoc-openapi` 时，新增接口/参数是否已补齐 Swagger 注解？

## 10. AI 自我纠错指导原则 (AI Self-Correction Principles)
* **本地单元测试执行规范**:
  * 运行单元测试时，为避免复杂的命令行转义错误以及自动激活 `agent` 调试 Profile，**优先**使用 `.agent/` 目录下的专属脚本。若因特殊情况无法使用脚本，则退而使用原生 Maven 命令行（必须附带 `"-Dfile.encoding=UTF-8"` 与 `"-Dmaven.gitcommitid.skip=true"`）：
    * **Windows (PowerShell)**: 优先使用 `.\.agent\agent-test.cmd -pl <module-name> test`（例如：`.\.agent\agent-test.cmd -pl pap4j-boot3-example/pap4j-boot3-example-dynamic-form test`）。
* **低噪音精准定位报错**:
  * 若测试执行失败，AI **必须优先且直接读取**项目根目录下的 `.ai/diagnostics/test_failures.md` 文件。该文件包含了剔除杂音后的精炼失败堆栈信息，禁止盲目在庞杂的 Maven 控制台日志中遍历检索，以节约上下文窗口与 Token 消耗。


