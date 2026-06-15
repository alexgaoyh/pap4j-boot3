## TREE_FLATTEN — 树形结构全节点展开函数

> **元数据**
> - **组件/引擎**：QLExpress
> - **分类/模块**：树形结构处理
> - **类型**：自定义函数
> - **关键字**：树展开, 层级路径, flatten

### 💡 核心介绍
TREE_FLATTEN 是 QLExpress 引擎中递归展开树形数据结构的函数。它会从根节点开始，递归地遍历整棵树的所有节点，包括中间节点和叶子节点，并将每个节点从根到当前位置的完整层级路径拼接为字符串，最终返回所有节点路径组成的列表。

### 📝 语法签名
`TREE_FLATTEN(root, childrenKey, nameKey, sep)`
- 第一个参数 root 是树结构的根对象，通常是 json 中的某个字段。
- 第二个参数 childrenKey 是标识子节点列表的字段名，例如 `'children'`。
- 第三个参数 nameKey 是标识节点名称的字段名，例如 `'name'`。
- 第四个参数 sep 是层级之间的分隔符，例如 `' > '`。

### 💻 推荐代码示例

```ql
TREE_FLATTEN(json.category, 'children', 'name', ' > ')
```
这段脚本展开 category 树，返回包含所有节点（包括中间分类）路径的列表。例如一个三级分类树会返回类似 `['电子产品', '电子产品 > 手机', '电子产品 > 手机 > 智能手机']` 的路径列表。

### ⚙️ 与 TREE_LEAF_FLATTEN 的区别
TREE_FLATTEN 会包含所有中间节点的路径，而 [TREE_LEAF_FLATTEN](function_tree_leaf_flatten.md) 只返回叶子节点（没有子节点的末端节点）的路径。如果只需要最终分类而不需要中间层级，应使用 TREE_LEAF_FLATTEN。
