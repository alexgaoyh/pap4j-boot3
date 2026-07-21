---
name: code-review-verification
description: [质量/审查] 验证代理独立批判性 Diff Review 技能。完成 [Edit] 准备交付前，切换至验证代理批判视角，审查 git diff，全面检视缺失单测、System.out 残留、NPE 隐患及 guard.md 违规。
globs: "*"
---

# 🧐 code-review-verification 技能 SOP (验证代理独立批判性 Diff Review)

## 📌 触发机制

- **🤖 自动触发**：在完成代码修改 `[Edit]`、准备交付最终结果前。
- **💬 显式触发**：当开发者输入 `code-review-verification`、`“进行 Code Review”` 或 `“审查本次代码修改”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：角色切换 (Validation Agent Persona)
切换为 **【验证代理 / Reviewer】** 人格，摆脱编码者的主观倾向，以挑剔和批判性的视角对待任何代码改动。

### 步骤 2：提取并审阅 Git Diff (Diff Audit)
使用 PowerShell 获取本次对话涉及的所有代码改动 `git diff`：
```powershell
git diff
```

### 步骤 3：硬核规则与边界交叉核查 (Checklist)
逐行审查 diff 内容，核查以下 6 项硬指标：
1. **Guardrails 红线**：是否包含并发锁隐患、JPA 级联注解、`pap4j-common` 公共 API 变更？
2. **调试代码残留**：是否残留 `System.out.println`、`e.printStackTrace()` 或临时 `TODO`？
3. **空指针与边界防御**：入参及返回值是否有 NULL 值防护？极端集合为空时是否优雅处理？
4. **单测覆盖度**：新增逻辑是否补全对应的单元测试？
5. **日志与规范**：日志级别是否恰当？是否使用 Slf4j 占位符 `log.info("...", arg)`？
6. **PowerShell 环境兼容**：文档或说明中的命令是否均原生兼容 Windows PowerShell？

### 步骤 4：输出 Code Review 报告 (Pass / Reject)
根据审查结果输出结构化评审报告：

```markdown
### 🧐 Code Review 审查报告

- **审查结果**: 🟢 通过 (PASSED) / 🔴 驳回 (REJECTED)
- **审查视角**: 验证代理 (Validation Agent)

#### 🔍 详细项检查：
1. [x] Guardrails 规则自检
2. [x] 调试代码与控制台打印清理
3. [x] 空指针与极值边界防御
4. [x] 单元测试覆盖
5. [x] 日志规范与 PowerShell 指令兼容

*(若驳回，必须指出具体违规文件、代码行及修改指导方案)*
```

---

## ⚡ 命令行与验证规范 (PowerShell Compliance)
- 仅使用 PowerShell 原生命令查看 diff 与日志：
  ```powershell
  git diff
  ```
