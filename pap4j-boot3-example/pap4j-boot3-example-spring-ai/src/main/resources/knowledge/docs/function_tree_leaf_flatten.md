## TREE_LEAF_FLATTEN — 树形叶子节点提取函数

> **元数据**
> - **组件/引擎**：QLExpress
> - **分类/模块**：树形结构处理
> - **类型**：自定义函数
> - **关键字**：叶子节点, 树末端, 路径



TREE_LEAF_FLATTEN 是 QLExpress 引擎中递归提取树形数据结构中所有叶子节点路径的函数。它与 TREE_FLATTEN 类似，但有一个关键区别：它只返回叶子节点（即没有子节点的末端节点）的层级路径，不包含中间层级的父节点路径。

**函数签名**：`TREE_LEAF_FLATTEN(root, childrenKey, nameKey, sep)`
- 第一个参数 root 是树结构的根对象，通常是 json 中的某个字段。
- 第二个参数 childrenKey 是标识子节点列表的字段名，例如 `'children'`。
- 第三个参数 nameKey 是标识节点名称的字段名，例如 `'name'`。
- 第四个参数 sep 是层级之间的分隔符，例如 `'/'`。

**使用示例**：
```ql
TREE_LEAF_FLATTEN(json.category, 'children', 'name', '/')
```
这段脚本展开 category 树，只返回叶子节点的完整路径列表。例如同样的三级分类树，结果只会包含 `'电子产品/手机/智能手机'`，而不会包含 `'电子产品'` 或 `'电子产品/手机'` 这类中间节点路径。

**判断叶子节点的逻辑**：当一个节点的 childrenKey 字段不存在，或者其 children 列表为空时，该节点被认定为叶子节点，其路径才会被收集到结果中。如果需要包含所有节点（含中间层级）的路径，应使用 [TREE_FLATTEN](function_tree_flatten.md) 函数。
