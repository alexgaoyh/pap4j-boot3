## TERNARY — 安全三元运算函数

> **元数据**
> - **组件/引擎**：QLExpress
> - **分类/模块**：稳定性与安全
> - **类型**：自定义函数
> - **关键字**：三元, 条件, 防御性

### 💡 核心介绍
TERNARY 是 QLExpress 引擎中内置的三元表达式函数，用来代替直接编写 `?:` 三元运算符语法。在某些复杂的空值防御逻辑场景下，直接使用 `?:` 语法可能因引擎解析产生歧义或报错，使用 TERNARY 函数能提供更稳定可靠的替代方案。

### 📝 语法签名
`TERNARY(condition, trueValue, falseValue)`
- 第一个参数 condition 是布尔条件表达式。
- 第二个参数 trueValue 是条件为真时返回的值，可以是任意类型。
- 第三个参数 falseValue 是条件为假时返回的值，可以是任意类型。

### 💻 推荐代码示例

```ql
TERNARY(json.status, 'ACTIVE', 'INACTIVE')
```
这段脚本根据 status 字段的真假，分别返回字符串 'ACTIVE' 或 'INACTIVE'。逻辑等价于 `json.status ? 'ACTIVE' : 'INACTIVE'`，但语法更稳定。

### 🎯 适用场景
当你在脚本中编写 `?:` 三元运算符时遇到解析异常、语法报错或者行为不符合预期，可以直接将其替换为 TERNARY 函数调用，语义完全一致但兼容性更好。
