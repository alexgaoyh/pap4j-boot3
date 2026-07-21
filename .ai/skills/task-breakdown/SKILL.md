---
name: task-breakdown
description: [需求/设计] 大功能 PRD 梳理与 Task 拆解技能。接收中大型/复杂新功能开发需求时，将自然语言需求转化为 Markdown 格式 mini-PRD，并按模块拆解为带 PowerShell 验证命令的 Task 列表。
globs: "*"
---

# 📋 task-breakdown 技能 SOP (大功能 PRD 梳理与 Task 拆解)

## 📌 触发机制

- **🤖 自动触发**：当接收到涉及中大型、复杂业务逻辑或跨多个模块的新功能开发需求时。
- **💬 显式触发**：当开发者输入 `task-breakdown`、`“梳理新功能需求”` 或 `“拆解 Task 列表”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：需求分析与 mini-PRD 生成
1. 深入分析自然语言需求，明确背景、业务目标与非功能性要求。
2. 在 `docs/prd/` 或当前对话中输出 Markdown 格式的 **mini-PRD**：
   - **背景与目标 (Background & Goals)**
   - **核心功能范围 (In-Scope)**
   - **非功能要求 (Non-functional Requirements)**: 遵循 JDK 17 / SB3 规约，禁止使用 JPA 级联注解。
   - **验收标准 (Acceptance Criteria, AC)**: 可明确测量的规则列表。

### 步骤 2：原子化 Task 拆解 (Task Decomposition)
将 PRD 拆解为有序的 Task 列表。每个 Task 必须具备以下属性：
1. **单一职责**：单个 Task 尽量控制在 1~2 个文件修改内。
2. **明确的物理路径**：指定涉及的具体 Java 类或配置文件路径。
3. **独立 PowerShell 验证命令**：每个 Task 必须配有一条用于验证的 PowerShell 测试脚本命令。

#### Task 模板样例：
```markdown
- [ ] **Task 1: 编写通用工具接口与单测**
  - **物理路径**: `pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/FileUtils.java`
  - **验收条件**: 完成工具方法编写并通过 100% 边界单测
  - **验证命令**: `.\.agent\agent-test.cmd "-Dtest=FileUtilsTest"`
```

### 步骤 3：逐 Task 循环迭代执行 (Execution Loop)
1. 顺序选择未完成的 Task。
2. 依据 `AI.md` 规范进行 `[QuickPlan]` 或 `[Plan]`，而后进行 `[Edit]`。
3. 执行该 Task 对应的 PowerShell 验证命令。
4. 验证通过后标记 Task 为 `[x]`，再推进下一个 Task。

---

## ⚡ 命令行与验证规范 (PowerShell Compliance)
- 严禁使用 Linux 命令（`grep` / `cat` / `&&`）。
- 测试验证命令必须使用 `.\.agent\agent-test.cmd` 并加双引号包裹参数：
  ```powershell
  # 将 <YourModuleTaskTest> 替换为对应模块拆解 Task 的实际测试类名（例如 FileUtilsTest）
  .\.agent\agent-test.cmd "-Dtest=<YourModuleTaskTest>"
  ```
