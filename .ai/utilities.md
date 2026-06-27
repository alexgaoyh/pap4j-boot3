# 🛠️ 公共工具集索引 (Utilities Index)

在编写新代码前，请检查以下现有工具类以避免重复造轮子。

## 📁 基础与文本 (File & Text)
*   [ReadFileToMapUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/ReadFileToMapUtil.java): 读键值对配置文件到 Map。
*   [ReadTxtToStringUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/ReadTxtToStringUtil.java): 读取纯文本到 String。
*   [ResourceUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/resource/ResourceUtil.java): JPMS 模块下加载 Classpath 资源。
*   [TempDirUtils](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/util/TempDirUtils.java): 临时目录与文件生命周期管理。
*   [ReadDocDocUtils](../pap4j-common/pap4j-common-docx/src/main/java/cn/net/pap/common/docx/ReadDocDocUtils.java): 读取 Word 文档（.doc/.docx）内容为纯文本。
*   [ChineseWordSorterUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/chinese/ChineseWordSorterUtil.java): 字典序（UTF-16）分流写入中文词库并自动维护排序。
*   [BinaryConvertUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/util/BinaryConvertUtil.java): 十进制与 Base62 高进制数值的快速编解码转换。
*   [FileOperUtils](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/file/FileOperUtils.java): 递归删除目录与清空子文件树的安全文件操作工具。

## 🕸️ XML & XPath
*   [StaxXmlUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/xml/StaxXmlUtil.java): 流式游标解析大 XML 文件。
*   [ExtFunctionResolver](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/xml/xpath/ExtFunctionResolver.java): 注册自定义 XPath 辅助函数。

## 📦 JSON & ORM
*   [JacksonUtil](../pap4j-common/pap4j-common-jsonorm/src/main/java/cn/net/pap/common/jsonorm/util/JacksonUtil.java): 高级 JSON 序列化工具。
*   [JsonlUtil](../pap4j-common/pap4j-common-jsonorm/src/main/java/cn/net/pap/common/jsonorm/util/JsonlUtil.java): 行式 JSONL 追加及读尾行优化。
*   [LinuxTreeToJsonUtil](../pap4j-common/pap4j-common-jsonorm/src/main/java/cn/net/pap/common/jsonorm/tree/LinuxTreeToJsonUtil.java): `tree -J` 目录结构转 JSON。

## 🛡️ SQL 校验与解析
*   [JsonToSqlConverter](../pap4j-common/pap4j-common-jsqlparser/src/main/java/cn/net/pap/common/jsqlparser/JsonToSqlConverter.java): JSON 查询转换 SQL，带 AST 防注入校验。
*   [SQLCheckerUtil](../pap4j-common/pap4j-common-jsqlparser/src/main/java/cn/net/pap/common/jsqlparser/SQLCheckerUtil.java): SQL 语法验证与动态改写。

## 🌐 爬虫与诊断
*   [TakeScreenshotTest](../pap4j-common/pap4j-common-spider/src/test/java/cn/net/pap/common/spider/html/TakeScreenshotTest.java): Headless 截图及 Console/Network 日志获取。
*   [OkHttpBatchExecutor](../pap4j-common/pap4j-common-spider/src/main/java/cn/net/pap/common/spider/util/OkHttpBatchExecutor.java): OkHttp 安全批处理并发执行器。
*   [OkHttpBatchExecutorTest](../pap4j-common/pap4j-common-spider/src/test/java/cn/net/pap/common/spider/util/OkHttpBatchExecutorTest.java): 演示通过 Interceptor 拦截外部不可达 API，并支持基于 JSON 请求体参数路由分发 Mock 数据、或直接触发 SocketTimeoutException 异常，支持本地业务离线联调测试。
*   [HttpClientBatchExecutor](../pap4j-common/pap4j-common-spider/src/main/java/cn/net/pap/common/spider/util/HttpClientBatchExecutor.java): Apache HttpClient 5 安全批处理并发执行器（满足对 HttpClient 依赖场景下的统一实现）。
*   [HttpClientBatchExecutorTest](../pap4j-common/pap4j-common-spider/src/test/java/cn/net/pap/common/spider/util/HttpClientBatchExecutorTest.java): 演示通过 ExecChainHandler (拦截器) 拦截 Apache HttpClient 5 请求，解析 JSON 请求体参数路由分发 Mock 数据、或直接触发 SocketTimeoutException 异常，支持本地业务离线联调测试。
*   [JsoupUtil](../pap4j-common/pap4j-common-spider/src/main/java/cn/net/pap/common/spider/jsoup/JsoupUtil.java): 基于 Jsoup 和 ICU4J 视觉字符的 HTML 标签反射生成、分页索引过滤、多重 CSS 关键字高亮工具。

## 👁️ 图像与 OCR
*   [OpenCVUtils](../pap4j-common/pap4j-common-opencv/src/main/java/cn/net/pap/common/opencv/OpenCVUtils.java): OpenCV 加载与初始化。
*   [ImageSteganographyUtils](../pap4j-common/pap4j-common-opencv/src/main/java/cn/net/pap/common/opencv/ImageSteganographyUtils.java): 图片最低有效位 (LSB) 文本隐写。
*   [OCRUtils](../pap4j-common/pap4j-common-tesseract/src/main/java/cn/net/pap/common/tesseract/util/OCRUtils.java): Tesseract 本地 OCR 识别。
*   [BoofcvUtil](../pap4j-common/pap4j-common-boofcv/src/main/java/cn/net/pap/common/boofcv/BoofcvUtil.java): BoofCV 工业级视觉特征与图像处理包。
*   [CannyEdgeUtilss](../pap4j-common/pap4j-common-boofcv/src/main/java/cn/net/pap/common/boofcv/CannyEdgeUtilss.java): 视觉边缘检测算法。

## 📄 Office Excel & PDF
*   [ExcelUtil](../pap4j-common/pap4j-common-excel/src/main/java/cn/net/pap/common/excel/ExcelUtil.java): POI / EasyExcel 读写封装。
*   [ExcelCRUDUtil](../pap4j-common/pap4j-common-excel/src/main/java/cn/net/pap/common/excel/ExcelCRUDUtil.java): 具备单元格类型自适应读取、公式与日期转换、整行增删改查的 Excel 数据管理类。
*   [ExcelCopyUtil](../pap4j-common/pap4j-common-excel/src/main/java/cn/net/pap/common/excel/ExcelCopyUtil.java): 支持合并单元格复制、列宽行高克隆以及模板块循环平移复制的高级 Excel 拷贝工具。
*   [PDFUtil](../pap4j-common/pap4j-common-pdf/src/main/java/cn/net/pap/common/pdf/PDFUtil.java): iText7 渲染及多语言字体映射.
*   [FontSubsetUtils](../pap4j-common/pap4j-common-pdf/src/main/java/cn/net/pap/common/pdf/FontSubsetUtils.java): 嵌入式字体子集压缩。
*   [Html2DocxUtils](../pap4j-common/pap4j-common-docx/src/main/java/cn/net/pap/common/docx/Html2DocxUtils.java): HTML 富文本转换为 Word (docx) 文档。

## ⚡ 高性能位图与海量计算 (Bitmap & DataSketches)
*   [Roaring64NavigableMapUtil](../pap4j-common/pap4j-common-bitmap/src/main/java/cn/net/pap/common/bitmap/Roaring64NavigableMapUtil.java): 64位高性能 RoaringBitmap 存储与海量数据交并集运算。
*   [MD5StoreUtil](../pap4j-common/pap4j-common-bitmap/src/main/java/cn/net/pap/common/bitmap/MD5StoreUtil.java): 基于位图的亿级 MD5 数据极速去重。
*   [TfIdfDataSketchesUtil](../pap4j-common/pap4j-common-datasketches/src/main/java/cn/net/pap/common/datasketches/util/TfIdfDataSketchesUtil.java): 基于 Apache DataSketches 流式估算 TF-IDF 权重与 TopK 词频。

## 🧠 算法与增强数据结构 (Algorithms & DataStructures)
*   [CatalogUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/catalog/CatalogUtil.java): 层级目录树构造与跨层级节点快速匹配。
*   [DoubleArrayTrie](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/trie/DoubleArrayTrie.java): 双数组 Trie 树（Darts 算法）高性能前缀过滤与敏感词匹配。
*   [BPETokenization](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/tokenization/BPETokenization.java): LLM 大模型 BPE 分词分词器实现。
*   [SimHash](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/simHash/SimHash.java): LSH 局部敏感哈希海量文本相似性匹配。
*   [ReservoirSamplingUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/ReservoirSampling/ReservoirSamplingUtil.java): 蓄水池抽样算法，实现流式大数据的在线均匀抽样。
*   [ReversibleShortUrl](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/url/ReversibleShortUrl.java): 双向可逆短链接哈希映射。
*   [MessyCodeRecoveryUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/chatset/MessyCodeRecoveryUtil.java): 基于多种常见中文字符集交叉编解码尝试的乱码恢复与评分排序工具。
*   [MyersDiffUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/myersdiff/MyersDiffUtil.java): Myers Diff 差异比对算法，计算行/字的最短编辑路径，并生成 Git 风格 diff。
*   [DataTraceIdUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/trace/DataTraceIdUtil.java): 具备完整性校验且嵌入业务域、数据源、秒级时间戳及分支层级的 Base62 数据血缘关系追踪 ID 编码工具。
*   [DataTreeIdUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/trace/DataTreeIdUtil.java): 基于固定长度 Base62 节点拼接的有序完整前缀树形追踪 ID 生成与根路径回溯工具。
*   [IpUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/ip/IpUtil.java): 强大的局域网、内网与保留 IP 地址段判断工具，支持 CIDR 网段、IP 范围、通配符等解析匹配。
*   [Express4RunnerUtil](../pap4j-common/pap4j-common-qlexpress/src/main/java/cn/net/pap/common/qlexpress/Express4RunnerUtil.java): QLExpress 4 脚本表达式引擎封装，内置高精度除法及树形扁平化等操作符，结合 JsonPath 实现快速数据提取与字段计算。

## 🔌 深度学习与外部存储 (MinIO & WebDAV & DL4J)
*   [MinioUtil](../pap4j-common/pap4j-common-minio/src/main/java/cn/net/pap/common/minio/util/MinioUtil.java): MinIO 对象存储桶与文件标准操作器。
*   [WebDavUtil](../pap4j-common/pap4j-common-webdav/src/main/java/cn/net/pap/common/webdav/WebDavUtil.java): WebDAV 协议远程存储交互工具。
*   [Deeplearning4jUtilss](../pap4j-common/pap4j-common-deeplearning4j/src/main/java/cn/net/pap/common/deeplearning4j/Deeplearning4jUtilss.java): Deeplearning4j 本地神经网络前向推理工具。

## ⚙️ 异步与并发调度 (Worker Framework)
*   [TaskExecutor](../pap4j-common/pap4j-common-worker/src/main/java/cn/net/pap/common/worker/executor/TaskExecutor.java): 并发执行器与异步任务跟踪模型。
*   [SimpleMaster](../pap4j-common/pap4j-common-worker/src/main/java/cn/net/pap/common/worker/simple/SimpleMaster.java) / [SimpleWorker](../pap4j-common/pap4j-common-worker/src/main/java/cn/net/pap/common/worker/simple/SimpleWorker.java): 核心 Master-Worker 并行计算模型抽象。

## 🔌 Spring Boot Starters 增强工具 (Starters Utilities)
*   [MilvusUtilss](../pap4j-boot3-starters/pap4j-boot3-starters-milvus/src/main/java/cn/net/pap/milvus/MilvusUtilss.java): Milvus 向量数据库数据交互与向量相似度检索工具。
*   [GstoreConnector](../pap4j-boot3-starters/pap4j-boot3-starters-gStore/src/main/java/jgsc/GstoreConnector.java): gStore 图数据库的 Java 连接与 SPARQL 语句执行封装。
*   [TaskExecutorUtil](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/main/java/cn/net/pap/task/executor/TaskExecutorUtil.java): 独立高并发线程池任务执行器，内置拒绝策略捕获、超时监控及中断防日志风暴策略。
*   [DynamicTaskExecutorUtil](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/main/java/cn/net/pap/task/util/DynamicTaskExecutorUtil.java): 包含异步执行、排队能力、线程池参数化调整的异步调度工具。
*   [RetryUtil](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/main/java/cn/net/pap/task/retry/RetryUtil.java): 声明式优雅重试组件，支持断路器机制配合。
*   [WebClientUtil](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/main/java/cn/net/pap/task/webclient/WebClientUtil.java): 响应式 Spring WebClient 的底层超时及自动重试封装。
*   [LogbackConfigurationUtil](../pap4j-boot3-starters/pap4j-boot3-starters-logback/src/main/java/cn/net/pap/logback/util/LogbackConfigurationUtil.java): 运行时 Logback 日志上下文属性的动态重载与配置工具。
*   [LoggerLevelUtil](../pap4j-boot3-starters/pap4j-boot3-starters-logback/src/main/java/cn/net/pap/logback/util/LoggerLevelUtil.java): 在线运行时动态查询及设置 Logback 中指定包/类日志级别的管理工具。
*   [QuartzUtils](../pap4j-boot3-starters/pap4j-boot3-starters-quartz/src/main/java/cn/net/pap/quartz/util/QuartzUtils.java): 快速注册与调配 Quartz 任务触发器及作业的控制工具。
*   [GetThreadsWithFullStackTraceUtil](../pap4j-boot3-starters/pap4j-boot3-starters-quartz/src/main/java/cn/net/pap/quartz/util/GetThreadsWithFullStackTraceUtil.java): JVM 活跃线程级堆栈深层诊断分析工具。

## 💡 业务与最佳实践工具 (Example Utilities)
*   [SimpleRateLimiter](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/main/java/cn/net/pap/example/proguard/util/SimpleRateLimiter.java): 内存滑动窗口的高并发速率限制器（限流组件）。
*   [SpringUtils](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/main/java/cn/net/pap/example/proguard/util/SpringUtils.java): 静态直接存取与调用 Spring ApplicationContext 及其 Bean 的全局门面类。
*   [NumberSegmentUtil](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/main/java/cn/net/pap/example/proguard/util/NumberSegmentUtil.java): 高级号段步长式分布式 ID 发号发牌规则处理类。
*   [SearchUtil](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/main/java/cn/net/pap/example/proguard/util/SearchUtil.java): JPA 动态复杂 Criteria 多条件聚合解析与通用拼装器。
