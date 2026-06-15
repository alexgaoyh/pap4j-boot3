# QLExpress 表达式引擎知识库索引

> 本知识库包含 QLExpress/JsonPath 混合引擎的规则脚本开发知识，按功能拆分为独立的文档。
> 在向量检索时，建议将此索引文件与具体函数文档一并召回，以便模型建立全局上下文。

---

## 一、运算符（Operators）

| 文件 | 说明 | 关联文档 |
|------|------|----------|
| [arithmetic_operators.md](arithmetic_operators.md) | 四则运算（`+`, `-`, `*`, `/`）及优先级 | ➡ [function_div2.md](function_div2.md) |

---

## 二、函数（Functions）

### 2.1 算术运算类

| 文件 | 说明 | 关联文档 |
|------|------|----------|
| [function_div2.md](function_div2.md) | 高精度舍入除法，替代 `/` 运算符 | ➡ [arithmetic_operators.md](arithmetic_operators.md) |

### 2.2 字符串处理类

| 文件 | 说明 | 关联文档 |
|------|------|----------|
| [function_trim.md](function_trim.md) | 去除字符串首尾空白 | |
| [function_upper.md](function_upper.md) | 字符串转大写 | ➡ [function_isblank.md](function_isblank.md) |
| [function_substring.md](function_substring.md) | 按索引截取子串，用于脱敏 | ➡ [function_isblank.md](function_isblank.md) |
| [function_isblank.md](function_isblank.md) | 安全判空（null / 空串 / 空白串） | ➡ [function_upper.md](function_upper.md)、[function_substring.md](function_substring.md) |

### 2.3 集合/列表操作类

| 文件 | 说明 | 关联文档 |
|------|------|----------|
| [function_list_size.md](function_list_size.md) | 安全获取集合长度 | |
| [function_list_join.md](function_list_join.md) | 集合元素拼接为字符串 | |
| [function_json_path.md](function_json_path.md) | QLExpress 内部调用 JsonPath 引擎 | ➡ [engine_routing_strategy.md](engine_routing_strategy.md) |

### 2.4 树形结构处理类

| 文件 | 说明 | 关联文档 |
|------|------|----------|
| [function_tree_flatten.md](function_tree_flatten.md) | 展开树的所有节点（含中间节点） | ➡ [function_tree_leaf_flatten.md](function_tree_leaf_flatten.md) |
| [function_tree_leaf_flatten.md](function_tree_leaf_flatten.md) | 只提取树的叶子节点路径 | ➡ [function_tree_flatten.md](function_tree_flatten.md) |

### 2.5 流程控制类

| 文件 | 说明 | 关联文档 |
|------|------|----------|
| [function_ternary.md](function_ternary.md) | 安全三元运算，替代 `?:` 语法 | |

---

## 三、核心语法（Syntax）

| 文件 | 说明 | 关联文档 |
|------|------|----------|
| [syntax_map_construction.md](syntax_map_construction.md) | 脚本中构建嵌套 Map 对象 | |
| [syntax_interpolation.md](syntax_interpolation.md) | `${varName}` 动态变量插值 | |
| [engine_routing_strategy.md](engine_routing_strategy.md) | `$` 前缀路由到 JsonPath 引擎 | ➡ [function_json_path.md](function_json_path.md) |
| [context_variables.md](context_variables.md) | 预置变量 `json`、`data` 的使用 | |

---

## 四、开发调试工具（Dev Tools）

| 文件 | 说明 | 关联文档 |
|------|------|----------|
| [devutil_syntax_check.md](devutil_syntax_check.md) | 语法静态校验方法 `runner.check()` | |
| [devutil_trace_execution.md](devutil_trace_execution.md) | 执行链路追踪 `traceExpression` | |

---

## 类别总览

```
knowledge/docs/
├── _index.md                          ← 本索引文件
├── arithmetic_operators.md            # 运算符
├── function_div2.md                   # 函数 - 算术
├── function_isblank.md                # 函数 - 字符串/安全
├── function_ternary.md                # 函数 - 流程控制
├── function_trim.md                   # 函数 - 字符串
├── function_upper.md                  # 函数 - 字符串
├── function_substring.md              # 函数 - 字符串
├── function_list_size.md              # 函数 - 集合
├── function_list_join.md              # 函数 - 集合
├── function_json_path.md              # 函数 - 跨引擎
├── function_tree_flatten.md           # 函数 - 树形
├── function_tree_leaf_flatten.md      # 函数 - 树形
├── syntax_map_construction.md         # 语法 - Map
├── syntax_interpolation.md            # 语法 - 插值
├── engine_routing_strategy.md         # 语法 - 引擎路由
├── context_variables.md               # 语法 - 上下文
├── devutil_syntax_check.md            # 调试 - 语法校验
└── devutil_trace_execution.md         # 调试 - 执行追踪
```

---

## 使用建议

- **对于 LLM 推理**：当用户的问题涉及多个概念时（如"对树形分类脱敏后转大写"），请同时检索 `_index.md` 以了解知识库全貌，再根据索引中标注的关联关系逐一查找相关函数文档。
- **对于开发者**：新增功能时，请在本索引末追加条目，并在对应的关联文档中互加 "详见" 链接。
