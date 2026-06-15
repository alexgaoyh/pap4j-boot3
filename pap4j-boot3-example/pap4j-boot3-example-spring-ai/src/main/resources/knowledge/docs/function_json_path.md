## JSON_PATH — 脚本内深度过滤函数

> **元数据**
> - **组件/引擎**：QLExpress
> - **分类/模块**：集合/列表操作
> - **类型**：自定义函数
> - **关键字**：jsonpath, 过滤, 深度搜索, 数组查询



JSON_PATH 是 QLExpress 引擎中动态调用 JsonPath 引擎进行复杂数据过滤的函数。它允许在 QLExpress 脚本内部，对已经解析好的 json 对象使用 JsonPath 语法进行深度搜索、条件过滤和数组查询，是两种引擎能力的融合使用方式。关于引擎路由规则（什么情况下用 JsonPath 引擎、什么情况下用 QLExpress 引擎）详见 [engine_routing_strategy.md](engine_routing_strategy.md)。

**函数签名**：`JSON_PATH(root, path)`
- 第一个参数 root 是作为 JsonPath 查询根节点的对象，通常传入 `json`。
- 第二个参数 path 是 JsonPath 查询表达式字符串。

**使用示例**：
```ql
JSON_PATH(json, '$.logs[?(@.level == \'ERROR\')]')
```
这段脚本在 json 对象的 logs 数组中，过滤出所有 level 字段值为 'ERROR' 的日志条目，返回一个符合条件的列表。

**转义避坑指南（极其重要）**：
1. **在 QLExpress 规则脚本内**：JsonPath 表达式内如果需要使用单引号（通常用于字符串比较，如 `== 'ERROR'`），必须对单引号使用**单反斜杠**转义，写法为 `\'`。例如：`JSON_PATH(json, '$.logs[?(@.level == \'ERROR\')]')`。
2. **在 Java 代码中定义表达式**：由于 Java 宿主语言本身的转义规则，Java 字符串字面量中必须写为**双反斜杠** `\\'`，例如：`String express = "JSON_PATH(json, '$.logs[?(@.level == \\'ERROR\\')]')";`。
在配置界面录入或编写脚本本身时，只使用单个反斜杠 `\'`。
