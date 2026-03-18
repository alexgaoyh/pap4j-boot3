# Gemini CLI Project Mandates: pap4j-boot3

欢迎来到 pap4j-boot3 项目。在协助开发、重构或排查问题时，请务必严格遵守以下核心指令和项目规范。

## 1. 技术栈边界 (Tech Stack)
*   **核心框架**: Java 17+ 和 Spring Boot 3.x (Jakarta EE 规范)。
*   **构建工具**: Maven。执行构建或测试时，请优先使用项目根目录下的 `./mvnw` (或 `.\mvnw.cmd` 在 Windows 上) 包装器，而不是系统全局的 `mvn`。
*   **模块架构**: 本项目是一个多模块架构。
    *   `pap4j-boot3-example`: 存放各类集成示例和测试业务。
    *   `pap4j-boot3-starters`: 存放自定义的 Spring Boot Starter 组件。
    *   `pap4j-common`: 存放基础工具类和通用数据结构库（不可随意引入强业务依赖）。

## 2. 编码与架构规范 (Coding & Architecture Standards)
*   **禁止私自造轮子**: 在引入新的工具类或并发结构前，必须先搜索 `pap4j-common`（例如 `pap4j-common-datastructure`）下是否已有现成的实现（如 `IdempotentTaskLock` 等）。优先复用 Apache Commons、Guava 等成熟基础库（前提是项目中已引入）。
*   **并发与多线程 (阿里规范级别)**:
    *   **禁止显式创建线程**: 严禁在业务代码中直接 `new Thread()`。必须使用线程池。
    *   **严禁使用 Executors 默认工厂**: 不允许使用 `Executors.newFixedThreadPool()` 等方法创建线程池，因为它们底层使用的是无界队列 `LinkedBlockingQueue`（容易导致 OOM）。必须显式使用 `ThreadPoolExecutor` 的构造函数，并指定有界队列大小及合理的拒绝策略（如 `CallerRunsPolicy` 或自定义拒绝处理）。
    *   **必须自定义线程池命名**: 必须通过自定义 `ThreadFactory` 并结合 `AtomicInteger` 为线程池内的线程命名（如 `xxx-worker-%d`），以便于线上排查问题和打印日志。
    *   **Spring 并发管理**: 在 Spring 环境中，优先使用 `@Async`，或者将自定义的 `ThreadPoolExecutor` 注册为 Spring Bean。如果是 Bean，必须配置 `destroyMethod = "shutdown"` 或在 `@PreDestroy` 中执行优雅停机。
*   **异常处理与状态安全**:
    *   **中断处理**: 捕获 `InterruptedException` 时，必须显式调用 `Thread.currentThread().interrupt()` 恢复中断状态，以保证系统的优雅停机能力。
    *   **禁止生吞异常**: catch 块中必须有实质性的处理或日志打印，严禁仅使用 `e.printStackTrace()`。
    *   **状态可见性与原子性**: 多线程共享的可变状态变量必须使用 `volatile` 保证可见性。处理复合操作时，必须考虑死锁避免及原子性（优先使用 `ConcurrentHashMap.compute/putIfAbsent` 或 `AtomicReference` 等无锁/原子 API）。
    *   **内存泄漏防御**: 使用基于内存的集合（如 Map/List）做缓存或状态追踪时，**必须**设计清理机制（如结合 ScheduledExecutorService 的定时清理，或基于容量/时间的淘汰策略如 Caffeine），严防 OOM。
*   **日志规范 (Google/阿里级别)**:
    *   **统一门面**: 统一使用 `org.slf4j.Logger` (SLF4J) 接口。
    *   **禁止标准输出**: 严禁在提交的代码中遗留 `System.out.println` 或 `System.err.println`。
    *   **性能优先**: 日志拼接必须使用占位符 `{}`，而非字符串加号 `+`（避免不必要的对象创建和字符串拼接开销）。
    *   **异常日志**: 记录异常时，`e` 必须作为日志方法的最后一个独立参数传入，而不是与字符串拼接（如 `log.error("Failed: ", e)`，而不是 `log.error("Failed: " + e)`）。
*   **集合与泛型**:
    *   初始化集合时，尽量指定初始容量（如 `new ArrayList<>(16)`，`new HashMap<>(16)`）。
    *   返回空集合时，优先使用 `Collections.emptyList()`，而非 `new ArrayList<>()`。
*   **代码整洁度 (Clean Code)**:
    *   **方法职责单一**: 一个方法只做一件事，如果方法体超过 50 行，请考虑抽取私有方法。
    *   **魔法值处理**: 代码中严禁出现未定义的“魔法值”（如直接写死的状态码 `1`, `"SUCCESS"` 等），必须提取为 `Enum` 或 `static final` 常量。

## 3. 修改与重构原则 (Modification Principles)
*   **最小侵入性**: 修改代码时采用外科手术式的精准修改（Surgical edits），不要为了“清理代码”而修改与当前任务无关的文件，除非用户明确要求。
*   **保证编译通过**: 每次完成代码修改后，如果涉及公共模块，应建议或主动运行相关的单元测试（如 `.\mvnw.cmd clean test -pl <模块名> -Dtest=<测试类>`）以确保没有破坏现有逻辑。
*   **注释与文档**: 如果修改了核心类或复杂的并发逻辑，必须更新或补充详尽的 Javadoc，注明设计意图、局限性及关键的防御性设计（如防脏读、防 OOM 策略）。

## 4. Git 交互行为
*   **禁止自动提交**: 无论你帮我写了多好的代码，都**绝对不要**自动执行 `git add` 或 `git commit`。所有的提交动作必须由我亲自确认和执行。
*   如果我要求你“生成提交信息”，请先运行 `git status` 和 `git diff` 了解上下文，并参考 `git log` 模仿项目中已有的 Commit Message 风格（例如：`feat: ...`, `fix: ...`, `refactor: ...`）。