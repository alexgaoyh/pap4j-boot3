---
name: smart-diagnose-heal
description: [测试/诊断] 失败日志精炼与 MockMvc 回放自愈技能。当编译或单测失败时，自动提取 DiagnosticsExtractor 堆栈，结合 recorded-bugs 与 ReqResLoggerReplayTest (MockMvc) 进行零容器回放与自愈修复（上限 2 次）。
globs: ".ai/diagnostics/*.md, logs/recorded-bugs/*.json"
---

# 🩹 smart-diagnose-heal 技能 SOP (单测诊断精炼与 Mock 回放自愈)

## 📌 触发机制

- **🤖 自动触发**：当本地 Maven 编译、构建或单元测试运行失败，或检测到 `.ai/diagnostics/test_failures.md` 存在错误日志时。
- **💬 显式触发**：当开发者输入 `smart-diagnose-heal`、`“诊断单测失败”` 或 `“修复测试报错日志”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：错误堆栈提取与精练 (Extract Failures)
1. 运行项目专属快捷诊断脚本提炼堆栈至 [.ai/diagnostics/test_failures.md](file:///D:/ideaprojects/pap4j-boot3/.ai/diagnostics/test_failures.md)，滤除冗余日志，精准定位核心 Exception 堆栈与失败代码行：
   ```powershell
   .\.agent\agent-diagnose.cmd
   ```
2. 若属于 HTTP 接口类异常，检索 `logs/recorded-bugs/bug_*.json` 捕获的异常快照报文。

### 步骤 2：零容器 MockMvc 回放复现 (Offline Replay)
利用 `ReqResLoggerReplayTest`，在不启动真实 Servlet 容器的情况下，载入异常快照并进行 MockMvc 回放，精准复现 Bug 场景。

### 步骤 3：根本原因分析与外科手术式修复 (Root Cause Analysis & Fix)
1. 对照 `guard.md` 自检：是否存在空指针、类型转换异常、死锁或并发竞态条件。
2. 进行目标代码的外科手术式修复 `[Edit]`。

### 步骤 4：闭环重新验证与重试控制 (Retry Policy)
1. 使用 PowerShell 命令重新运行失败的单测：
   ```powershell
   # 1. 将 <FailingTestClass> 替换为实际失败的单测类名
   # 2. 若涉及 HTTP 接口 Mock 回放，可直接使用项目通用回放类 ReqResLoggerReplayTest
   .\.agent\agent-test.cmd "-Dtest=<FailingTestClass>"
   ```
2. **重试上限限制**：自动修复尝试上限为 **2 次**。若 2 次修复后测试仍然失败，暂停自动修复并向开发者汇报诊断细节。

---

## ⚡ 命令行与验证规范 (PowerShell Compliance)
- 强制遵守 2 次重试上限。
- 所有命令原生支持 PowerShell 语法：
  ```powershell
  .\.agent\agent-test.cmd "-Dtest=ReqResLoggerReplayTest"
  ```
