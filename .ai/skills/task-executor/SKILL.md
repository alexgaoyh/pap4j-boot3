---
name: task-executor
description: [核心/流程] 任务执行编排器。接收需求后先进行任务画像，基于标签动态生成自检清单，逐项执行并打勾验证，全部通过后输出 Commit Message。
globs: "*"
---

# ⚙️ task-executor 技能 SOP（任务执行编排器）

## 📌 触发机制

- **入口锚点**：AI.md 的 `[Edit]` 功能原型指向本技能，任何代码修改前必须先进入本流程。
- **💬 显式触发**：`task-executor`、`"执行任务"`、`"开始实施"`。

---

## 🧭 与相邻技能的关系

| 技能 | 本技能的角色 |
|------|-------------|
| `grill-me` | 命中断时，由本技能调用发起质询 |
| `smart-diagnose-heal` | 验证失败时，由本技能调用诊断自愈（计入重试轮数） |
| `tdd-workflow` | 需要 TDD 的 Task，由本技能在其执行阶段内激活 |
| `utility-lookup` | 涉及 `pap4j-common` 新增工具时，本技能在执行前触发查重 |

---

## 🛠️ 执行流程

```
请求到来
  │
  ├─ 第 1 步：任务画像（Profile）
  │
  ├─ 第 2 步：生成自检清单（Checklist）
  │
  ├─ 第 3 步：前置门控（Gate）
  │
  ├─ 第 4 步：逐项执行循环（Loop）
  │    ├─ 取一项 → [Edit] → [x]
  │    └─ [Shell] 验证 → [x] 或失败重试
  │
  └─ 第 5 步：收尾（Finalize）
       ├─ 全量审计
       └─ 输出 Commit Message
```

---

### 第 1 步：任务画像（Profile）

分析任务需求，产出特征标签。

**特征标签体系**：

| 维度 | 标签 | 判断依据 |
|------|------|---------|
| 变更范围 | `single-module` | 仅涉及一个子模块内文件 |
| | `cross-module` | 涉及多个子模块 |
| | `involves-common` | 涉及 `pap4j-common` 下文件 |
| 变更类型 | `new-feature` | 新增功能 / 工具类 |
| | `bug-fix` | 修复既有代码缺陷 |
| | `refactor` | 代码重构，无行为变化 |
| | `config-change` | 依赖 / 配置变更 |
| 技术领域 | `concurrency` | 线程池、锁、ForkJoin |
| | `persistence` | JPA / MyBatis / 事务 |
| | `api-contract` | Controller 接口 / OpenAPI |
| | `algorithm` | 性能敏感算法 |
| 影响面 | `public-api` | 修改 `pap4j-common` 的 public 方法签名 |
| | `internal-only` | 内部实现变更，不对外暴露 |

**输出示例**：
```
{ scope: single-module, type: bug-fix, area: persistence, impact: internal-only }
```

---

### 第 2 步：生成自检清单（Checklist）

基于任务画像标签，从检查项池中筛选匹配项，组成专属清单。

#### 检查项池（Checklist Pool）

**公共基线（必检）**：
- [ ] 1. 未残留 `System.out` / `e.printStackTrace()`
- [ ] 2. 日志使用 SLF4J 占位符 `{}`，非 `+` 字符串拼接
- [ ] 3. 异常日志将异常实例作为最后一个参数传入（`log.error("msg: ", e)`）
- [ ] 4. 空指针边界防御（入参、返回值均做防护）
- [ ] 5. 魔法值已抽取为 `static final` 常量或 `Enum`
- [ ] 6. **仅修改目标代码**，未顺带重构无关老代码
- [ ] 7. 本次修改已验证通过（`[Shell]` 已执行）
- [ ] 8. 当前重试轮数 ≤ 2（未超上限）

**涉及跨模块 / pap4j-common 时追加**：
- [ ] 9. public 方法签名保持向后兼容（不破坏下游调用方）
- [ ] 10. 新增 public 方法已确认走 `[QuickPlan]` 或 `[Plan]`

**涉及持久层时追加**：
- [ ] 11. 无 `@OneToMany` / `@ManyToMany` / `@ManyToOne` / `@OneToOne` 关联注解
- [ ] 12. `@Transactional` 仅在 Service 方法级别（非类级别）
- [ ] 13. `@Transactional` 写操作已配 `rollbackFor = Exception.class`
- [ ] 14. 循环内无 Repository / Mapper 调用（N+1 防御）
- [ ] 15. `SELECT` 已显式指定字段列表，无 `SELECT *`
- [ ] 16. Controller 未直接暴露 / 返回 Entity 对象（使用 DTO/VO）

**涉及并发时追加**：
- [ ] 17. 无 `new Thread()` 或 `Executors.newXxx()` 无界线程池
- [ ] 18. ThreadPoolExecutor 使用显式构造函数 + 有界队列
- [ ] 19. `ThreadLocal` 已配 `finally { remove() }`
- [ ] 20. `InterruptedException` 已恢复中断状态
- [ ] 21. I/O 阻塞任务未使用 `ForkJoinPool.commonPool()`

**涉及配置 / 依赖变更时追加**：
- [ ] 22. 依赖版本未引入已知 CVE（无已知安全漏洞）
- [ ] 23. 配置变更保留旧配置向后兼容

**涉及算法 / 性能时追加**：
- [ ] 24. 高频循环内未重复创建 `ByteArrayOutputStream` 等缓冲对象
- [ ] 25. 集合初始化已指定初始容量

**涉及 Swagger 模块时追加**：
- [ ] 26. 新增 Controller / 接口已标注 Swagger 注解（`@Tag` / `@Operation` / `@Parameter`）

---

### 第 3 步：前置门控（Gate）

基于任务画像判断是否触发强阻断：

```text
if 满足以下任一条件:
   - 跨模块变更（cross-module / involves-common）
   - 底层算法优化（algorithm）
   - 多文件联动 ≥3 个 src/main/java 源文件
   - Maven 依赖 / Spring Boot 配置变更
   - 持久层红线违规
   - 修改 pap4j-common 的 public 方法签名
then
   → [Plan] 模式 — 输出设计方案
   → 调用 grill-me 技能发起质询（并发/边界/幂等/兼容）
   → 等待开发者确认方案
else
   → [QuickPlan] — 一句话说明变更范围后直接进入执行
```

**产出**：确认的设计方案 + 目标文件列表。

---

### 第 4 步：逐项执行循环（Loop）

```text
while 清单中还有未标记 [x] 的检查项:
  取与当前项关联的 [Edit] 操作 → 执行修改
  标记该项为 ✅ [x]
  执行对应的 [Shell] 验证命令
  if 验证通过:
    标记该项为 ✅ [x]
    进入下一项
  else:
    重试次数 += 1
    if 重试次数 > 2:
      ⛔ 停止执行，向开发者汇报：失败项 + 已尝试的 2 种思路 + 根因判断
      return
    else:
      调用 smart-diagnose-heal 诊断 → 回到 [Edit]
```

**执行原则**：
- 一次只处理一项，不跳跃
- 每项必须配验证命令，无验证不通过
- `[Shell]` 命令根据运行环境选择：Git Bash（如 Claude Code）用左列命令，PowerShell 用右列命令

---

### 第 5 步：收尾（Finalize）

1. **全量审计**：扫描清单全部项是否均为 `[x]`
2. **安全性最终核验**：对照 guard.md 审计清单快速过一遍
3. **汇报结果**：清单打勾状态 + 验证结果 + 改动物理范围
4. **输出 Commit Message**：

```text
<type>(<scope>): <short description>

- 第 1 步: <what>
- 第 2 步: <what>
...

Closes / Ref #<issue>
```

---

## ⚡ 命令行与验证规范（Git Bash / PowerShell Compliance）

### Git Bash 环境（如 Claude Code）
```bash
# Maven 单测
./.agent/agent-test.cmd "-Dtest=<TestClass>"
# 文本搜索
grep -rn "<pattern>" --include="*.java" .
# 命令链
cd module && mvn test
```

### PowerShell 环境
```powershell
# Maven 单测
./.agent/agent-test.cmd "-Dtest=<TestClass>"
# 文本搜索
Get-ChildItem -Recurse -Filter "*.java" | Select-String "<pattern>"
# 命令链（用分号代替 &&）
cd module; mvn test
```

---

## ⚠️ 重试上限规则

- `[Edit] → [Shell]` 为一个完整轮次
- 同一次任务，**最多 2 轮**
- 第 3 轮仍失败 → 立即停止，不得继续
- 前一轮修正全部通过后，新增功能引入的新失败 → 不算重试（新任务）
