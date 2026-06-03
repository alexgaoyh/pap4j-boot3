# 项目 AI 协同总纲 (Universal AI Instructions)

欢迎。本文件是 `pap4j-boot3` 项目的通用 AI 指令入口。

## 🛠 功能原型协议 (Archetype Protocol)

为了确保跨平台的精确执行，本指令系统使用方括号标记的功能原型来请求 AI 调用其环境下的对应工具：

*   **`[Search]`**: 调用代码搜索、路径匹配或文件列表工具，用于探索上下文。
*   **`[Plan]`**: 开启深度思考或设计规划模式，在修改前输出详细的设计逻辑。
*   **`[Edit]`**: 执行代码修改、重写或外科手术式编辑操作。
*   **`[Shell]`**: **【当前环境：Windows 操作系统】** 在 **PowerShell 终端**中执行命令（如 Maven 编译、测试、Git 操作等）。
    *   *环境绝对约束*：严禁输出 Linux 独有命令（如 `ls -la`, `cat`, `rm -rf`, `export`）。
    *   *等价替换原则*：必须直接采用 PowerShell 兼容语法或内建 Cmdlet（如 `Get-ChildItem`, `Get-Content`, `Remove-Item`, `$env:VAR=value`）。

---

## 🧩 模块化指令集

1.  **[技术守卫 (.ai/guard.md)]**: 
    *   定义了项目的技术栈（JDK 17/SB3）、Windows/PowerShell 环境红线、代码红线、并发禁令及反模式示例。
2.  **[开发工作流 (.ai/workflow.md)]**:
    *   定义了 RSE 操作周期（结合功能原型标签）、PowerShell 自动化验证标准及链式执行协议。
3.  **[代理角色 (.ai/agents.md)]**:
    *   定义了在不同场景下应采取的专业人格及其对应的原型行为。

---

## 🎯 核心原则 (Core Tenets)
- **环境合规性**: 必须时刻铭记当前处于 Windows 环境，所有的 `[Shell]` 指令必须原生支持 PowerShell。
- **合规性**: 必须遵守 `guard.md` 中的所有禁令，严禁“自作聪明”。
- **验证性**: 任何修改 **`[Edit]`** 必须通过终端 **`[Shell]`** 进行验证。
- **透明度**: 修改前先沟通策略 **`[Plan]`**，修改后汇报结果。
