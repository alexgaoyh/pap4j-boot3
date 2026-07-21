---
name: refactor-scanner
description: [质量/重构] 适应度函数静态扫描与代码修缮技能。运行 RefactorScanner 巡检项目违规代码（如 System.out、未用 log、关联注解滥用），解析 refactor_todo.md 并执行闭环修复。
globs: "**/*.java, .ai/diagnostics/refactor_todo.md"
---

# 🔍 refactor-scanner 技能 SOP (适应度函数静态扫描与代码修缮)

## 📌 触发机制

- **🤖 自动触发**：当触发项目级静态代码规范巡检、提交前质量自检或大规模重构后。
- **💬 显式触发**：当开发者输入 `refactor-scanner`、`“运行静态规则扫描”` 或 `“对当前模块跑规则巡检”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：运行适应度函数静态扫描 (Execute Scanner)
使用 PowerShell 运行项目专属快捷巡检脚本：
```powershell
.\.agent\agent-scan.cmd
```

### 步骤 2：解析扫描待办清单 (Parse refactor_todo.md)
读取生成的 [.ai/diagnostics/refactor_todo.md](file:///D:/ideaprojects/pap4j-boot3/.ai/diagnostics/refactor_todo.md)，归类扫描出的违规项：
1. **控制台输出残留**：`System.out.println` / `e.printStackTrace()`。
2. **日志规范违规**：未使用 Slf4j 占位符、硬编码字符串拼接。
3. **Guardrails 红线违规**：JPA 级联注解使用、`pap4j-common` 公共 API 破坏性变动。
4. **废弃 API 或硬编码魔法值**。

### 步骤 3：外科手术式修复 (Surgical Refactoring)
按优先级逐项进行代码修复：
- 用 `@Slf4j` 替代 `System.out`。
- 移除禁止使用的注解与废弃方法调用。

### 步骤 4：再次扫描与单测闭环 (Re-Scan Verification)
1. 重新运行 `RefactorScanner` 确保违规项为 0。
2. 运行相关子模块单测验证功能未中断：
   ```powershell
   # 将 <AffectedModuleTest> 替换为受重构影响模块的实际单测类名
   .\.agent\agent-test.cmd "-Dtest=<AffectedModuleTest>"
   ```

---

## ⚡ 命令行与验证规范 (PowerShell Compliance)
- 所有扫描与测试命令均通过 PowerShell 原生脚本执行：
  ```powershell
  .\.agent\agent-test.cmd "-Dtest=cn.net.pap.example.devtools.RefactorScanner"
  ```
