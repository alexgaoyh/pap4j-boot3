---
name: architecture-review-doc
description: [需求/设计] 架构演进与技术债评估技能。对 pap4j-common 25+ 子模块及 pap4j-boot3-starters 依赖树进行静态分析，识别循环依赖、废弃 API 滥用与组件过度耦合，输出架构建议报告。
globs: "**/pom.xml, **/*.java"
---

# 🏗️ architecture-review-doc 技能 SOP (全盘架构演进与技术债评估)

## 📌 触发机制

- **🤖 自动触发**：当准备进行模块级重构、依赖版本升级或发现跨模块循环依赖风险时。
- **💬 显式触发**：当开发者输入 `architecture-review-doc`、`“评估架构技术债”` 或 `“进行模块架构审查”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：多模块依赖树静态分析 (Dependency Tree Scan)
1. 遍历扫描根目录及 `pap4j-common/*`、`pap4j-boot3-starters/*` 下的 `pom.xml`。
2. 识别子模块间的引用关系，排查是否存在循环依赖或不合理的下层依赖上层反向依赖。

### 步骤 2：废弃 API 与强阻断禁令扫描 (Code Debt Audit)
1. 检索项目中的 `@Deprecated` 标注及调用情况（按环境选择）：
   ```bash
   # Git Bash
   grep -rn "@Deprecated" --include="*.java" .
   ```
   ```powershell
   # PowerShell
   Get-ChildItem -Recurse -Filter "*.java" | Select-String "@Deprecated"
   ```
2. 检视 `guard.md` 中的架构红线违规：
   - 检查实体类中是否存在 `@OneToMany` / `@ManyToMany` 等 JPA 级联关联注解。
   - 检查 `pap4j-common` 是否泄漏具体业务逻辑或依赖 Spring Web/Boot 上层框架。

### 步骤 3：输出架构评估报告 (Architecture Report)
在对话中或指定位置生成 `ARCHITECTURE_REVIEW.md` 报告：
1. **模块依赖拓扑与健康度**
2. **技术债清单 (High / Medium / Low)**
3. **架构违规与演进提案**
4. **渐进式重构路线图**

---

## ⚡ 命令行与验证规范 (Git Bash / PowerShell Compliance)
- **Git Bash 环境**：使用 `grep -rn` 进行文本检索。
- **PowerShell 环境**：使用 `Get-ChildItem | Select-String` 原生命令。
- 双环境等价命令示例：
  ```bash
  # Git Bash
  grep -rn "Deprecated" pap4j-common/ --include="*.java"
  ```
  ```powershell
  # PowerShell
  Get-ChildItem -Path "pap4j-common" -Recurse -Include "*.java" | Select-String -Pattern "Deprecated"
  ```
