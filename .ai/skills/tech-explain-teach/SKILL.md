---
name: tech-explain-teach
description: [架构/教学] 架构带读与技术原理交互式教学技能。当开发者需要理解复杂的 pap4j-common 底层工具类、并发锁机制、Spring Boot 3 Starter 自动化配置或核心重构逻辑时，AI 切换为架构导师人格，进行分步图解、类图推演与源码带读。
globs: "**/*.java, docs/**/*.md"
---

# 🎓 tech-explain-teach 技能 SOP (架构带读与技术原理交互式教学)

## 📌 触发机制

- **🤖 自动触发**：当开发者提出“解释该方法原理”、“带读此源码”、“分析并发锁实现逻辑”时。
- **💬 显式触发**：当开发者输入 `tech-explain-teach`、`“带读源码”` 或 `“技术原理讲解”` 时。

---

## 🛠️ 执行流程 (Execution Steps)

### 步骤 1：目标代码与物理路径定位 (Target Discovery)
1. 确定待带读的 Java 类或接口物理路径（例如 [ReqResLoggerHttpFilter](../../../pap4j-boot3-starters/pap4j-boot3-starters-logback/src/main/java/cn/net/pap/logback/filter/ReqResLoggerHttpFilter.java)）。
2. 核查涉及的 JDK 17 / Spring Boot 3 核心机制（如 Reactive 直通、OOM 内存防爆、底层并发锁、字节切片等）。

### 步骤 2：架构导师交互式带读 (Architect Tutor Delivery)
按照以下四层结构向开发者讲解：

1. **核心设计意图 (Design Goal)**：用 2~3 句话说明该类解决的核心工程痛点。
2. **Mermaid 结构/流程图 (Architecture Visual)**：绘制流程图或类图说明组件间数据流向。
3. **源码关键行带读 (Line-by-Line Highlights)**：
   - 标注具体代码行（如 `ReqResLoggerHttpFilter.java#L80-L105`）。
   - 解释关键防错设计（如为什么使用原子操作而非同步锁、如何实现 OOM 内存裁剪）。
4. **守卫规约对齐 (Guard Alignment)**：结合 `guard.md` 说明该设计如何规避内存泄漏与并发竞态。

---

## ⚡ 命令行与验证规范 (PowerShell Compliance)
- 讲解过程中涉及的代码检索必须使用 PowerShell 命令：
  ```powershell
  # 将 <TargetClassName> 替换为待讲解的类名
  Get-ChildItem -Path "pap4j-common" -Recurse -Filter "<TargetClassName>.java"
  ```
