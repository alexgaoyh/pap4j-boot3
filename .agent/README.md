# 🛠️ `.agent` AI Agent 专属工具与可执行脚本库

本目录是 `pap4j-boot3` 项目专为 AI Agent 及开发者终端设计的**可执行工具与 CI / 诊断辅助脚本集**。

---

## 📂 常用快捷脚本清单 (Executable Tool Suite)

| 快捷脚本 (Windows CMD) | 快捷脚本 (Linux Bash) | 关联底层 Executable Action / Class | 核心用途 |
| :--- | :--- | :--- | :--- |
| **`.\.agent\agent-test.cmd`** | `agent-test.sh` | Maven `-Pagent` Profile | AI 通用单元测试/构建执行器（带 `file.encoding=UTF-8` 规约） |
| **`.\.agent\agent-scan.cmd`** | `agent-scan.sh` | `RefactorScanner.java` | 一键触发适应度函数静态规则巡检（扫描 `System.out` / 日志与注解违规） |
| **`.\.agent\agent-export-openapi.cmd`** | `agent-export-openapi.sh` | `ApiRouterCatalogExporterTest.java` | 一键导出最新 OpenAPI Catalog JSON 快照并核对 Swagger 契约 |
| **`.\.agent\agent-diagnose.cmd`** | `agent-diagnose.sh` | `DiagnosticsExtractor.java` | 一键提炼测试失败堆栈至 `.ai/diagnostics/test_failures.md` |

---

## ⚡ PowerShell 使用规范
在 Windows 10/11 环境下，必须遵循 `AI.md` 思想钢印，使用分号隔离并带双引号传参：
```powershell
.\.agent\agent-scan.cmd
.\.agent\agent-export-openapi.cmd
.\.agent\agent-diagnose.cmd
.\.agent\agent-test.cmd "-Dtest=YourTestClass"
```
