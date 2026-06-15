## LIST_SIZE — 列表长度统计函数

> **元数据**
> - **组件/引擎**：QLExpress
> - **分类/模块**：集合/列表操作
> - **类型**：自定义函数
> - **关键字**：size, 长度, count, 数组



LIST_SIZE 是 QLExpress 引擎中安全获取集合或列表大小的函数。与直接调用 Java 的 `.size()` 方法不同，LIST_SIZE 内置了 null 安全保护：当传入的列表为 null 时，它不会抛出异常，而是直接返回 0，保证脚本的健壮性。

**函数签名**：`LIST_SIZE(list)`
- 参数 list 是待统计的集合或列表。

**使用示例**：
```ql
LIST_SIZE(json.items) > 0
```
这段脚本判断 items 列表是否非空（即是否包含至少一个元素），返回 true 或 false。可以用作条件判断的前置检查。

**常见错误**：严禁直接使用 Java 风格的 `list.size()` 方法调用，因为当 list 为 null 时，`.size()` 会抛出 NullPointerException 空指针异常，导致整个脚本执行失败。必须统一使用 LIST_SIZE 函数，它能在集合为 null 时安全地返回 0 而不是抛出异常。
