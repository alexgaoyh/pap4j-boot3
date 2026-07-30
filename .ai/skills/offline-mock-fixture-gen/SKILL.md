---
name: offline-mock-fixture-gen
description: [开发/测试] 离线 Mock 数据与测试基桩生成技能。结合 JSONAPIController 与 ReqResLoggerReplayTest，针对复杂 JSON/Query 参数或第三方 API 自动生成 Mock 数据基桩 JSON，在无需真实启动 Servlet 容器的情况下模拟各种场景。
globs: "**/src/test/**/*.json, **/filter/ReqResLoggerReplayTest.java"
---

# 🧪 offline-mock-fixture-gen 技能 SOP (离线 Mock 数据与测试基桩生成)

## 📌 触发机制

- **🤖 自动触发**：当需要测试复杂 JSON/Query 参数、第三方 API 依赖，或需在无真实 Servlet 容器/网络环境进行断网测试时。
- **💬 显式触发**：当开发者输入 `offline-mock-fixture-gen`、`“生成 Mock 测试基桩”` 或 `“生成离线报文快照”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：分析接口数据结构与边界 scenario
1. 明确目标 API 的 Request Body / Response Body 结构，识别关键字段与类型。
2. 规划 Mock 基桩数据涵盖的场景：
   - 正常成功场景 (200 OK + 标准 JSON)
   - 客户端参数异常 (400 Bad Request + 校验错误信息)
   - 服务端故障/降级 (500 Internal Error + 降级 Mock 响应)
   - 高延迟/超时模拟场景

### 步骤 2：生成 Mock JSON 基桩文件 (Fixture Generation)
在 `src/test/resources/mock/` 或 `logs/recorded-bugs/` 目录下生成标准 Mock JSON 报文文件：
```json
{
  "requestUri": "/api/v1/sample/query",
  "method": "POST",
  "headers": {
    "Content-Type": "application/json"
  },
  "requestBody": {
    "id": 1001,
    "queryKey": "test_mock"
  },
  "expectedStatus": 200,
  "responseBody": {
    "code": "20000",
    "message": "success",
    "data": { "status": "ACTIVE" }
  }
}
```

### 步骤 3：结合 MockMvc / ReqResLoggerReplayTest 回放测试 (Replay Integration)
编写或集成测试用例，结合 `ReqResLoggerReplayTest` (MockMvc 离线测试套件) 加载 Mock 报文基桩。

### 步骤 4：运行验证 (Verification)
使用 `.agent/agent-test.cmd` 运行 Mock 离线测试（Git Bash / PowerShell 通用）：
```bash
./.agent/agent-test.cmd "-Dtest=ReqResLoggerReplayTest"
```

---

## ⚡ 命令行与验证规范 (Git Bash / PowerShell Compliance)
- `.agent/` 目录下的 `.cmd` 脚本在 Git Bash 和 PowerShell 中均可执行：
  ```bash
  ./.agent/agent-test.cmd "-Dtest=ReqResLoggerReplayTest"
  ```

