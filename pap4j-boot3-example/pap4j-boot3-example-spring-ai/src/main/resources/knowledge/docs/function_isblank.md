## ISBLANK — 安全判空函数

> **元数据**
> - **组件/引擎**：QLExpress
> - **分类/模块**：稳定性与安全
> - **类型**：自定义函数
> - **关键字**：判空, blank, null检查, 默认值

### 💡 核心介绍
ISBLANK 是 QLExpress 引擎中用于安全检查字符串是否为空的函数。它可以同时检测两种情况：字符串是否为 null，以及字符串是否仅包含空白字符（如空格、制表符）。只要满足其中任意一种情况，ISBLANK 就会返回 true，代表字符串"为空"。

### 📝 语法签名
`ISBLANK(input)`
- 参数 input 是待检查的字符串。

### 💻 推荐代码示例

```ql
ISBLANK(json.email) ? 'NONE' : json.email
```
这段脚本检查邮箱字段是否为空，如果为空则返回字符串 'NONE'，否则返回邮箱的真实值。这是一种典型的防御性编程写法。

### ⚠️ 避坑指南与常见错误
严禁使用 `str == ''` 或 `str == null` 这类写法来判断字符串是否为空。`str == ''` 无法处理 null 值，`str == null` 无法处理空字符串和空白字符串，两种写法都存在漏洞。必须统一使用 ISBLANK 函数，它能同时兼容 null、空字符串、纯空格字符串三种场景，是最安全的判空方式。

**配合使用**：ISBLANK 常用于对其他函数的结果做前置安全校验——例如在调用 [UPPER](function_upper.md) 或 [SUBSTRING](function_substring.md) 之前，先用 ISBLANK 确认字符串非空，避免传入 null 导致运行时异常。
