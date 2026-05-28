# QLExpress 扩展函数说明: extractJsonPath

**函数名称**: extractJsonPath

**用途**: 根据传入的 JSON 字符串和 JSON-Path 表达式，提取目标值并统一返回字符串。如果是数组则以逗号分隔。

**Java 侧参数签名**: String extractJsonPath(String jsonContent, String jsonPath)

**脚本编写范例**:

jsonStr = '{"user": {"roles": ["admin", "editor"]}}';
result = extractJsonPath(jsonStr, "$.user.roles[*]");
return result; // 预期返回 "admin,editor"