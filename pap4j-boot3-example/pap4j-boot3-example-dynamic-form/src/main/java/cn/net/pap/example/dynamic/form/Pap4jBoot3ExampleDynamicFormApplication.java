package cn.net.pap.example.dynamic.form;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <p>
 * <b>Pap4j-Boot3 Dynamic Form (EAV Architecture)</b> 示例程序启动类。
 * </p>
 *
 * <p>
 * 该模块展示了如何基于 <b>Spring Boot 3.x</b> 和 <b>JPA</b> 实现一个高度通用的 <b>EAV (Entity-Attribute-Value)</b> 模型后端数据存储方案。
 * 它旨在解决在复杂多变的业务场景下，频繁修改数据库 Schema 或创建大量 Java 实体的痛点。
 * </p>
 *
 * <h3>核心功能特性：</h3>
 * <ul>
 *     <li><b>深度递归持久化</b>：支持无限层级的 JSON 嵌套结构解析与存储。</li>
 *     <li><b>结构化还原</b>：精准从扁平的 EAV 表结构中还原出原始的嵌套 JSON 结构。</li>
 *     <li><b>多类型存储优化</b>：根据数据类型自动路由至最优列（string, number, text, date）。</li>
 *     <li><b>级联生命周期管理</b>：实现主记录、属性及嵌套子记录的一站式增删改。</li>
 * </ul>
 *
 * <h3>设计模型：</h3>
 * <pre>
 * [ DynamicForm ] (元数据定义: JSON Schema)
 *        |
 *        v
 * [ DynamicRecord ] (主记录锚点)
 *        |
 *        +--- [ DynamicFieldValue ] (KV 属性)
 *        |
 *        +--- [ DynamicRelation ] (嵌套关系: ONE_TO_ONE / ONE_TO_MANY)
 * </pre>
 *
 * <p>
 * 本项目可完美对接前端动态表单引擎（如 Alibaba Formily），支持海量不同结构的业务表单共用同一套底座。
 * </p>
 *
 * @author
 */
@SpringBootApplication
public class Pap4jBoot3ExampleDynamicFormApplication {
    public static void main(String[] args) {
        SpringApplication.run(Pap4jBoot3ExampleDynamicFormApplication.class, args);
    }
}
