# AI 工作流：开发与重构流程 (AI Workflow)

本文件概述了 AI 代理在修改 `pap4j-boot3` 代码库时必须遵循的操作周期。

## 1. 研究 -> 策略 -> 执行 (RSE)
每个任务必须经过以下三个阶段：

### 阶段 1：研究 (经验验证)
* **主导角色**: 架构师 / 代码守卫
* **目标**: 验证假设并复现问题。
* **动作**: 使用代码搜索与路径匹配工具 **`[Search]`** 映射依赖关系。
* **复现**: 对于 Bug 修复，在应用修复方案之前，必须先编写测试用例或脚本以确认故障。

### 阶段 2：策略 (设计与影响)
* **主导角色**: 架构师
* **目标**: 制定精准的修改计划。
* **规划**: 对于复杂的架构变更或跨模块重构，必须开启设计规划模式 **`[Plan]`**。
* **冲突检查**: 确保计划不违反 `guard.md`。

> **`[Plan]` 输出必包含**: 变更范围（文件列表+子模块）/ 技术选型理由 / 风险与回滚 / 按序执行步骤 / PowerShell 验证命令。不适用的章节标注 N/A。

### 阶段 3：执行 (迭代式 P-A-V)
* **计划 (Plan)**: [架构师 / 测试工程师] 定义具体的变更和验证测试。
* **行动 (Act)**: [整洁代码实践者] 执行代码修改与写入操作 **`[Edit]`**；[代码守卫] 强制进行修改前审计。
* **验证与审计 (Validate & Audit)**: [测试工程师] 立即运行本地测试和代码检查 **`[Shell]`**。本地自测通过后，启动 **多智能体循环评价打分流程**：
  * **独立审计**: 优先派生独立的审计子智能体/子会话作为[审计专家]进行客观审查；若受限，则显式切换为批判性测试人格。
  * **量化打分**: 对红线合规度(40%)、逻辑完备度(30%)、测试覆盖率(20%)及代码整洁度(10%)进行综合评分(0-100)，并输出具体改进意见(Critique)。
  * **循环优化**: 若评分低于 90 分，必须返回 **行动 (Act)** 阶段迭代修复，重跑测试并重新审计（最多迭代 2 轮）。评分达 90+ 且自测通过方可交付。

## 2. 修改原则
* **外科手术式修改**: 针对特定问题进行精准编辑。除非用户明确要求，否则不要进行无关的"顺带"重构。
* **最小惊讶原则**: 遵循文件或模块中已有的命名约定和模式。
* **无破坏性变更**: 确保 `pap4j-common` 中内部 API 的向后兼容性。

## 3. 验证标准与自动化协议
*   **编译**: 每次修改都必须能编译通过。
*   **测试覆盖**: 新功能或 Bug 修复必须包含单元测试或集成测试。
*   **自动化验证协议 (Mandatory)**: 
    *   **链式执行 (Chained Execution)**: 任何代码修改 **`[Edit]`**，**必须**在同一个交互回合内紧随一个验证或测试命令 **`[Shell]`**。
    *   **PowerShell 链式控制规范**: 多条命令连续执行时，**严禁使用 Linux 的 `&&` 拼接符**（在 Windows 内置的 PowerShell 5.1 中会导致语法错误）。必须改用分号 `;` 或 PowerShell 逻辑控制符。
        *   ❌ 错误: `cd my-module && mvn clean test`
        *   ✅ 正确: `cd my-module; mvn clean test` 或者是 `mvn clean test -pl my-module`
    *   **顺序控制 (Sequential Control)**: 必须确保验证步骤在代码写入完全完成后再执行。
    *   **结果闭环 (Result Loop)**: 除非编译和测试全部通过，否则不应宣布任务完成。若验证失败，必须自动进行诊断并尝试修复。

### 🏛️ 基于适应度函数的渐进式演进 (Automated Fitness Functions)
为了保障重构与开发的交付质量，项目在 `pap4j-boot3-example-devtools` 模块下部署了自动化监考（适应度函数）测试类。AI 代理必须使用它们进行质量合规验证：

1. **代码规范扫描器 (RefactorScanner)**
   * **作用**: 遍历项目源码，静态扫描是否违反 `guard.md` 中的架构红线（如 `@Autowired` 字段注入、JPA级联关联注解、类级事务或超长方法等）。
   * **输出**: 自动生成或更新待重构清单：[.ai/diagnostics/refactor_todo.md]。
   * **命令**: 
     ```powershell
     mvn test -pl pap4j-boot3-example/pap4j-boot3-example-devtools "-Dtest=RefactorScanner" "-Dfile.encoding=UTF-8" "-Dmaven.gitcommitid.skip=true"
     ```
2. **失败日志精炼器 (DiagnosticsExtractor)**
   * **作用**: 当单元测试/编译执行失败时运行，自动扫描 surefire 报告，剔除 Spring, JUnit 等框架级冗余噪音日志，提取最核心的业务 Exception 堆栈。
   * **输出**: 自动生成或更新精炼错误日志：[.ai/diagnostics/test_failures.md]。
   * **命令**:
     ```powershell
     mvn test -pl pap4j-boot3-example/pap4j-boot3-example-devtools "-Dtest=DiagnosticsExtractor" "-Dfile.encoding=UTF-8" "-Dmaven.gitcommitid.skip=true"
     ```

### 🔁 修复重试上限（强制）

> 同一次 `[Edit] → [Shell]` 验证循环，**最多 2 轮**。第 3 轮仍失败时，立即停止并向用户汇报：完整错误信息、已尝试的两种思路、当前根因判断，并请求指令。严禁超过 2 轮后继续盲目重试。
>
> - 第 1 轮修了 A 错误，第 2 轮出现新的 B 错误 → 算新轮次，继续
> - 第 1 轮修完后全部通过，后续新增功能引入新失败 → 不算重试（新任务）
> - 计数按 `[Edit]` 开始到 `[Shell]` 结果返回为一个完整轮次

*   **PowerShell 常用命令**:
  ```powershell
  .\.agent\agent-test.cmd -pl <module> "-Dtest=<test_class>" test
  mvn clean test -pl <module> "-Dtest=<test_class>" "-Dfile.encoding=UTF-8" "-Dmaven.gitcommitid.skip=true" "-DskipTests=false"
  Get-ChildItem -Path . -Filter *Test.java -Recurse | Select-String -Pattern "Autowired"
  ```
*   **清理**: 任务完成且独立验证通过后，必须主动删除调试期间添加的所有临时文件、日志或 `System.out`。

## 4. Git 协议
* **禁止自动提交**: 严禁 AI 代理自主执行 `git commit` 或 `git push`。
* **起草提交信息**: 在任务验证通过后的最终回复中，AI **必须主动**为用户起草符合规范（`feat:`, `fix:`, `refactor:` 等）的 Commit Message，以方便用户手动提交。
