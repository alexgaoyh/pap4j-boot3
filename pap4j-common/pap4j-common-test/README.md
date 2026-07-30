# 🧪 pap4j-common-test

`pap4j-common-test` 是专为 AI 代理设计的单元测试故障诊断与收集模块。

## 🎯 核心功能

*   **SPI 自动装配**: 基于 JUnit 5 Platform SPI (`TestExecutionListener`) 实现。只要引入依赖，单测运行失败时便会自动拦截并提取精炼堆栈（8 行以内）。
*   **诊断输出**: 诊断信息自动输出至项目根目录下的 `.ai/diagnostics/test_failures.md`（每轮测试开始前会自动清空）。
*   **AI 降噪定位**: 避免 AI 代理在冗长、易乱码的 Maven 控制台日志中检索错误，极大节省上下文 Token 并加速故障自修复。

## 📦 引入与使用

在需要测试增强的子模块中，引入以下依赖：

```xml
<dependency>
    <groupId>cn.net.pap</groupId>
    <artifactId>pap4j-common-test</artifactId>
    <version>${revision}</version>
    <scope>test</scope>
</dependency>
```

### 💡 运行建议

推荐配合项目内置的测试脚本或 `-Pagent` 参数运行测试，以跳过 Checkstyle / Git Commit Id 等非必要校验并指定 UTF-8 编码：
*   **PowerShell**: `./.agent/agent-test.cmd -pl <module-name> test`
