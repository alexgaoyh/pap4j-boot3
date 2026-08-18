---
name: task-executor
description: [核心/总控] 任务执行与技能总编排器。作为全局任务唯一主干中枢，区分实施类任务（完整画像与自检清单）与只读咨询类任务（轻量路由），按需裁剪质量门禁，并无缝调度串联全生命周期 15 大战术技能。
globs: "*"
---

# ⚙️ task-executor 技能 SOP（任务执行与全技能总编排器）

## 📌 触发机制与任务分类路由

- **全局总入口**：所有代码编写、Bug 修复、接口重构等实施类任务的唯一主干编排中枢。`AI.md` 的 `[Edit]` 功能原型强制绑定本技能。
- **任务分类分流（防止过度中心化与注意力稀释）**：
  - 📖 **只读 / 咨询 / 教学类任务**（如纯技术带读、依赖架构评估、工具库检索且不产生代码修改）：**跳过任务画像与代码自检清单**，轻量直通对应技能（如 [`tech-explain-teach`](../tech-explain-teach/SKILL.md)、[`utility-lookup`](../utility-lookup/SKILL.md)、[`architecture-review-doc`](../architecture-review-doc/SKILL.md)）。⚠️ **中途转码落地即回归**：若直通过程转为代码修改（如检索后新增工具类、带读后动手改码），**立即转回实施类流水线**，不得绕过自检清单。
  - 🛠️ **代码实施 / 变更类任务**（涉及代码修改 `[Edit]`、配置修改、Bug 修复）：**全量进入本 SOP 编排流水线**。
- **敏捷交互原则（反冗余仪式感）**：流转中严禁输出大段形式化阶段宣称（如“正在进入阶段1画像...正在组装清单...”），将认知算力聚焦于改动逻辑与测试验证；分类路由仅输出一行结构化标签（如 `[Routed: read-only]`）即可。**阶段 2 `[Plan]` 设计方案与质询输出属实质内容，不受仪式感裁剪。**

---

## 🧭 全生命周期技能联动矩阵 (Skill Orchestration Matrix)

本技能作为 **Central Master Orchestrator（总调度中枢）**，在实施任务的生命周期（阶段 0~6）中按需激活对应的战术技能：

| 生命周期阶段 | 联动技能 | 激活条件 | 核心产出与联动契约 |
| :--- | :--- | :--- | :--- |
| **阶段 0：需求接入与拆解 (Ingest & Clarify)** | [task-breakdown](../task-breakdown/SKILL.md) | 宽泛需求、跨多模块中大型特性 | 输出 mini-PRD 与原子化 Task 列表 |
| | [tech-explain-teach](../tech-explain-teach/SKILL.md) | 需先理清现有复杂源码/设计机制 | 输出类图推演与架构源码带读 |
| **阶段 1：任务画像与自检清单 (Profile & Checklist)** | ——（内部画像，无外部技能联动） | 所有实施类任务必经 | 产出特征标签与专属自检清单 |
| **阶段 2：前置门控与方案质询 (Gate & Plan)** | [architecture-review-doc](../architecture-review-doc/SKILL.md) | 跨模块依赖重构或架构调整 | 静态分析依赖树，排查循环依赖与耦合 |
| | [grill-me](../grill-me/SKILL.md) | 触发 `AI.md` 强阻断（并发/JPA/破坏性契约） | 暂停编辑，向开发者发起 3~5 项设计质询 |
| **阶段 3：编码前查重与基桩准备 (Pre-Coding)** | [utility-lookup](../utility-lookup/SKILL.md) | 涉及 `pap4j-common` 新增工具/算法 | 检索 25+ 模块及 `utilities.md` 防重复造轮子 |
| | [offline-mock-fixture-gen](../offline-mock-fixture-gen/SKILL.md) | 涉及复杂 Controller/外部接口（多层嵌套DTO/鉴权/三方调用） | 生成 MockMvc 离线基桩与测试 JSON 数据 |
| **阶段 4：逐项执行、自愈与动态升轨 (Execution Loop)** | [tdd-workflow](../tdd-workflow/SKILL.md) | 核心算法/多分支业务逻辑/Bug修复编写 | 驱动“红 (失败单测) ➔ 绿 (最小实现) ➔ 重构” |
| | [smart-diagnose-heal](../smart-diagnose-heal/SKILL.md) | 编译报错或单测失败（单点重试 ≤ 2 次） | 提取精炼堆栈，零容器回放并自动修复 |
| | [native-lib-load-debug](../native-lib-load-debug/SKILL.md) | 遭遇 JNI / DLL 动态库加载失败 | 执行 JVM MSVC CRT 依赖等七步阶梯排查 |
| | [agent-handoff](../agent-handoff/SKILL.md) | 任务过长/Token 耗尽/需要挂起交接 | 导出当前进度与断点上下文至 `HANDOFF.md` |
| **阶段 5：分级质量门禁 (Graded Quality Gate)** | [refactor-scanner](../refactor-scanner/SKILL.md) | 涉及中大型/跨模块/架构敏感改动 | 运行适应度函数扫描架构红线 |
| | [api-contract-sync](../api-contract-sync/SKILL.md) | 修改了 Controller / REST 接口 | 导出 OpenAPI 快照，核对 Swagger 注解规范 |
| | [code-review-verification](../code-review-verification/SKILL.md) | 测试通过后交付前的独立审查 | 独立验证视角批判性审查 git diff，排除红线与边界漏洞后放行 |
| **阶段 6：收尾、提交与自适应演化 (Finalize & Learn)** | [git-commit-changelog-drafter](../git-commit-changelog-drafter/SKILL.md) | 审查通过，准备交付 | 起草 Conventional Commit 与 Changelog |
| | [learn-and-persist](../learn-and-persist/SKILL.md) | 攻克新踩坑/新增工具/规则纠偏 | 工具自动索引更新；`guard.md`/`SKILL.md` 改动须经人工确认 |

---

## 🛠️ 全流程执行生命周期 (Execution Lifecycle)

```
📥 需求到来
  │
  ├─ 判决：是否为只读/咨询/教学类？
  │    ├─ 是 ──▶ 直通对应技能（tech-explain-teach / architecture-review-doc 等），不生成自检清单
  │    └─ 否（实施类任务） ──▶ 进入标准流水线 ⬇️
  │
  ├─ 阶段 0：需求接入与前置拆解 (Ingest & Clarify)
  │    └─ [中大型需求] ──▶ 激活 task-breakdown ──▶ 产出 mini-PRD & Task 列表
  │
  ├─ 阶段 1：任务画像与自检清单 (Profile & Checklist)
  │    ├─ 分析维度打标签 (Scope / Type / Area / Impact) ➔ 输出 [Profile: ...]
  │    └─ 动态组装专属自检清单 (Checklist Pool)
  │
  ├─ 阶段 2：前置门控与方案质询 (Gate & Plan)
  │    ├─ [架构重构] ──▶ 激活 architecture-review-doc
  │    ├─ [触发强阻断] ──▶ 激活 grill-me ──▶ 反向质询 + 输出 [Plan]
  │    └─ [轻量改动] ──▶ 输出 [QuickPlan]
  │
  ├─ 阶段 3：编码前查重与基桩准备 (Pre-Coding)
  │    ├─ [涉及 common 工具] ──▶ 激活 utility-lookup 查重
  │    └─ [涉及复杂接口] ──▶ 激活 offline-mock-fixture-gen 基桩生成
  │
  ├─ 阶段 4：逐项执行、自愈与动态升轨循环 (Execution Loop)
  │    ├─ ⚡ [改动范围蔓延检测] ──▶ 若扩散触发阻断条件且升轨次数=0，暂停并升轨至阶段 2 [Plan]（升轨上限 1 次）
  │    ├─ [核心/多分支/修复] ──▶ 激活 tdd-workflow (红-绿-重构)
  │    ├─ 执行代码修改 [Edit] ➔ 紧随执行测试验证 [Shell]
  │    ├─ [单测失败] ──▶ 激活 smart-diagnose-heal 诊断自愈 (单点 <=2 次)
  │    ├─ [Native 报错] ──▶ 激活 native-lib-load-debug 七步诊断
  │    ├─ [长会话挂起] ──▶ 激活 agent-handoff 导出 HANDOFF.md
  │    └─ 🔄 [Task数≥2收尾] ──▶ 执行模块全量单测回归，确保后置改动未破坏前置 Task 基线
  │
  ├─ 阶段 5：分级质量门禁 (Graded Quality Gate)
  │    ├─ 🟢 [轻量改动: typo/≤2个源文件/internal-only] ──▶ 内联快速审查，跳过耗时全量扫描
  │    └─ 🛡️ [标准/重型改动] ──▶ 激活 refactor-scanner + api-contract-sync + code-review-verification
  │
  └─ 阶段 6：收尾、提交与自适应演化 (Finalize & Learn)
       ├─ 激活 git-commit-changelog-drafter ──▶ 起草规范 Commit Message
       └─ 激活 learn-and-persist ──▶ 工具自动索引；规则/SOP 提请用户显式确认后持久化
```

---

### 阶段 0：需求接入与前置拆解 (Ingest & Clarify)

1. **复杂度研判**：
   - 若需求涉及跨模块、多层交互或自然语言描述宽泛，**自动调用 [task-breakdown](../task-breakdown/SKILL.md)**，在对话中输出 mini-PRD 与原子化 Task 清单。
   - 若开发者对底层设计机制存在疑惑，**自动调用 [tech-explain-teach](../tech-explain-teach/SKILL.md)** 进行类图与源码推演。
2. **只读分流复核**：若在触发机制中已判定为只读/咨询/教学类任务，直接路由到对应技能，**跳过本流水线（阶段 1~6）**；否则进入阶段 1。

---

### 阶段 1：任务画像与自检清单 (Profile & Checklist)

> **【交互规范】**：画像完成后仅输出 1 行结构化标签（如 `[Profile: single-module | new-feature | involves-common | additive-api]`），无需展开冗长自然语言解释。

#### 1. 任务画像（Profile）
| 维度 | 标签 | 判断依据 |
|------|------|---------|
| **变更范围** | `single-module` | 仅涉及一个子模块内文件 |
| | `cross-module` | 涉及多个子模块 |
| | `involves-common` | 涉及 `pap4j-common` 下文件 |
| **变更类型** | `new-feature` | 新增功能 / 工具类 |
| | `bug-fix` | 修复既有代码缺陷 |
| | `refactor` | 代码重构，无行为变化 |
| | `config-change` | 依赖 / 全局配置变更 |
| **技术领域** | `concurrency` | 线程池、锁、ForkJoin、异步 |
| | `persistence` | JPA / MyBatis / 事务 |
| | `api-contract` | Controller 接口 / OpenAPI |
| | `algorithm` | 性能敏感算法 / 流式处理 |
| **影响面** | `breaking-api` | 破坏性契约：修改 `pap4j-common` public 签名/接口/返回值/throws |
| | `additive-api` | 契约新增：新增 `pap4j-common` public 方法/接口 |
| | `internal-only` | 内部实现变更，不对外暴露 |

#### 2. 自检清单池（Checklist Pool）
**公共基线（必检）**：
- [ ] 1. 未残留 `System.out` / `e.printStackTrace()`
- [ ] 2. 日志使用 SLF4J 占位符 `{}`，非 `+` 字符串拼接
- [ ] 3. 异常日志将异常实例作为最后一个参数传入（`log.error("msg: ", e)`）
- [ ] 4. 空指针边界防御（入参、返回值均做防护）
- [ ] 5. 魔法值已抽取为 `static final` 常量或 `Enum`
- [ ] 6. **外科手术式修改**：仅修改目标代码，严禁顺带重构无关老代码
- [ ] 7. 本次修改已验证通过（`[Shell]` 已执行并绿灯）
- [ ] 8. 未触发熔断红线（单点连续重试 ≤ 2 且全局连续失败 < 3）

**按画像标签追加检查项（先 `Read .ai/guard.md` 代码审计清单，映射见下表）**：

| 画像标签 | 追加检查项 |
| :--- | :--- |
| `persistence` | 持久层关联 / 持久层 N+1 / 持久层事务 |
| `concurrency` | 并发安全（`new Thread()` / 无界队列） |
| `api-contract` | Swagger 注解；涉及上传/鉴权/跨域时追加防御性安全（SQL 注入 / 文件上传 / 接口安全 / 跨域过滤 / 配置与依赖） |
| `involves-common`、`breaking-api`、`additive-api` | 公共 API 边界 + Javadoc 规范 |
| `config-change` | 防御性安全（配置与依赖）+ 配置向后兼容 |
| 通用（任何改动） | 日志规范 / 方法边界 / 精度安全 / 生命周期 |

---

### 阶段 2：前置门控与方案质询 (Gate & Plan) —— 【决策意图：判定是否需要前置质询与输出设计方案】

```text
if (impact == 'breaking-api' || area in ('algorithm', 'concurrency') || 联动修改 src/main 源文件 ≥ 3 个 || type == 'config-change' || 涉及持久层/事务禁令):
    1. 若涉及依赖重构 ──▶ 自动调用 architecture-review-doc 评估依赖树
    2. 进入 [Plan] 状态 ──▶ 自动调用 grill-me 技能发起苏格拉底式反向质询
    3. 输出设计方案并等待开发者显式确认
else:
    进入 [QuickPlan] 状态 ──▶ 1 句话说明变更物理范围后直接进入执行
```

---

### 阶段 3：编码前查重与基桩准备 (Pre-Coding)

1. **工具防重查重**：若标签包含 `involves-common` 且为 `new-feature`，**自动调用 [utility-lookup](../utility-lookup/SKILL.md)** 检索防重。
2. **测试基桩准备**：若涉及复杂 Controller（请求体含多层嵌套对象、涉及上传、鉴权拦截或下游多接口调用），**自动调用 [offline-mock-fixture-gen](../offline-mock-fixture-gen/SKILL.md)** 生成 MockMvc 离线基桩与 JSON 数据。

---

### 阶段 4：逐项执行、自愈与动态升轨循环 (Execution Loop & Escalation)

```text
升轨计数 = 0

while 任务清单中存在未标记 [x] 的项:
    1. 取出当前 Task
    
    2. 【动态升轨检测 (Dynamic Escalation)】:
       若执行中发现物理改动范围扩大（如修改源文件累计达到 ≥3 个、触碰 pap4j-common public 契约、或引入持久层/并发等强阻断特性）：
       if (升轨计数 == 0):
           升轨计数 += 1
           ⛔ 立即暂停代码修改！回退并强制升轨至阶段 2 输出 [Plan] 设计方案，等待开发者确认后再行修改；升轨后重置单点重试与全局连续失败计数。
       else:
           ⛔ 任务范围二次失控（升轨达上限）！立即停机汇报，请求开发者人工介入拆解任务。
           return
    
    3. 若涉及非平凡核心业务、多分支状态机、核心算法或 Bug 修复 ──▶ 优先调用 tdd-workflow (先编写失败单测 ➔ 最小实现)
    4. 执行代码修改 [Edit] ➔ 紧随执行自动化验证命令 [Shell]
    
    if (验证全部通过):
        单点重试轮数 = 0
        全局熔断计数 = 0  # 成功归零，仅连续失败计数
        标记当前项为 ✅ [x]
        推进下一项
    else:
        单点重试轮数 += 1
        全局熔断计数 += 1
        if (单点重试轮数 > 2 || 全局熔断计数 >= 3):
            ⛔ 立即触发熔断停机！向开发者汇报：错误详情 + 已尝试的思路 + 根因判断，请求指示
            return
        else:
            if (属于 JVM Native/DLL 异常):
                调用 native-lib-load-debug 技能排查
            else:
                调用 smart-diagnose-heal 提取精炼堆栈并自愈
            回到 [Edit] 修复并重新验证

# 多任务全量回归防漏校验
if (Task 列表总项数 ≥ 2):
    ⚡ 触发涉及模块全量回归测试，确保后置 Task 未破坏前置 Task 功能基线
    if 回归失败:
        计入全局熔断计数 → 走 smart-diagnose-heal 自愈 → 修复后重跑回归
        连续 ≥ 3 次 → 熔断停机汇报

opt 中途长任务挂起:
    若会话接近 Token 上限或开发者要求暂停 ──▶ 调用 agent-handoff 导出 HANDOFF.md（熔断/升轨计数不跨会话延续，恢复后重新计算）
```

---

### 阶段 5：分级质量门禁 (Graded Quality Gate) —— 【决策意图：判定是否需要裁剪耗时的全量扫描】

为了平衡验证严密性与执行效率，质量门禁实行**分级裁剪**：

#### 🟢 轻量改动档 (Trivial / Inline Path)
- **判定条件**：画像为 `single-module` + `internal-only`（如 Typo 修复、注释补全、文档调整、≤2 个源文件的内部实现优化）。
- **门禁动作**：
  - 执行**轻量内联自检 (Inline Check)**（对照自检清单核对无 `System.out` / NPE 隐患即可）。
  - **裁剪跳过** 全量 `refactor-scanner` 扫描与 `api-contract-sync` 快照导出，直接放行进入收尾。

#### 🛡️ 标准与重型改动档 (Standard / Heavy Path)
- **判定条件**：涉及跨模块、`pap4j-common` 公共 API、Controller/REST 接口或 ≥3 个文件联动。
- **门禁动作**：
  1. **架构红线扫描**：调用 [refactor-scanner](../refactor-scanner/SKILL.md)，仅执行 `RefactorScanner` **静态扫描核验**（若本次改动引入新违规则打回修复；**严禁借机顺带重构无关的历史存量代码**）。
  2. **契约同步校验**：若涉及 Controller 变动，调用 [api-contract-sync](../api-contract-sync/SKILL.md) 导出 OpenAPI JSON 快照。
  3. **独立验证代理审查**：调用 [code-review-verification](../code-review-verification/SKILL.md)，以批判性视角审阅 `git diff`（严格核对无 `guard.md` 违规、无 NPE/极值漏洞、单测覆盖完整且无调试代码残留），审查通过后放行。
  4. **审查打回与全局熔断**：若审查发现问题打回修复，同一审查点最多打回 2 次；打回轮次计入**全局熔断计数**（连续失败/打回 ≥ 3 即硬熔断）。超限立即停机并向开发者上报，严禁无界拉扯。

---

### 阶段 6：收尾、提交与自适应演化 (Finalize & Learn)

1. **起草 Commit 信息**：
   - 调用 [git-commit-changelog-drafter](../git-commit-changelog-drafter/SKILL.md)，起草符合 Conventional Commits 规范的提交信息与更新日志。
2. **触发自适应演化闭环（Learn & Persist）**：
   - 调用 [learn-and-persist](../learn-and-persist/SKILL.md)，检查本轮任务是否产生知识沉淀：
     - **新工具沉淀**：若在 `pap4j-common` 新增了公共工具类/方法，**自动追加更新 [.ai/utilities.md](../../utilities.md)**。
     - **新规则与 SOP 演进安全网（Human-in-the-loop）**：若发现新踩坑、反模式或需要调整 `guard.md` / `SKILL.md`，**严禁静默直接覆写**！必须输出 `[Proposal] 规则沉淀提案 (Diff)`，向开发者陈述条款与理由，**经开发者显式确认后方可持久化写入**。

---

## ⚡ 命令行与验证规范 (Git Bash / PowerShell Compliance)

### Git Bash 环境（如 Claude Code）
```bash
# 执行单测
./.agent/agent-test.cmd "-Dtest=<TestClass>"
# 文本检索
grep -rn "<pattern>" --include="*.java" .
# 命令链拼接（务必经 .agent 脚本执行，遵守 guard.md 构建合规）
grep -rn "<pattern>" --include="*.java" . && ./.agent/agent-test.cmd "-Dtest=<TestClass>"
```

### PowerShell 环境
```powershell
# 执行单测
./.agent/agent-test.cmd "-Dtest=<TestClass>"
# 文本检索
Get-ChildItem -Recurse -Filter "*.java" | Select-String "<pattern>"
# 命令链拼接（使用分号代替 &&；务必经 .agent 脚本执行，遵守 guard.md 构建合规）
Get-ChildItem -Recurse -Filter "*.java" | Select-String "<pattern>"; ./.agent/agent-test.cmd "-Dtest=<TestClass>"
```

---

## ⚠️ 熔断阈值与自愈红线 (Global Fuse & Circuit Breakers)

- `[Edit] → [Shell]` / 审查打回修改定义为 1 轮执行周期。
- **单点重试阈值**：同一报错/失败点连续重试，**最多 2 轮**。
- **全局熔断计数**：仅统计**连续失败/打回轮次**（成功即归零），达到 **3 轮** 立即硬熔断停机，避免多原子任务累计误伤。
- **升轨上限约束**：单任务生命周期内升轨**最多 1 次**，严禁二次升轨或利用升轨无限重置计数器；超限立即硬停机。
- **熔断动作**：停止一切自动重试，向开发者汇报完整上下文（错误现象 + 已尝试的方案 + 根因研判），请求明确指令。
