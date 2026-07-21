---
name: agent-handoff
description: [协同/交接] 任务挂起与上下文断点续传交接技能。当中长任务暂停、上下文接近 Token 上限或需要切换对话/开发者交接时，自动总结当前进展、未尽事项与环境上下文，导出写回 HANDOFF.md 文件。
globs: ".ai/diagnostics/HANDOFF.md, docs/HANDOFF.md"
---

# 🤝 agent-handoff 技能 SOP (任务挂起与上下文断点续传交接)

## 📌 触发机制

- **🤖 自动触发**：当检测到当前对话步数过多、跨模块任务未完结且需要挂起，或开发者即将中断会话时。
- **💬 显式触发**：当开发者输入 `agent-handoff`、`“导出任务交接文档”`、`“挂起当前进度”` 或 `“保存上下文断点”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：状态与未尽事项盘点 (Status Check)
1. 使用 PowerShell 查看当前 Git 暂存与工作区状态：
   ```powershell
   git status
   ```
2. 梳理已完成的修改点（修改的物理文件路径、增加的单元测试）。
3. 盘点未完成的 Task 以及遗留的技术难题或阻塞点。

### 步骤 2：生成 HANDOFF.md 文档 (Export HandOff Spec)
在 [.ai/diagnostics/HANDOFF.md](../../diagnostics/HANDOFF.md) 中写入结构化交接报告：

```markdown
# 🤝 Agent 任务交接与断点续传报告 (HANDOFF)

## 📌 1. 任务背景与目标
- **原始需求**: ...
- **目标子模块**: `pap4j-common/...`

## 🟢 2. 已完成的工作 (Completed Work)
- [x] 修改文件: `pap4j-boot3-starters/.../ReqResLoggerHttpFilter.java`
- [x] 单测验证: 通过 `.\.agent\agent-test.cmd "-Dtest=ReqResLoggerReplayTest"`

## 🟡 3. 未尽事项与剩余 Task (Pending Tasks)
- [ ] **Task 2**: 完善边界逻辑空指针校验
  - 待修改文件: `.../FileUtils.java`
  - 验证命令: `.\.agent\agent-test.cmd "-Dtest=<YourPendingTestClass>"`

## ⚠️ 4. 关键设计决策与阻塞点 (Notes & Blockers)
- 遵循 `guard.md` 禁令，未在实体类中使用 `@OneToMany` 关联注解。
```

### 步骤 3：续传接入与快速恢复 (Resume Workflow)
新会话或新开发者接入时：
1. 读取 [.ai/diagnostics/HANDOFF.md](../../diagnostics/HANDOFF.md)。
2. 运行未尽 Task 对应的 PowerShell 验证命令，快速重设上下文与断点。

---

## ⚡ 命令行与验证规范 (PowerShell Compliance)
- 所有状态检查与验证必须原生兼容 PowerShell：
  ```powershell
  # 查看当前未提交代码改动
  git diff
  ```
