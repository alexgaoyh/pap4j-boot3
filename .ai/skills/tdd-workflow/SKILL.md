---
name: tdd-workflow
description: [开发/测试] TDD 红-绿-重构测试驱动开发技能。在编写新功能或修复复杂 Bug 前，先编写断言失败的单元测试 (Red)，再编写最小化实现代码使测试通过 (Green)，最后进行外科手术式重构 (Refactor)。
globs: "**/src/test/**/*.java, **/src/main/**/*.java"
---

# 🔴🟢🔵 tdd-workflow 技能 SOP (红-绿-重构测试驱动开发)

## 📌 触发机制

- **🤖 自动触发**：当接收到需高可靠保证的核心业务算法、边界复杂的方法开发或 Bug 修复需求时。
- **💬 显式触发**：当开发者输入 `tdd-workflow`、`“TDD 开发”` 或 `“按测试驱动开发流程编写代码”` 时。

---

## 🧭 技能边界与协同关系 (Boundary & Synergy)

- **与 `task-breakdown` (需求拆解) 协同**：`task-breakdown` 负责**宏观 PRD 与 Task 列表拆解**；`tdd-workflow` 用于单个 Task 在编码实施时的**微观 TDD 范式**。
- **与 `utility-lookup` (工具查重) 协同**：经 `utility-lookup` 查重确认需新建工具后，可激活 `tdd-workflow` 进行红-绿-重构驱动开发。
- **与 `smart-diagnose-heal` (故障自愈) 区分**：`tdd-workflow` 的“红灯”属于**预期的未实现测试**；而 `smart-diagnose-heal` 用于处理**既有代码非预期报错的崩溃诊断与自愈**。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：🔴 红灯阶段 (Red — 编写断言失败的单测)
1. 明确新功能的物理路径与边界条件（NULL 值、极值、异常路径）。
2. 在 `src/test/java` 目录下编写单元测试类，针对未实现的方法书写断言逻辑。
3. 执行单测（Git Bash / PowerShell 通用），**确认测试失败 (Red Phase Pass)**：
   ```bash
   # 将 <YourTddTestClass> 替换为新建的 TDD 单元测试类名
   ./.agent/agent-test.cmd "-Dtest=<YourTddTestClass>"
   ```
4. 确认控制台输出预期的断言异常（如 `AssertionFailedError`）或未实现异常，验证测试的有效性。

### 步骤 2：🟢 绿灯阶段 (Green — 编写最小化实现代码)
1. 在 `src/main/java` 中编写**刚好能使测试通过的最小化实现代码**。
2. 严禁在绿灯阶段过早优化或编写未被测试覆盖的冗余逻辑。
3. 再次运行单测（Git Bash / PowerShell 通用），**确认测试全绿 (Green Phase Pass)**：
   ```bash
   # 将 <YourTddTestClass> 替换为新建的 TDD 单元测试类名
   ./.agent/agent-test.cmd "-Dtest=<YourTddTestClass>"
   ```

### 步骤 3：🔵 重构阶段 (Refactor — 外科手术式代码修缮)
1. 在测试全绿的安全网保护下，检查实现代码：
   - 提取魔数与冗余逻辑。
   - 检查空指针防护与 JDK 17 现代语法模式（如 `Text Blocks` / `switch` 表达式）。
   - 严格遵守 `guard.md` 中的并发锁与事务规范。
2. 重新运行单测，确保重构过程没有引入任何回归问题。

---

## ⚡ 命令行与验证规范 (Git Bash / PowerShell Compliance)
- `.agent/` 目录下的 `.cmd` 脚本在 Git Bash 和 PowerShell 中均可执行：
  ```bash
  # 将 <YourTddTestClass> 替换为实际需要验证的 TDD 单测类名
  ./.agent/agent-test.cmd "-Dtest=<YourTestClass>"
  ```
