# pap4j-boot3

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://gitee.com/alexgaoyh/pap4j-boot3/raw/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/cn.net.pap/pap4j-boot3.svg)](https://central.sonatype.com/namespace/cn.net.pap)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M6-blue.svg)](https://spring.io/projects/spring-ai)

#### 介绍
&ensp;&ensp; **pap4j-boot3** 是一个基于 **Java 17+** 和 **Spring Boot 3.5.x** 构建的工业级全栈工程脚手架。它通过高度模块化的设计，将企业级开发中所需的各种中间件、底层算法及业务最佳实践进行了深度整合。无论是构建高性能大数据分析系统，还是现代化的 AI 驱动应用，**pap4j-boot3** 都能提供开箱即用的基础设施。

---

#### 🏗️ 项目架构愿景

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                          pap4j-boot3-example                             │
│       (AI 对话、实时数仓、混淆打包、测试容器、桌面端、微信集成等最佳实践)        │
└─────────────────────────────────────┬────────────────────────────────────┘
                                      ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          pap4j-boot3-starters                            │
│       (状态机、规则引擎、向量库、图数据库、分布式任务、缓存增强等核心组件)       │
└─────────────────────────────────────┬────────────────────────────────────┘
                                      ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          pap4j-common                                    │
│       (高性能位图、计算机视觉、语义网、流式计算、文档处理、表达式引擎等算法库)    │
└──────────────────────────────────────────────────────────────────────────┘
```

---

#### 🔥 核心特性亮点

*   **🤖 AI 2.0 赋能 (Spring AI + RAG)**
    - 针对流式对话优化，支持 **POST 协议 SSE** 输出，完美适配 Markdown 渲染与代码一键复制。**ChatId 记忆机制**，轻松实现多轮对话上下文关联。

*   **⚡ 极致性能优化 (Bitmap & DataSketches)**
    - 集成 **RoaringBitmap**，在内存中极速处理亿级数据的画像筛选与交并集统计。
    - 引入 **Apache DataSketches** 算法，支撑在海量数据流中进行超低内存占用的基数估算（Count-Min Sketch）。

*   **🧩 动态业务编排 (LiteFlow & Drools)**
    - 支持通过 XML/JSON 或 **QLExpress** 脚本动态编排业务链路，实现业务逻辑与代码的彻底解耦。
    - 预置 **Spring Statemachine** 状态机模型，标准化订单、流程等复杂状态转移逻辑。

---

#### 🛠️ 核心技术栈清单

| 领域 | 核心组件 | 版本 | 描述 |
| :--- | :--- | :--- | :--- |
| **基础框架** | Spring Boot / WebFlux | 3.5.x | Jakarta EE 规范，全面适配虚拟线程 |
| **人工智能** | Spring AI / Ollama / Milvus | 1.0.0-M6 | 大模型集成、向量检索与 RAG 方案 |
| **规则引擎** | Drools / LiteFlow / QLExpress | 10.1.0+ | 复杂业务逻辑解耦与动态流程编排 |
| **图数据库** | Neo4j / gStore (国产) | 最新版 | 关联关系挖掘与 RDF 知识图谱存储 |
| **数据处理** | Apache Doris / RoaringBitmap | 最新版 | 实时列式存储数仓与高性能位图索引 |
| **视觉识别** | OpenCV / BoofCV / Tesseract | 1.2.4+ | 图像特征提取与工业级 OCR 文字识别 |
| **文档套件** | EasyExcel / POI / PDFBox / iText 7 | 4.0.3+ | 解决大数据量 Office 读写 OOM 问题 |
| **质量安全** | Testcontainers / Dependency-Check | 最新版 | 容器化集成测试与依赖漏洞自动扫描 |

---

#### 📂 模块详细索引 (Total 45+ Modules)

<details>
<summary><b>点击展开查看【自定义组件库 (pap4j-boot3-starters)】</b></summary>

```text
├─pap4j-boot3-starter-statemachine               # Spring Statemachine 状态机集成
├─pap4j-boot3-starters-cache                     # Redis Cache 自定义注解增强（支持缓存自动失效与刷新）
├─pap4j-boot3-starters-drools                    # Drools 业务规则引擎集成（支持动态规则加载）
├─pap4j-boot3-starters-gStore                    # 国产 gStore 图数据库集成驱动
├─pap4j-boot3-starters-liteflow                  # LiteFlow 组件式规则引擎集成（流程编排利器）
├─pap4j-boot3-starters-logback                   # Logback 日志链路扩展（支持自定义日志格式与增强）
├─pap4j-boot3-starters-milvus                    # Milvus 向量数据库集成（AI 向量检索支持）
├─pap4j-boot3-starters-neo4j                     # Neo4j 图数据库集成（原生驱动封装）
├─pap4j-boot3-starters-quartz                    # Quartz 定时任务调度框架扩展（支持集群任务管理）
└─pap4j-boot3-starters-task                      # 自定义异步任务执行与追踪组件
```
</details>

<details>
<summary><b>点击展开查看【核心通用工具库 (pap4j-common)】</b></summary>

```text
├─pap4j-common-bitmap                            # RoaringBitmap 高性能位图算法封装
├─pap4j-common-boofcv                            # BoofCV 计算机视觉库常用功能封装
├─pap4j-common-datasketches                      # Apache DataSketches 大数据流式计算算法
├─pap4j-common-datastructure                     # 增强型数据结构：树形、聚类算法、设计模式封装
├─pap4j-common-deeplearning4j                   # Deeplearning4j 深度学习框架集成
├─pap4j-common-docx                              # POI Docx 复杂文档处理组件
├─pap4j-common-excel                             # Alibaba EasyExcel 高性能 Excel 处理组件
├─pap4j-common-file                              # 通用文件操作工具（支持 ISO 解压）
├─pap4j-common-groovy                            # Groovy 脚本引擎动态执行组件
├─pap4j-common-itext7                            # iText 7 高级 PDF 生成与编辑工具
├─pap4j-common-jdbc                              # 基础 JDBC 增强与原生 SQL 执行工具
├─pap4j-common-jena                              # Apache Jena 语义网与 RDF 三元组操作组件
├─pap4j-common-jsonorm                           # JSON 与 ORM 的自定义映射与转换引擎
├─pap4j-common-jsqlparser                        # JSqlParser 动态 SQL 解析与自动改写工具
├─pap4j-common-kingbase                          # 人大金仓国产数据库适配驱动封装
├─pap4j-common-minio                             # MinIO 对象存储标准操作组件
├─pap4j-common-opencv                            # OpenCV 图像处理算法底层驱动
├─pap4j-common-pdf                               # PDFBox 标准 PDF 文档处理工具
├─pap4j-common-qlexpress                         # QLExpress 表达式引擎业务增强方案
├─pap4j-common-spider                            # Jsoup 爬虫与数据提取通用组件
├─pap4j-common-tesseract                         # Tesseract OCR 文本识别处理工具
├─pap4j-common-webdav                             # Jackrabbit WebDAV 协议通信组件
└─pap4j-common-worker                             # 统一业务工作者模型与多线程任务封装
```
</details>

<details>
<summary><b>点击展开查看【集成最佳实践 (pap4j-boot3-example)】</b></summary>

```text
├─pap4j-boot3-example-actuator-admin             # Spring Boot Admin 监控端点集成示例
├─pap4j-boot3-example-admin                      # 监控管理后台服务端集成实践
├─pap4j-boot3-example-apitester                  # 自动化接口测试与压测示例
├─pap4j-boot3-example-assembly                   # Maven Assembly 复杂制品分发打包配置
├─pap4j-boot3-example-async                      # 异步编程、CompletableFuture 最佳实践
├─pap4j-boot3-example-doris                      # Apache Doris 实时数仓数据写入与查询示例
├─pap4j-boot3-example-dynamic-form               # 动态表单生成与 JSON Schema 校验实践
├─pap4j-boot3-example-javafx                     # JavaFX 现代化桌面 UI 客户端开发示例
├─pap4j-boot3-example-proguard                   # ProGuard 代码混淆与安全加密配置
├─pap4j-boot3-example-spring-ai                  # Spring AI 流式对话（支持 RAG、记忆、Markdown）
├─pap4j-boot3-example-testcontainers             # Testcontainers 基于 Docker 的集成测试方案
├─pap4j-boot3-example-webflux                    # Spring WebFlux 响应式编程与流式接口
└─pap4j-boot3-example-wechat                     # 微信公众号/小程序后端集成实践
```
</details>

---

#### 🚀 快速开始

**1. 本地构建**
```bash
mvn clean install -Dfile.encoding=UTF-8
```

**2. 中央仓库依赖 (Version 0.0.4)**
项目核心组件已发布至 Maven 中央仓库，可直接引入：
```xml
<dependency>
    <groupId>cn.net.pap</groupId>
    <artifactId>pap4j-boot3-starters-cache</artifactId>
    <version>0.0.4</version>
</dependency>
```

**3. 核心规约**
- **Java 规范**: 严禁显式 `new Thread()`，必须通过自定义 `ThreadPoolExecutor`。
- **日志规约**: 杜绝 `System.out`，强制使用 `SLF4J` 占位符记录。
- **信创适配**: 优先支持国产图数据库 (gStore) 与国产关系型数据库 (Kingbase)。

---

#### 🛤️ 2026 Roadmap

- [ ] **多模态 AI 接入**: 集成图像识别与语音转文字模型到 Spring AI 模块。
- [ ] **虚拟线程深度优化**: 在 `pap4j-common-worker` 中全面引入 JDK 21 虚拟线程支持。
- [ ] **低代码支持**: 基于 `dynamic-form` 模块实现全自动 CRUD 代码生成器。

---

#### Special thanks to JetBrains for supporting open-source projects

[![](https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg)](https://gitee.com/alexgaoyh/pap4j-boot3)

---
© 2024-2026 [alexgaoyh](https://gitee.com/alexgaoyh) | [Maven Central](https://central.sonatype.com/namespace/cn.net.pap)
