# 🛠️ 公共工具集索引 (Utilities Index)

在编写新代码前，请检查以下现有工具类以避免重复造轮子。

> 🔄 **维护规则**：新增 `public` 工具类经 `[Shell]` 验证通过后，AI 必须在最终回复中提示用户将其补录到本文件对应分类中。

## 🔁 AI 协同与自愈反馈闭环 (AI Co-pilot Feedback Loops)

为了使 AI 代理能够通过“自动化反馈”在沙箱环境中高效实现编译、测试和代码自愈，本项目建立了以下三条核心反馈闭环，开发和调试时请遵循对应的操作指南：

1. **接口契约同步闭环 (API Contract Sync Loop)**:
   * **适用场景**：当您新增、删除或修改了 Controller 接口层代码后，需要同步刷新本地 API 契约视图。
   * **操作步骤**：在项目根目录下执行以下 PowerShell 命令（防止点号被误解析）：
     `mvn clean test "-Dtest=ApiRouterCatalogExporterTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
   * **闭环产物**：该命令会自动编译项目并执行所有子模块下的 [ApiRouterCatalogExporterTest](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/test/java/cn/net/pap/example/proguard/diagnostics/ApiRouterCatalogExporterTest.java)，将接口生成为 `.json` 文件并导出至项目根目录下的 `.ai/openapi/`（以子模块名命名）。AI 随后可根据这些契约文件更新接口映射认知。

2. **单测自愈诊断闭环 (Unit Test Failure Healing Loop)**:
   * **适用场景**：当运行 Maven 构建或单元测试遇到失败时。
   * **操作步骤**：本项目集成了 [pap4j-common-test](../pap4j-common/pap4j-common-test/README.md) 单测运行监听与诊断模块，单测运行失败时，会自动提取异常堆栈并精简为纯粹的报错根因，输出到项目根目录下的 `.ai/diagnostics/` 目录。
   * **闭环产物**：AI 代理人可直接扫描并查阅 `.ai/diagnostics/` 目录下的错误诊断快照文件，快速定位核心故障而无需解析臃肿的日志，修改代码后再次触发单测，完成“失败 -> 捕获精简堆栈 -> 自动修复 -> 重跑单测”的自愈闭环。

3. **离线请求录制与回放闭环 (Offline Mock & Replay Loop)**:
   * **适用场景**：调试第三方 API 不可用、局域网访问限制，或需要本地重现接口的 4xx/5xx 错误。
   * **操作步骤**：
     * 系统在过滤到 HTTP 400/500/未捕获异常时，通过 [ReqResLoggerHttpFilter](../pap4j-boot3-starters/pap4j-boot3-starters-logback/src/main/java/cn/net/pap/logback/filter/ReqResLoggerHttpFilter.java) 自动录制 HTTP 报文快照，写出到 `logs/recorded-bugs/` 下 of `bug_*.json` 文件。
     * AI 代理人可以读取此 JSON 数据，并在不需要启动真实 Servlet 容器的情况下，利用 [ReqResLoggerReplayTest](../pap4j-boot3-starters/pap4j-boot3-starters-logback/src/test/java/cn/net/pap/logback/filter/ReqResLoggerReplayTest.java) 描述的方式，利用最小化的 MockMvc 模拟器回放该请求以复现和定位问题。

## 📁 基础与文本 (File & Text)
*   [ReadFileToMapUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/ReadFileToMapUtil.java): 读键值对配置文件到 Map。（测试验证见 [ReadFileToMapUtilTest](../pap4j-common/pap4j-common-file/src/test/java/cn/net/pap/common/file/ReadFileToMapUtilTest.java)）
*   [ReadTxtToStringUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/ReadTxtToStringUtil.java): 读取纯文本到 String。（测试验证见 [ReadTxtToStringUtilTest](../pap4j-common/pap4j-common-file/src/test/java/cn/net/pap/common/file/ReadTxtToStringUtilTest.java)）
*   [ResourceUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/resource/ResourceUtil.java): JPMS 模块下加载 Classpath 资源。（测试验证见 [ResourceUtilTest](../pap4j-common/pap4j-common-file/src/test/java/cn/net/pap/common/file/resource/ResourceUtilTest.java)）
*   [TempDirUtils](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/util/TempDirUtils.java): 临时目录与文件生命周期管理。（测试验证见 [TempDirUtilsTest](../pap4j-common/pap4j-common-file/src/test/java/cn/net/pap/common/file/util/TempDirUtilsTest.java)）
*   [ReadDocDocUtils](../pap4j-common/pap4j-common-docx/src/main/java/cn/net/pap/common/docx/ReadDocDocUtils.java): 读取 Word 文档（.doc/.docx）内容为纯文本。（测试验证见 [ReadDocDocxUtilsTest](../pap4j-common/pap4j-common-docx/src/test/java/cn/net/pap/common/docx/ReadDocDocxUtilsTest.java)）
*   [ChineseWordSorterUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/chinese/ChineseWordSorterUtil.java): 字典序（UTF-16）分流写入中文词库并自动维护排序。（测试验证见 [ChineseWordSorterUtilTest](../pap4j-common/pap4j-common-file/src/test/java/cn/net/pap/common/file/ChineseWordSorterUtilTest.java)）
*   [BinaryConvertUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/util/BinaryConvertUtil.java): 十进制与 Base62 高进制数值的快速编解码转换。（测试验证见 [BinaryConvertUtilTest](../pap4j-common/pap4j-common-file/src/test/java/cn/net/pap/common/file/BinaryConvertUtilTest.java)）
*   [FileOperUtils](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/file/FileOperUtils.java): 递归删除目录与清空子文件树的安全文件操作工具。

## 🕸️ XML & XPath
*   [StaxXmlUtil](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/xml/StaxXmlUtil.java): 流式游标解析大 XML 文件。（测试验证见 [StaxXmlUtilTest](../pap4j-common/pap4j-common-file/src/test/java/cn/net/pap/common/file/StaxXmlUtilTest.java)）
*   [ExtFunctionResolver](../pap4j-common/pap4j-common-file/src/main/java/cn/net/pap/common/file/xml/xpath/ExtFunctionResolver.java): 注册自定义 XPath 辅助函数。（测试验证见 [StaxXmlUtilTest](../pap4j-common/pap4j-common-file/src/test/java/cn/net/pap/common/file/StaxXmlUtilTest.java)）

## 📦 JSON & ORM
*   [JacksonUtil](../pap4j-common/pap4j-common-jsonorm/src/main/java/cn/net/pap/common/jsonorm/util/JacksonUtil.java): 高级 JSON 序列化工具。（测试验证见 [JacksonUtilTest](../pap4j-common/pap4j-common-jsonorm/src/test/java/cn/net/pap/common/jsonorm/JacksonUtilTest.java)）
*   [JsonlUtil](../pap4j-common/pap4j-common-jsonorm/src/main/java/cn/net/pap/common/jsonorm/util/JsonlUtil.java): 行式 JSONL 追加及读尾行优化。（测试验证见 [JsonlTest](../pap4j-common/pap4j-common-jsonorm/src/test/java/cn/net/pap/common/jsonorm/JsonlTest.java)）
*   [LinuxTreeToJsonUtil](../pap4j-common/pap4j-common-jsonorm/src/main/java/cn/net/pap/common/jsonorm/tree/LinuxTreeToJsonUtil.java): `tree -J` 目录结构转 JSON。（测试验证见 [LinuxTreeToJsonUtilTest](../pap4j-common/pap4j-common-jsonorm/src/test/java/cn/net/pap/common/jsonorm/tree/LinuxTreeToJsonUtilTest.java)）

## 🛡️ SQL 校验与解析
*   [JsonToSqlConverter](../pap4j-common/pap4j-common-jsqlparser/src/main/java/cn/net/pap/common/jsqlparser/JsonToSqlConverter.java): JSON 查询转换 SQL，带 AST 防注入校验。（测试验证见 [JsonToSqlConverterTest](../pap4j-common/pap4j-common-jsqlparser/src/test/java/cn/net/pap/common/jsqlparser/JsonToSqlConverterTest.java)）
*   [SQLCheckerUtil](../pap4j-common/pap4j-common-jsqlparser/src/main/java/cn/net/pap/common/jsqlparser/SQLCheckerUtil.java): SQL 语法验证与动态改写。（测试验证见 [SQLCheckerUtilTest](../pap4j-common/pap4j-common-jsqlparser/src/test/java/cn/net/pap/common/jsqlparser/SQLCheckerUtilTest.java)）
*   [DbMetadataReader](../pap4j-common/pap4j-common-jdbc/src/main/java/cn/net/pap/common/jdbc/DbMetadataReader.java): 物理数据库元数据读取工具，支持 MySQL、Kingbase、H2 等多种数据库的表与字段及其注释元数据获取。（测试验证见 [DbMetadataReaderTest](../pap4j-common/pap4j-common-jdbc/src/test/java/cn/net/pap/common/jdbc/DbMetadataReaderTest.java)）


## 🌐 爬虫与诊断
*   [OkHttpBatchExecutor](../pap4j-common/pap4j-common-spider/src/main/java/cn/net/pap/common/spider/util/OkHttpBatchExecutor.java): OkHttp 安全批处理并发执行器。（测试验证见 [OkHttpBatchExecutorTest](../pap4j-common/pap4j-common-spider/src/test/java/cn/net/pap/common/spider/util/OkHttpBatchExecutorTest.java)）
*   [OkHttpBatchExecutorTest](../pap4j-common/pap4j-common-spider/src/test/java/cn/net/pap/common/spider/util/OkHttpBatchExecutorTest.java): 演示通过 Interceptor 拦截外部不可达 API，并支持基于 JSON 请求体参数路由分发 Mock 数据、或直接触发 SocketTimeoutException 异常，支持本地业务离线联调测试。
*   [HttpClientBatchExecutor](../pap4j-common/pap4j-common-spider/src/main/java/cn/net/pap/common/spider/util/HttpClientBatchExecutor.java): Apache HttpClient 5 安全批处理并发执行器（满足对 HttpClient 依赖场景下的统一实现）。（测试验证见 [HttpClientBatchExecutorTest](../pap4j-common/pap4j-common-spider/src/test/java/cn/net/pap/common/spider/util/HttpClientBatchExecutorTest.java)）
*   [HttpClientBatchExecutorTest](../pap4j-common/pap4j-common-spider/src/test/java/cn/net/pap/common/spider/util/HttpClientBatchExecutorTest.java): 演示通过 ExecChainHandler (拦截器) 拦截 Apache HttpClient 5 请求，解析 JSON 请求体参数路由分发 Mock 数据、或直接触发 SocketTimeoutException 异常，支持本地业务离线联调测试。
*   [JsoupUtil](../pap4j-common/pap4j-common-spider/src/main/java/cn/net/pap/common/spider/jsoup/JsoupUtil.java): 基于 Jsoup 和 ICU4J 视觉字符的 HTML 标签反射生成、分页索引过滤、多重 CSS 关键字高亮工具。（测试验证见 [JsoupUtilTest](../pap4j-common/pap4j-common-spider/src/test/java/cn/net/pap/common/spider/JsoupUtilTest.java)）

## 👁️ 图像与 OCR
*   [OpenCVUtils](../pap4j-common/pap4j-common-opencv/src/main/java/cn/net/pap/common/opencv/OpenCVUtils.java): OpenCV 加载与初始化。（测试验证见 [OpenCVUtilsTest](../pap4j-common/pap4j-common-opencv/src/test/java/cn/net/pap/common/opencv/OpenCVUtilsTest.java)）
*   [ImageSteganographyUtils](../pap4j-common/pap4j-common-opencv/src/main/java/cn/net/pap/common/opencv/ImageSteganographyUtils.java): 图片最低有效位 (LSB) 文本隐写。（测试验证见 [ImageSteganographyUtilsTest](../pap4j-common/pap4j-common-opencv/src/test/java/cn/net/pap/common/opencv/ImageSteganographyUtilsTest.java)）
*   [OCRUtils](../pap4j-common/pap4j-common-tesseract/src/main/java/cn/net/pap/common/tesseract/util/OCRUtils.java): Tesseract 本地 OCR 识别。（测试验证见 [OCRUtilsTest](../pap4j-common/pap4j-common-tesseract/src/test/java/cn/net/pap/common/tesseract/util/OCRUtilsTest.java)）
*   [BoofcvUtil](../pap4j-common/pap4j-common-boofcv/src/main/java/cn/net/pap/common/boofcv/BoofcvUtil.java): BoofCV 工业级视觉特征与图像处理包。（测试验证见 [BoofcvUtilTest](../pap4j-common/pap4j-common-boofcv/src/test/java/cn/net/pap/common/boofcv/BoofcvUtilTest.java)）
*   [CannyEdgeUtilss](../pap4j-common/pap4j-common-boofcv/src/main/java/cn/net/pap/common/boofcv/CannyEdgeUtilss.java): 视觉边缘检测算法。

## 📄 Office Excel & PDF
*   [ExcelUtil](../pap4j-common/pap4j-common-excel/src/main/java/cn/net/pap/common/excel/ExcelUtil.java): POI / EasyExcel 读写封装。（测试验证见 [ExcelUtilTest](../pap4j-common/pap4j-common-excel/src/test/java/cn/net/pap/common/excel/ExcelUtilTest.java)）
*   [ExcelCRUDUtil](../pap4j-common/pap4j-common-excel/src/main/java/cn/net/pap/common/excel/ExcelCRUDUtil.java): 具备单元格类型自适应读取、公式与日期转换、整行增删改查的 Excel 数据管理类。（测试验证见 [ExcelCRUDUtilTest](../pap4j-common/pap4j-common-excel/src/test/java/cn/net/pap/common/excel/ExcelCRUDUtilTest.java)）
*   [ExcelCopyUtil](../pap4j-common/pap4j-common-excel/src/main/java/cn/net/pap/common/excel/ExcelCopyUtil.java): 支持合并单元格复制、列宽行高克隆以及模板块循环平移复制的高级 Excel 拷贝工具。
*   [PDFUtil](../pap4j-common/pap4j-common-pdf/src/main/java/cn/net/pap/common/pdf/PDFUtil.java): iText7 渲染及多语言字体映射。（测试验证见 [PDFUtilTest](../pap4j-common/pap4j-common-pdf/src/test/java/cn/net/pap/common/pdf/PDFUtilTest.java)）
*   [FontSubsetUtils](../pap4j-common/pap4j-common-pdf/src/main/java/cn/net/pap/common/pdf/FontSubsetUtils.java): 嵌入式字体子集压缩。（测试验证见 [FontSubsetUtilsTest](../pap4j-common/pap4j-common-pdf/src/test/java/cn/net/pap/common/pdf/FontSubsetUtilsTest.java)）
*   [Html2DocxUtils](../pap4j-common/pap4j-common-docx/src/main/java/cn/net/pap/common/docx/Html2DocxUtils.java): HTML 富文本转换为 Word (docx) 文档。（测试验证见 [Html2DocxUtilsTest](../pap4j-common/pap4j-common-docx/src/test/java/cn/net/pap/common/docx/Html2DocxUtilsTest.java)）

## ⚡ 高性能位图与海量计算 (Bitmap & DataSketches)
*   [Roaring64NavigableMapUtil](../pap4j-common/pap4j-common-bitmap/src/main/java/cn/net/pap/common/bitmap/Roaring64NavigableMapUtil.java): 64位高性能 RoaringBitmap 存储与海量数据交并集运算。（测试验证见 [Roaring64NavigableMapTest](../pap4j-common/pap4j-common-bitmap/src/test/java/cn/net/pap/common/bitmap/Roaring64NavigableMapTest.java)）
*   [MD5StoreUtil](../pap4j-common/pap4j-common-bitmap/src/main/java/cn/net/pap/common/bitmap/MD5StoreUtil.java): 基于位图的亿级 MD5 数据极速去重。（测试验证见 [MD5StoreUtilTest](../pap4j-common/pap4j-common-bitmap/src/test/java/cn/net/pap/common/bitmap/MD5StoreUtilTest.java)）
*   [TfIdfDataSketchesUtil](../pap4j-common/pap4j-common-datasketches/src/main/java/cn/net/pap/common/datasketches/util/TfIdfDataSketchesUtil.java): 基于 Apache DataSketches 流式估算 TF-IDF 权重与 TopK 词频。（测试验证见 [TfIdfDataSketchesUtilTest](../pap4j-common/pap4j-common-datasketches/src/test/java/cn/net/pap/common/datasketches/util/TfIdfDataSketchesUtilTest.java)）

## 🧠 算法与增强数据结构 (Algorithms & DataStructures)
*   [CatalogUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/catalog/CatalogUtil.java): 层级目录树构造与跨层级节点快速匹配。（测试验证见 [CatalogUtilTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/catalog/CatalogUtilTest.java)）
*   [DoubleArrayTrie](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/trie/DoubleArrayTrie.java): 双数组 Trie 树（Darts 算法）高性能前缀过滤与敏感词匹配。（测试验证见 [DoubleArrayTrieTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/catalog/DoubleArrayTrieTest.java)）
*   [BPETokenization](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/tokenization/BPETokenization.java): LLM 大模型 BPE 分词分词器实现。（测试验证见 [BPETokenizationTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/tokenization/BPETokenizationTest.java)）
*   [SimHash](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/simHash/SimHash.java): LSH 局部敏感哈希海量文本相似性匹配。（测试验证见 [SimHashTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/simHash/SimHashTest.java)）
*   [ReservoirSamplingUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/ReservoirSampling/ReservoirSamplingUtil.java): 蓄水池抽样算法，实现流式大数据的在线均匀抽样。（测试验证见 [ReservoirSamplingUtilTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/ReservoirSampling/ReservoirSamplingUtilTest.java)）
*   [ReversibleShortUrl](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/url/ReversibleShortUrl.java): 双向可逆短链接哈希映射。（测试验证见 [ReversibleShortUrlTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/url/ReversibleShortUrlTest.java)）
*   [MessyCodeRecoveryUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/chatset/MessyCodeRecoveryUtil.java): 基于多种常见中文字符集交叉编解码尝试的乱码恢复与评分排序工具。（测试验证见 [MessyCodeRecoveryUtilTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/charset/MessyCodeRecoveryUtilTest.java)）
*   [MyersDiffUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/myersdiff/MyersDiffUtil.java): Myers Diff 差异比对算法，计算行/字的最短编辑路径，并生成 Git 风格 diff。（测试验证见 [MyersDiffUtilTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/myersdiff/MyersDiffUtilTest.java)）
*   [DataTraceIdUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/trace/DataTraceIdUtil.java): 具备完整性校验且嵌入业务域、数据源、秒级时间戳及分支层级的 Base62 数据血缘关系追踪 ID 编码工具。（测试验证见 [DataTraceIdUtilTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/trace/DataTraceIdUtilTest.java)）
*   [DataTreeIdUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/trace/DataTreeIdUtil.java): 基于固定长度 Base62 节点拼接的有序完整前缀树形追踪 ID 生成与根路径回溯工具。（测试验证见 [DataTreeIdUtilTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/trace/DataTreeIdUtilTest.java)）
*   [IpUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/ip/IpUtil.java): 强大的局域网、内网与保留 IP 地址段判断工具，支持 CIDR 网段、IP 范围、通配符等解析匹配。（测试验证见 [IPTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/ip/IPTest.java)）
*   [SmCryptoUtil](../pap4j-common/pap4j-common-datastructure/src/main/java/cn/net/pap/common/datastructure/sm/SmCryptoUtil.java): 国密算法 (SM2, SM3, SM4) 实现工具类，支持字节级和 String/Hex 封装的哈希、对称加密/解密、非对称加密/解密、签名及验签。（测试验证见 [SmCryptoUtilTest](../pap4j-common/pap4j-common-datastructure/src/test/java/cn/net/pap/common/datastructure/sm/SmCryptoUtilTest.java)）
*   [Express4RunnerUtil](../pap4j-common/pap4j-common-qlexpress/src/main/java/cn/net/pap/common/qlexpress/Express4RunnerUtil.java): QLExpress 4 脚本表达式引擎封装，内置高精度除法及树形扁平化等操作符，结合 JsonPath 实现快速数据提取与字段计算。（测试验证见 [QLExpressArithTest](../pap4j-common/pap4j-common-qlexpress/src/test/java/cn/net/pap/common/qlexpress/QLExpressArithTest.java) 和 [JsonFunctionalExtractorTest](../pap4j-common/pap4j-common-qlexpress/src/test/java/cn/net/pap/common/qlexpress/parser/JsonFunctionalExtractorTest.java)）

## 🔌 深度学习与外部存储 (MinIO & WebDAV & DL4J)
*   [MinioUtil](../pap4j-common/pap4j-common-minio/src/main/java/cn/net/pap/common/minio/util/MinioUtil.java): MinIO 对象存储桶与文件标准操作器。（测试验证见 [MinioUtilTest](../pap4j-common/pap4j-common-minio/src/test/java/cn/net/pap/common/minio/util/MinioUtilTest.java)）
*   [WebDavUtil](../pap4j-common/pap4j-common-webdav/src/main/java/cn/net/pap/common/webdav/WebDavUtil.java): WebDAV 协议远程存储交互工具。（测试验证见 [WebDavTest](../pap4j-common/pap4j-common-webdav/src/test/java/cn/net/pap/common/webdav/WebDavTest.java)）
*   [Deeplearning4jUtilss](../pap4j-common/pap4j-common-deeplearning4j/src/main/java/cn/net/pap/common/deeplearning4j/Deeplearning4jUtilss.java): Deeplearning4j 本地神经网络前向推理工具。

## ⚙️ 异步与并发调度 (Worker Framework)
*   [TaskExecutor](../pap4j-common/pap4j-common-worker/src/main/java/cn/net/pap/common/worker/executor/TaskExecutor.java): 并发执行器与异步任务跟踪模型。（测试验证见 [TaskExecutorTest](../pap4j-common/pap4j-common-worker/src/test/java/cn/net/pap/common/worker/simple/TaskExecutorTest.java)）
*   [SimpleMaster](../pap4j-common/pap4j-common-worker/src/main/java/cn/net/pap/common/worker/simple/SimpleMaster.java) / [SimpleWorker](../pap4j-common/pap4j-common-worker/src/main/java/cn/net/pap/common/worker/simple/SimpleWorker.java): 核心 Master-Worker 并行计算模型抽象。（测试验证见 [SimpleMasterTest](../pap4j-common/pap4j-common-worker/src/test/java/cn/net/pap/common/worker/simple/SimpleMasterTest.java)）

## 🔌 Spring Boot Starters 增强工具 (Starters Utilities)
*   [MilvusUtilss](../pap4j-boot3-starters/pap4j-boot3-starters-milvus/src/main/java/cn/net/pap/milvus/MilvusUtilss.java): Milvus 向量数据库数据交互与向量相似度检索工具。（测试验证见 [MilvusTest](../pap4j-boot3-starters/pap4j-boot3-starters-milvus/src/test/java/cn/net/pap/milvus/MilvusTest.java) 和 [MilvusTextSimilarityTest](../pap4j-boot3-starters/pap4j-boot3-starters-milvus/src/test/java/cn/net/pap/milvus/MilvusTextSimilarityTest.java)）
*   [GstoreConnector](../pap4j-boot3-starters/pap4j-boot3-starters-gStore/src/main/java/jgsc/GstoreConnector.java): gStore 图数据库的 Java 连接与 SPARQL 语句执行封装。（测试验证见 [GStoreTest](../pap4j-boot3-starters/pap4j-boot3-starters-gStore/src/test/java/cn/net/pap/gstore/GStoreTest.java)）
*   [TaskExecutorUtil](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/main/java/cn/net/pap/task/executor/TaskExecutorUtil.java): 独立高并发线程池任务执行器，内置拒绝策略捕获、超时监控及中断防日志风暴策略。（测试验证见 [TaskExecutorTest](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/test/java/cn/net/pap/task/TaskExecutorTest.java)）
*   [DynamicTaskExecutorUtil](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/main/java/cn/net/pap/task/util/DynamicTaskExecutorUtil.java): 包含异步执行、排队能力、线程池参数化调整的异步调度工具。（测试验证见 [DynamicTaskExecutorUtilTest](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/test/java/cn/net/pap/task/DynamicTaskExecutorUtilTest.java)）
*   [RetryUtil](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/main/java/cn/net/pap/task/retry/RetryUtil.java): 声明式优雅重试组件，支持断路器机制配合。（测试验证见 [RetryUtilTest](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/test/java/cn/net/pap/task/RetryUtilTest.java)）
*   [WebClientUtil](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/main/java/cn/net/pap/task/webclient/WebClientUtil.java): 响应式 Spring WebClient 的底层超时及自动重试封装。（测试验证见 [WebClientTest](../pap4j-boot3-starters/pap4j-boot3-starters-task/src/test/java/cn/net/pap/task/WebClientTest.java)）
*   [LogbackConfigurationUtil](../pap4j-boot3-starters/pap4j-boot3-starters-logback/src/main/java/cn/net/pap/logback/util/LogbackConfigurationUtil.java): 运行时 Logback 日志上下文属性的动态重载与配置工具。
*   [LoggerLevelUtil](../pap4j-boot3-starters/pap4j-boot3-starters-logback/src/main/java/cn/net/pap/logback/util/LoggerLevelUtil.java): 在线运行时动态查询及设置 Logback 中指定包/类日志级别的管理工具。
*   [ReqResLoggerReplayTest](../pap4j-boot3-starters/pap4j-boot3-starters-logback/src/test/java/cn/net/pap/logback/filter/ReqResLoggerReplayTest.java): 日志录制与回放单测最佳实践用例，演示了如何通过 [ReqResLoggerHttpFilter](../pap4j-boot3-starters/pap4j-boot3-starters-logback/src/main/java/cn/net/pap/logback/filter/ReqResLoggerHttpFilter.java)（集成了 OOM 防护、安全内容裁剪、二进制及 SSE 直通的全局过滤器）自动录制 400/500 异常 JSON 快照，并在不启动 Tomcat 端口的情况下直接通过 MockMvc 100% 重现与测试回放。
*   [QuartzUtils](../pap4j-boot3-starters/pap4j-boot3-starters-quartz/src/main/java/cn/net/pap/quartz/util/QuartzUtils.java): 快速注册与调配 Quartz 任务触发器及作业的控制工具。（测试验证见 [QuartzTest](../pap4j-boot3-starters/pap4j-boot3-starters-quartz/src/test/java/cn/net/pap/quartz/QuartzTest.java)）

## 💡 业务与最佳实践工具 (Example Utilities)
*   [SimpleRateLimiter](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/main/java/cn/net/pap/example/proguard/util/SimpleRateLimiter.java): 内存滑动窗口的高并发速率限制器（限流组件）。
*   [SpringUtils](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/main/java/cn/net/pap/example/proguard/util/SpringUtils.java): 静态直接存取与调用 Spring ApplicationContext 及其 Bean 的全局门面类。（测试验证见 [ProguardTest](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/test/java/cn/net/pap/example/proguard/ProguardTest.java)）
*   [NumberSegmentUtil](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/main/java/cn/net/pap/example/proguard/util/NumberSegmentUtil.java): 高级号段步长式分布式 ID 发号发牌规则处理类。（测试验证见 [NumberSegmentTest](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/test/java/cn/net/pap/example/proguard/NumberSegmentTest.java)）
*   [SearchUtil](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/main/java/cn/net/pap/example/proguard/util/SearchUtil.java): JPA 动态复杂 Criteria 多条件聚合解析与通用拼装器。（测试验证见 [ProguardTest](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/test/java/cn/net/pap/example/proguard/ProguardTest.java)）

## 👁️ AI 协同与运行时诊断 (AI & Run-time Diagnostics)
*   [TakeScreenshotTest](../pap4j-common/pap4j-common-spider/src/test/java/cn/net/pap/common/spider/html/TakeScreenshotTest.java): Headless 截图及 Console/Network 日志获取工具。支持前端页面渲染、交互与网络日志诊断，在无图沙箱环境下为 AI 提供“视觉之眼”。
*   [JSONAPIController](../pap4j-boot3-example/pap4j-boot3-example-dynamic-form/src/main/java/cn/net/pap/example/dynamic/form/controller/JSONAPIController.java): 高精度语义匹配 Mock 转发控制器。支持 JSON/Query 无序比对、二进制流模拟、状态码与延时自定义；配合 [mock-api.html](../pap4j-boot3-example/pap4j-boot3-example-dynamic-form/src/main/resources/static/mock-api.html) 支持 cURL 导入实现离线业务联调。使用用例见 [MockApiServiceTests](../pap4j-boot3-example/pap4j-boot3-example-dynamic-form/src/test/java/cn/net/pap/example/dynamic/form/MockApiServiceTests.java)。
*   [ApiRouterCatalogExporterTest](../pap4j-boot3-example/pap4j-boot3-example-proguard/src/test/java/cn/net/pap/example/proguard/diagnostics/ApiRouterCatalogExporterTest.java): Springdoc OpenAPI 接口契约自动生成器。单测编译运行时自动导出最新 OpenAPI 标准 JSON 文件至项目根目录的 `.ai/openapi/` 文件夹中（以当前子模块名称命名），在无图沙箱环境下为 AI 提供“API 契约活地图”。
    *   **多模块同步触发命令**（适用于 PowerShell）：`mvn clean test "-Dtest=ApiRouterCatalogExporterTest" "-Dsurefire.failIfNoSpecifiedTests=false"` （可在项目根目录下执行，同步触发所有子模块中的该单测并生成对应的 `openapi.json`，且不会因其他子模块中找不到该测试类而导致构建失败）。
*   [GetThreadsWithFullStackTraceUtil](../pap4j-boot3-starters/pap4j-boot3-starters-quartz/src/main/java/cn/net/pap/quartz/util/GetThreadsWithFullStackTraceUtil.java): JVM 活跃线程级堆栈深层诊断分析工具。在排查死锁或高 CPU 耗时线程时为 AI 提供深层堆栈视图。
*   [pap4j-common-test](../pap4j-common/pap4j-common-test/README.md): JUnit 5 单测运行监听与诊断工具子模块。子模块引入 `cn.net.pap:pap4j-common-test` 依赖后，单测执行失败时将自动捕捉并提取精简堆栈写入指定诊断文件，协助 AI 代理在无头终端环境下极速定位并自动修复单元测试故障。
