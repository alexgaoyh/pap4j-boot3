# Pap4j-Boot3 Dynamic Form (EAV Architecture)

## 项目简介
`pap4j-boot3-example-dynamic-form` 是基于 **Spring Boot 3.x** 和 **JPA** 实现的一个高度通用的后端数据存储方案。它采用 **EAV (Entity-Attribute-Value)** 模型，旨在解决复杂多变的业务场景下，频繁修改数据库 Schema 或创建大量 Java 实体的痛点。

本项目可以完美对接如 **Alibaba Formily** 等动态表单前端引擎，支持 100+ 不同结构的业务表单共用同一套底座。

## 核心设计思想：EAV 模型
EAV 模型的精髓在于将数据的“结构”与“内容”解耦：
- **Entity (实体)**: 对应 `dynamic_record`，作为数据锚点。
- **Attribute (属性)**: 对应 JSON Schema 中的 Key。
- **Value (值)**: 对应 `dynamic_field_value`，存储具体的属性内容。

### 架构图示
```text
[ DynamicForm ] (元数据定义: JSON Schema)
       |
       v
[ DynamicRecord ] (主记录锚点)
       |
       +--- [ DynamicFieldValue ] (KV 属性: 自动路由 string/num/text/date)
       |
       +--- [ DynamicRelation ] (嵌套关系: ONE_TO_ONE / ONE_TO_MANY)
```

## 技术亮点
1. **深度递归持久化**: 支持无限层级的 JSON 嵌套结构。Service 层会自动解析 payload，递归生成子记录并建立 `DynamicRelation`。
2. **结构化还原**: 从扁平的 EAV 表结构中，精准还原出原始的嵌套 JSON (LinkedHashMap) 结构，保证数据读写一致性。
3. **多类型存储优化**: `DynamicFieldValue` 预置了多种类型列（short text, long text, double, datetime），系统会根据 Java 类型自动选择最优列进行存储，平衡灵活性与查询性能。
4. **级联生命周期管理**: 利用 JPA 的 `CascadeType.ALL` 和 `orphanRemoval`，实现主记录、属性、嵌套子记录的“一站式”增删改。
5. **Swagger UI 集成**: 全量支持 OpenAPI 3.0，通过交互式文档即可调试复杂的动态数据接口。

## 核心模块说明
- **Entity**:
  - `DynamicForm`: 存储表单定义（如 `formCode`, `schemaDefinition`）。
  - `DynamicRecord`: 数据主体，关联 `formCode`。
  - `DynamicFieldValue`: 属性值存储，支持长文本 (`@Lob`)。
  - `DynamicRelation`: 维护记录间的父子/嵌套关系。
- **Service**:
  - `DynamicRecordService`: 提供 `saveComplexRecord` (递归存) 和 `reconstructRecord` (递归取) 核心逻辑。
- **Web**:
  - `GenericCrudController`: 暴露 `/api/generic/{formCode}` 统一接口。

## 快速开始
### 1. 提交动态数据
**Endpoint**: `POST /api/generic/order/save`
**Payload**:
```json
{
  "orderNo": "ORD-2026-001",
  "customer": "Alice",
  "items": [
    { "name": "MacBook", "price": 15000 },
    { "name": "Mouse", "price": 300 }
  ]
}
```

### 2. 获取数据
**Endpoint**: `GET /api/generic/order/{id}`
**Response**: 将原样返回上述嵌套结构的 JSON。

## 单元测试
本项目包含完整的集成测试 `DynamicFormApplicationTests`，涵盖了：
- 复杂嵌套结构的保存与重构验证。
- 级联删除的安全验证。
- 类型路由的准确性验证。

---
*Powered by Pap4j-Boot3 Framework.*
