# Gemini CLI Project Mandates: pap4j-boot3

欢迎来到 pap4j-boot3 项目。在协助开发、重构或排查问题时，请务必严格遵守以下核心指令和项目规范。

## 1. 技术栈边界 (Tech Stack)
*   **核心框架**: Java 17+ 和 Spring Boot 3.x (Jakarta EE 规范)。
*   **构建工具**: Maven。执行构建或测试时，请直接使用系统全局环境变量中配置的 `mvn` 命令。
*   **模块架构**: 本项目是一个多模块架构。
    *   `pap4j-boot3-example`: 存放各类集成示例和测试业务。
    *   `pap4j-boot3-starters`: 存放自定义的 Spring Boot Starter 组件。
    *   `pap4j-common`: 存放基础工具类和通用数据结构库（不可随意引入强业务依赖）。

## 2. 编码与架构规范 (Coding & Architecture Standards)
*   **禁止私自造轮子**: 在引入新的工具类或并发结构前，必须先搜索 `pap4j-common`（例如 `pap4j-common-datastructure`）下是否已有现成的实现（如 `IdempotentTaskLock` 等）。优先复用 Apache Commons、Guava 等成熟基础库（前提是项目中已引入）。

### 2.1 现代 Java 特性 (JDK 17 适配)
*   **文本块 (Text Blocks)**: 对于多行字符串（如 SQL、JSON、HTML），必须使用 `"""` 文本块，严禁使用冗长的 `+` 拼接。
*   **记录类 (Records)**: 对于纯粹的数据载体（DTO、VO），优先使用 `record` 关键字替代传统的带有 Getter/Setter 的 `class`，以保证数据的不可变性和语法的简洁性。
*   **模式匹配 (Pattern Matching)**: 优先使用 `instanceof` 的模式匹配（如 `if (obj instanceof String s) { s.length(); }`），避免强制类型转换；使用增强的 `switch` 表达式（带 `->` 和 `yield`），避免 `break` 遗漏。
*   **密封类 (Sealed Classes)**: 对于领域驱动设计（DDD）或状态机中有限的子类继承结构，优先使用 `sealed class/interface` 来限制继承树。

### 2.2 Spring Boot 3.x & Jakarta EE 规范
*   **命名空间迁移**: 严格使用 `jakarta.*` 包名（如 `jakarta.servlet.*`, `jakarta.persistence.*`, `jakarta.validation.*`），绝对禁止使用过时的 `javax.*`。
*   **依赖注入**: 推荐使用构造器注入（Constructor Injection）。严禁在业务代码中使用字段注入（`@Autowired` 放在成员变量上），以保证 Bean 的可测试性和不变性。
*   **响应式与虚拟线程 (前瞻)**: 虽然是 Spring Boot 3，但若未使用 WebFlux，仍按传统 Servlet 模型开发。若涉及 JDK 21 升级，需注意传统 `synchronized` 与虚拟线程（Virtual Threads）的固定（Pinning）问题，此时应强制使用 `ReentrantLock`。

### 2.3 并发与多线程 (阿里规范级别)
*   **禁止显式创建线程**: 严禁在业务代码中直接 `new Thread()`。必须使用线程池。
*   **严禁使用 Executors 默认工厂**: 不允许使用 `Executors.newFixedThreadPool()` 等方法创建线程池，因为它们底层使用的是无界队列 `LinkedBlockingQueue`（容易导致 OOM）。必须显式使用 `ThreadPoolExecutor` 的构造函数，并指定有界队列大小及合理的拒绝策略（如 `CallerRunsPolicy` 或自定义拒绝处理）。
*   **必须自定义线程池命名**: 必须通过自定义 `ThreadFactory` 并结合 `AtomicInteger` 为线程池内的线程命名（如 `xxx-worker-%d`），以便于线上排查问题和打印日志。
*   **Spring 并发管理**: 在 Spring 环境中，优先使用 `@Async`，或者将自定义的 `ThreadPoolExecutor` 注册为 Spring Bean。如果是 Bean，必须配置 `destroyMethod = "shutdown"` 或在 `@PreDestroy` 中执行优雅停机。
*   **ThreadLocal 规范**: 使用 `ThreadLocal` 时，必须在 `finally` 块中调用 `remove()` 方法，防止内存泄漏和线程复用导致的上下文污染。

### 2.4 异常处理与状态安全
*   **中断处理**: 捕获 `InterruptedException` 时，必须显式调用 `Thread.currentThread().interrupt()` 恢复中断状态，以保证系统的优雅停机能力。
*   **禁止生吞异常**: catch 块中必须有实质性的处理或日志打印，严禁仅使用 `e.printStackTrace()`。严禁捕获 `Exception` 后不做任何处理。
*   **状态可见性与原子性**: 多线程共享的可变状态变量必须使用 `volatile` 保证可见性。处理复合操作时，必须考虑死锁避免及原子性（优先使用 `ConcurrentHashMap.compute/putIfAbsent` 或 `AtomicReference` 等无锁/原子 API）。
*   **内存泄漏防御**: 使用基于内存的集合（如 Map/List）做缓存或状态追踪时，**必须**设计清理机制（如结合 ScheduledExecutorService 的定时清理，或基于容量/时间的淘汰策略如 Caffeine），严防 OOM。

### 2.5 日志规范 (Google/阿里级别)
*   **统一门面**: 统一使用 `org.slf4j.Logger` (SLF4J) 接口。
*   **禁止标准输出**: 严禁在提交的代码中遗留 `System.out.println` 或 `System.err.println`。
*   **性能优先**: 日志拼接必须使用占位符 `{}`，而非字符串加号 `+`（避免不必要的对象创建和字符串拼接开销）。如果日志级别涉及复杂的计算，必须使用条件判断（如 `if (log.isDebugEnabled())`）。
*   **异常日志**: 记录异常时，`e` 必须作为日志方法的最后一个独立参数传入，而不是与字符串拼接（如 `log.error("Failed: ", e)`，而不是 `log.error("Failed: " + e)`）。

### 2.6 集合与数据结构
*   **初始化容量**: 初始化集合时，尽量指定初始容量（如 `new ArrayList<>(16)`，`new HashMap<>(16)`）。
*   **空集合返回**: 返回空集合时，优先使用 `Collections.emptyList()`，而非 `new ArrayList<>()`。
*   **禁止修改 SubList**: 由 `subList` 返回的集合不可强转为 `ArrayList`，且对其元素的修改会影响原列表；使用 `Arrays.asList()` 转换的列表不支持 `add/remove` 操作。
*   **等值判断**: 包装类（如 `Integer`, `Long`）的等值判断必须使用 `equals()`，绝对禁止使用 `==`（防 -128 到 127 缓存池外的对象地址比对错误）。

### 2.7 代码整洁度与 OOP (Clean Code)
*   **方法职责单一**: 一个方法只做一件事，如果方法体超过 50 行，请考虑抽取私有方法。参数列表不应超过 5 个。
*   **魔法值处理**: 代码中严禁出现未定义的“魔法值”（如直接写死的状态码 `1`, `"SUCCESS"` 等），必须提取为 `Enum` 或 `static final` 常量。
*   **Optional 防 NPE**: 对于可能为 null 的返回值，推荐使用 `Optional` 包装。严禁将 `Optional` 用作方法的参数或类的字段。
*   **BigDecimal 精度**: 涉及金额或高精度计算，必须使用 `BigDecimal`，且其构造必须使用 `String` 类型的构造函数（`new BigDecimal("0.1")`）或 `BigDecimal.valueOf(double)`，严禁使用 `new BigDecimal(0.1)` 导致精度丢失。

## 3. 修改与重构原则 (Modification Principles)
*   **最小侵入性**: 修改代码时采用外科手术式的精准修改（Surgical edits），不要为了“清理代码”而修改与当前任务无关的文件，除非用户明确要求。
*   **保证编译通过**: 每次完成代码修改后，如果涉及公共模块，应建议或主动运行相关的单元测试（如 `.\mvnw.cmd clean test -pl <模块名> -Dtest=<测试类>`）以确保没有破坏现有逻辑。
*   **注释与文档**: 如果修改了核心类或复杂的并发逻辑，必须更新或补充详尽的 Javadoc，注明设计意图、局限性及关键的防御性设计（如防脏读、防 OOM 策略）。

## 4. Git 交互行为
*   **禁止自动提交**: 无论你帮我写了多好的代码，都**绝对不要**自动执行 `git add` 或 `git commit`。所有的提交动作必须由我亲自确认和执行。
*   如果我要求你“生成提交信息”，请先运行 `git status` 和 `git diff` 了解上下文，并参考 `git log` 模仿项目中已有的 Commit Message 风格（例如：`feat: ...`, `fix: ...`, `refactor: ...`）。