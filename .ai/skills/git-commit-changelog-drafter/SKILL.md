---
name: git-commit-changelog-drafter
description: [交付/运维] Conventional Commit 起草与 Changelog 技能。分析 git diff 改动内容与物理模块范围，起草符合 Conventional Commits 规范的提交信息与 Changelog 摘要。
globs: "*"
---

# 📝 git-commit-changelog-drafter 技能 SOP (Conventional Commit 起草与 Changelog)

## 📌 触发机制

- **🤖 自动触发**：当任务开发与单元测试验证通过，准备提交 Git 时。
- **💬 显式触发**：当开发者输入 `git-commit-changelog-drafter`、`“起草 Commit 信息”` 或 `“生成 Changelog 摘要”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：分析物理变更文件与作用域 (Scope & Status)
1. 使用 PowerShell 查看受影响的文件及 Git 状态：
   ```powershell
   git status
   ```
2. 识别变动集中的子模块作用域 (Scope)，如：
   - `common-file` (`pap4j-common/pap4j-common-file`)
   - `common-json` (`pap4j-common/pap4j-common-json`)
   - `boot3-logback` (`pap4j-boot3-starters/pap4j-boot3-starters-logback`)
   - `example-devtools` (`pap4j-boot3-example/pap4j-boot3-example-devtools`)

### 步骤 2：起草 Conventional Commit 消息 (Commit Draft)
根据改动意图分类（`feat`, `fix`, `docs`, `refactor`, `test`, `chore` 等），起草符合规范的标准提交信息：

```text
<type>(<scope>): <short description>

[Optional Body]
- Detailed change list item 1
- Detailed change list item 2

[Optional Footer]
Fixes #IssueId / Ref #ProposalId
```

#### 示例：
```text
feat(common-file): add ZipCompressor utility method with unit tests

- Implemented ZipCompressor.compressFolder for recursive directory zipping
- Added ZipCompressorTest with 100% boundary scenario test coverage
- Updated .ai/utilities.md index table
```

### 步骤 3：起草 Changelog 摘要 (Changelog Generation)
自动生成可供追加至 `CHANGELOG.md` 的 Feature / Fix 变动摘要：

```markdown
## [Unreleased]

### 🚀 Features
- **common-file**: 新增 `ZipCompressor` 通用目录压缩工具类与单测。

### 🐞 Bug Fixes
- **boot3-logback**: 修复 Logback 异步 Appender 极值情况下的空指针问题。
```

---

## ⚡ 命令行与验证规范 (PowerShell Compliance)
- 所有 Git 状态分析均使用 PowerShell 原生指令：
  ```powershell
  git status
  ```
