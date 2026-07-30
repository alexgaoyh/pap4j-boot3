---
name: api-contract-sync
description: [开发/编码] OpenAPI 契约刷新与 Swagger 校验技能。当新增或修改 Controller / REST 接口时，运行 ApiRouterCatalogExporterTest 导出 OpenAPI 标准 JSON 快照，核对 Swagger 注解与契约规范。
globs: "**/*Controller.java, **/openapi/*.json"
---

# 🗺️ api-contract-sync 技能 SOP (OpenAPI 契约导出与 Swagger 校验)

## 📌 触发机制

- **🤖 自动触发**：当新增或修改任何 Controller 类、REST 接口定义或 DTO 参数类时。
- **💬 显式触发**：当开发者输入 `api-contract-sync`、`“导出 OpenAPI 契约”` 或 `“同步 Swagger 接口说明”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：Swagger / OpenAPI 注解规范自检 (Annotation Audit)
1. 检查修改的 Controller 方法是否配齐 Swagger 注解：
   - 类级别：`@Tag(name = "...", description = "...")`
   - 方法级别：`@Operation(summary = "...", description = "...")`
   - 参数级别：`@Parameter(description = "...", required = true)`
2. 确保 REST API 响应对象具备清晰的字段说明与类型。

### 步骤 2：触发契约导出测试 (Export OpenAPI JSON)
运行项目专属快捷脚本导出最新 OpenAPI 标准快照（Git Bash / PowerShell 通用）：
```bash
./.agent/agent-export-openapi.cmd
```

### 步骤 3：核对契约快照与 Diff 变更 (Contract Verification)
1. 检查 `.ai/openapi/` 或相关输出路径下的 OpenAPI 契约 JSON 快照文件。
2. 核对新增或修改的接口路径、请求方式、 Query/Body 参数及返回码。

### 步骤 4：报告契约变更摘要 (Sync Summary)
向开发者汇报 OpenAPI 契约的同步结果及变化点。

---

## ⚡ 命令行与验证规范 (Git Bash / PowerShell Compliance)
- `.agent/` 目录下的 `.cmd` 脚本在 Git Bash 和 PowerShell 中均可执行：
  ```bash
  ./.agent/agent-test.cmd "-Dtest=cn.net.pap.example.proguard.diagnostics.ApiRouterCatalogExporterTest"
  ```

