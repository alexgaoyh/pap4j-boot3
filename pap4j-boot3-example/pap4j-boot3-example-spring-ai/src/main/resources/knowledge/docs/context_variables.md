## 脚本上下文变量（Context Variables）

> **元数据**
> - **组件/引擎**：QLExpress
> - **分类/模块**：核心语法
> - **类型**：infrastructure
> - **关键字**：json变量, data变量, 上下文



在 QLExpress 引擎的执行环境中，系统会自动注入两个预置变量，脚本可以直接使用它们，无需额外声明。

第一个变量是 `json`，它代表已经解析好的数据对象，类型为 Map 或 List，是访问数据的推荐方式。例如，要访问用户姓名，直接写 `json.user.name` 即可。

第二个变量是 `data`，它代表原始的 JSON 字符串，通常不需要直接使用，只在需要原始字符串内容时才会用到。

**推荐写法**：
```ql
json.user.name
```
这段脚本访问 json 对象中 user 嵌套对象下的 name 字段。

**严重错误**：在 QLExpress 脚本中，如果需要用 return 关键字返回 json 数据，必须在 `return` 和 `json` 之间保留一个空格，正确写法为 `return json.user.name`。严禁将两个词粘连拼写为 `returnjson.user.name`，那是一个不存在的变量名，会导致运行时报错找不到变量。
