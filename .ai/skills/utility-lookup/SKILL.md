---
name: utility-lookup
description: [开发/编码] pap4j-common 工具库检索与防重技能。准备在 pap4j-common 新增工具类或通用算法时，先在 25+ 通用子模块及 utilities.md 中检索防重；编写完成后引导补全单测并更新 utilities.md。
globs: "pap4j-common/**/*.java, .ai/utilities.md"
---

# 🧰 utility-lookup 技能 SOP (pap4j-common 工具检索与防重)

## 📌 触发机制

- **🤖 自动触发**：当准备在 `pap4j-common` 编写新工具类、新公共静态方法或通用算法时。
- **💬 显式触发**：当开发者输入 `utility-lookup`、`“查找通用工具类”` 或 `“在 pap4j-common 新增工具类”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：检索现有工具库索引与防重 (Anti-Duplication Check)
1. 优先检索 [.ai/utilities.md](../../utilities.md) 中的分类工具表（涵盖 `pap4j-common-file`, `pap4j-common-json`, `pap4j-common-crypto` 等 25+ 子模块）。
2. 使用 PowerShell 工具进行关键词模糊与精确搜索：
   ```powershell
   # 将 <TargetMethodName> 替换为准备编写的目标工具方法名或检索关键词
   Get-ChildItem -Path "pap4j-common" -Recurse -Filter "*.java" | Select-String "<TargetMethodName>"
   ```
3. **判定结果**：
   - 若已存在类似工具方法 ➔ 直接复用或进行扩展，防止重复造轮子。
   - 若确认不存在 ➔ 明确应归属的具体 `pap4j-common-*` 子模块。

### 步骤 2：遵规编写与 100% 边界单测 (Implementation & Unit Test)
1. 编写工具类：严格遵守 JDK 17 语法，公共 API 方法必须有清晰的 Javadoc 说明（复杂工具类推荐结合 [tdd-workflow](../tdd-workflow/SKILL.md) 采用红-绿-重构顺序驱动编写）。
2. 为新工具方法编写 100% 覆盖率的单元测试，包含 NULL 值、空字符串、极端边界等情况。

### 步骤 3：PowerShell 单测验证 (Verification)
使用 `.agent/agent-test.cmd` 验证新增工具单测：
```powershell
# 将 <YourNewUtilityTest> 替换为新建工具类的实际单测类名
.\.agent\agent-test.cmd "-Dtest=<YourNewUtilityTest>"
```

### 步骤 4：同步更新 utilities.md 索引 (Index Maintenance)
单测通过后，主动将新工具类及其方法简要说明同步追加至 [.ai/utilities.md](../../utilities.md)。

---

## ⚡ 命令行与验证规范 (PowerShell Compliance)
- 必须使用 PowerShell 命令进行搜索与测试。
- 测试命令参数必须包裹双引号 `"-Dtest=..."`。
