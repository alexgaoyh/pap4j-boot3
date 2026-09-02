package cn.net.pap.example.spring.ai;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h3>版式分析（Layout Analysis）单元测试 —— 本地 ONNX 推理，零 VLM 调用</h3>
 *
 * <p>基于 {@code com.microsoft.onnxruntime:onnxruntime} 直接加载 .onnx 模型推理，
 * 读取 {@code src/test/resources/test_doc_page.jpg}（HG/T 5982 标准页：标题 + 段落 + 大表格混排），
 * 输出版式分析结果（bbox / type / confidence）。</p>
 *
 * <h3>一、模型与预处理契约</h3>
 * <ul>
 *   <li><b>CDLA 模型</b>：{@code src/main/resources/onnx/layout_cdla.onnx} —— PP-Layout <b>PicoDet</b>（CDLA 10 类），
 *       固定输入 {@code image float32 [1,3,800,608]}；输出 4 组分类头 {@code [1,N,10]} + 4 组 DFL 回归头 {@code [1,N,32]}
 *       （stride 8/16/32/64，reg_max=7，N=ceil(H/s)×ceil(W/s)=7600/1900/475/130）。<b>裸头输出</b>，
 *       后处理在本测试内完成：DFL softmax 期望值 <b>×stride</b> → anchor 中心加减距离 → 阈值 0.5 → NMS 0.5。</li>
 *   <li><b>V3 模型</b>：{@code src/main/resources/onnx/PP-DocLayoutV3.onnx} —— PP-DocLayoutV3（RT-DETR，25 类），
 *       三输入 im_shape/image/scale_factor（800×800 固定；im_shape 必须传目标边长 [800,800]、
 *       scale_factor=[800/原H,800/原W]，模型内部据此把框还原到原图坐标），输出 (maxDets,7)=[label,score,x0,y0,x1,y1,read_order]，
 *       直接回归框坐标 → 无 DFL 分箱 / 无「顶格 7」臂长上限。</li>
 *   <li><b>预处理</b>：拉伸 resize 到模型输入尺寸（CDLA 608×800 / V3 800×800，宽×高）→ /255 →
 *       ImageNet mean/std（0.485/0.456/0.406，0.229/0.224/0.225）→ CHW。
 *       <b>关键坑</b>：PicoDet 回归目标按 stride 归一化，DFL 期望值必须 ×stride，否则框坍缩成 ~3px 碎条。</li>
 *   <li><b>输出</b>：bbox 归一化到 {@code [0,1000]} 整数、顺序 {@code [左,上,右,下]}，附带 {@code pageType} / {@code type} /
 *       {@code confidence}，以 JSON + ASCII 版面图打印。
 *       CDLA 10 类：text / title / figure / figure_caption / table / table_caption / header / footer / reference / equation。</li>
 * </ul>
 *
 * <h3>二、运行方式</h3>
 * <ul>
 *   <li><b>IDEA</b>：右键直接运行本测试。若报 {@code UnsatisfiedLinkError: ...onnxruntime.dll: 动态链接库(DLL)初始化例程失败}，
 *       是项目 SDK（JBR 17.0.7）自带 MSVC CRT 过旧所致 —— 在 Run ▸ Edit Configurations ▸ Modify options ▸ JRE
 *       切换为标准 JDK 17+ 即可（详细诊断见 {@link #createOrtEnvironment()}）。</li>
 *   <li><b>Maven</b>（模块目录下；工作区有未提交修改时跳过 git-commit-id 校验）：
 *       <pre>{@code mvn "-Ddefault.skip=true" "-Dtest=LayoutAnalysisOnnxTest" test}</pre></li>
 *   <li><b>模型</b>：模型文件体积较大、未纳入版本控制；缺失时对应测试自动跳过（跳过原因见
 *       {@link #cdlaMissingHint()} / {@link #v3MissingHint()}），按提示下载并放入
 *       {@code src/main/resources/onnx/} 后重跑即可。</li>
 * </ul>
 */
public class LayoutAnalysisOnnxTest {

    private static final Logger log = LoggerFactory.getLogger(LayoutAnalysisOnnxTest.class);

    /**
     * 模型固定输入尺寸（CHW，来自模型元数据 image [1,3,800,608]）
     */
    private static final int MODEL_H = 800;
    private static final int MODEL_W = 608;

    /**
     * PaddleDetection NormalizeImage：/255 后按 ImageNet mean/std 归一化
     */
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    /**
     * PicoDet 多尺度输出对应的 stride（与模型输出锚点数 ceil(H/s)*ceil(W/s)=7600/1900/475/130 对应）
     */
    private static final int[] STRIDES = {8, 16, 32, 64};
    /**
     * DFL 分布回归分箱数 = REG_MAX+1（回归头每坐标 8 个 bin → 4×8=32）
     */
    private static final int REG_MAX = 7;

    /**
     * CDLA 10 类（PaddleOCR 输出顺序）
     */
    private static final String[] CDLA_CLASSES = {
            "text", "title", "figure", "figure_caption", "table", "table_caption",
            "header", "footer", "reference", "equation"
    };

    private static final float SCORE_THRESHOLD = 0.5f;
    private static final float NMS_IOU = 0.5f;

    /**
     * PP-DocLayoutV3 (RT-DETR) 模型契约：输入 im_shape/image/scale_factor（PaddleDetection 约定，800×800 固定；
     * im_shape 必须传目标边长 [800,800]，scale_factor=[800/原H, 800/原W]，模型内部据此把框还原到原图坐标），
     * 输出 (maxDets,7) = [label, score, x0,y0,x1,y1,read_order]，框已在原图像素坐标；
     * RT-DETR 直接回归框坐标（无 DFL 分箱）→ 无「顶格 7」臂长上限。
     */
    private static final String V3_MODEL_RESOURCE = "onnx/PP-DocLayoutV3.onnx";
    /**
     * PP-DocLayoutV3 输入边长（use_dynamic_shape=false，固定 800×800）
     */
    private static final int V3_INPUT = 800;
    /**
     * 与 PP-DocLayoutV3 官方推理 draw_threshold 一致
     */
    private static final float V3_CONF = 0.5f;
    /**
     * PP-DocLayoutV3 25 类（PaddleDetection label_list 顺序）
     */
    private static final String[] V3_CLASSES = {
            "abstract", "algorithm", "aside_text", "chart", "content", "display_formula",
            "doc_title", "figure_title", "footer", "footer_image", "footnote", "formula_number",
            "header", "header_image", "image", "inline_formula", "number", "paragraph_title",
            "reference", "reference_content", "seal", "table", "text", "vertical_text", "vision_footnote"
    };

    /**
     * CDLA 模型资源（未纳入版本控制，缺失时对应测试自动跳过，见 {@link #cdlaMissingHint()}）
     */
    private static final String MODEL_RESOURCE = "onnx/layout_cdla.onnx";
    private static final String IMAGE_RESOURCE = "test_doc_page.jpg";

    private static final ObjectMapper objectMapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 一次检测：类 id、置信度、输入坐标空间 [x0,y0,x1,y1]
     */
    private record Detection(int clsId, float score, float x0, float y0, float x1, float y1) {
    }

    @Test
    @DisplayName("test_doc_page.jpg 版式分析：读取图像 → onnxruntime 推理 → 输出区域图（[0,1000] bbox）")
    void layoutAnalysisOnTestDocPage() throws Exception {
        Assumptions.assumeTrue(LayoutAnalysisOnnxTest.class.getClassLoader().getResource(MODEL_RESOURCE) != null,
                cdlaMissingHint());

        BufferedImage image = loadTestImage();
        log.info("源图像：{}，{} x {} px", IMAGE_RESOURCE, image.getWidth(), image.getHeight());

        // 预处理：拉伸 resize 到 608×800（宽×高） + ImageNet 归一化 → CHW float[]
        long preStart = System.currentTimeMillis();
        float[] inputData = preprocess(image);
        log.info("预处理耗时：{} ms", System.currentTimeMillis() - preStart);

        List<LayoutRegion> regions = runInference(inputData);

        // 输出契约结果 + ASCII 版面图 + 可视化 PNG（含存在性断言）
        logAndRender("layout_cdla.onnx", image, regions);

        // 该标准页至少含一个大表格 + 正文文字，检出含表格/文字即通过
        assertHasTableOrText(regions, "layout_cdla.onnx");
    }

    /**
     * PP-DocLayoutV3 (RT-DETR) 版面分析：800×800 拉伸 + /255 + ImageNet 归一化 → PaddleDetection 三输入 → DETR 输出。
     * RT-DETR 直接回归框坐标（无 DFL 分箱）→ 无「顶格 7」臂长上限；25 类含 seal/header/footer/formula。
     * 模型未拷贝到 resources 时自动跳过（见 {@link #v3MissingHint()}）。
     */
    @Test
    @DisplayName("PP-DocLayoutV3 (RT-DETR) 版式分析：800×800 → PaddleDetection 三输入 → DETR 输出")
    void layoutAnalysisWithPpDocLayoutV3() throws Exception {
        Assumptions.assumeTrue(LayoutAnalysisOnnxTest.class.getClassLoader().getResource(V3_MODEL_RESOURCE) != null,
                v3MissingHint());

        BufferedImage image = loadTestImage();
        int srcW = image.getWidth();
        int srcH = image.getHeight();

        long preStart = System.currentTimeMillis();
        V3Inputs v3 = preprocessV3(image);
        log.info("PP-DocLayoutV3 预处理：800x800 /255 + ImageNet 归一化，im_shape=[{},{}]，scale_factor=[{},{}]，耗时 {} ms",
                v3.imShape()[0], v3.imShape()[1], v3.scaleFactor()[0], v3.scaleFactor()[1],
                System.currentTimeMillis() - preStart);

        List<LayoutRegion> regions = runV3Inference(v3.chw(), v3.imShape(), v3.scaleFactor(), srcW, srcH);

        // 输出契约结果 + ASCII 版面图 + 可视化 PNG（含存在性断言）
        logAndRender("PP-DocLayoutV3", image, regions);

        assertHasTableOrText(regions, "PP-DocLayoutV3");
    }

    /**
     * 读取测试图像 test_doc_page.jpg（classpath 缺失时抛异常）
     */
    private static BufferedImage loadTestImage() throws IOException {
        try (InputStream in = LayoutAnalysisOnnxTest.class.getClassLoader().getResourceAsStream(IMAGE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("classpath 找不到 " + IMAGE_RESOURCE);
            }
            return ImageIO.read(in);
        }
    }

    /**
     * 组装区域图契约结果：pageType + regions(id/type/bbox/confidence)，两模型路径共用
     */
    private static Map<String, Object> buildRegionMap(List<LayoutRegion> regions) {
        Map<String, Object> regionMap = new LinkedHashMap<>();
        regionMap.put("pageType", derivePageType(regions));
        List<Map<String, Object>> regionList = new ArrayList<>();
        for (LayoutRegion r : regions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("type", r.type);
            m.put("bbox", Arrays.asList(r.bbox[0], r.bbox[1], r.bbox[2], r.bbox[3]));
            m.put("confidence", Math.round(r.confidence * 100.0) / 100.0);
            regionList.add(m);
        }
        regionMap.put("regions", regionList);
        return regionMap;
    }

    /**
     * 打印版式 JSON + ASCII 版面图，并把框标回原图输出 PNG（含存在性断言），两模型路径共用
     */
    private static void logAndRender(String modelLabel, BufferedImage image, List<LayoutRegion> regions) throws Exception {
        log.info("\n==================== 版式分析结果（{}）====================\n{}",
                modelLabel, objectMapper.writeValueAsString(buildRegionMap(regions)));
        log.info("版面 ASCII 示意（# 表格 / t 文字 / T 标题 / h 页眉 / o 页脚 / F 图 / = 公式 / . 空白）：\n{}",
                renderAscii(regions));

        Path visPath = renderRegionsToTempDir(image, regions);
        log.info("{} 版面可视化 PNG：{}", modelLabel, visPath.toAbsolutePath());
        assertTrue(Files.exists(visPath), modelLabel + " 版面可视化 PNG 应已生成：" + visPath);
    }

    /**
     * 该标准页至少含一个大表格 + 正文文字：检出非空且含 table/text 即通过
     */
    private static void assertHasTableOrText(List<LayoutRegion> regions, String modelLabel) {
        assertTrue(!regions.isEmpty(), modelLabel + " 应至少检出 1 个版面区域");
        boolean hasTableOrText = regions.stream().anyMatch(r -> r.type.equals("table") || r.type.equals("text"));
        assertTrue(hasTableOrText, modelLabel + " 应检出 table 或 text 区域，实际：" + regions);
    }

    /**
     * 按阅读顺序（上→下、左→右）排序并重排 id，两个推理路径共用
     */
    private static void sortByReadingOrder(List<LayoutRegion> regions) {
        regions.sort(Comparator.comparingInt((LayoutRegion x) -> x.bbox[1])
                .thenComparingInt(x -> x.bbox[0]));
        for (int i = 0; i < regions.size(); i++) {
            regions.get(i).id = i + 1;
        }
    }

    /**
     * onnxruntime 推理 + PicoDet 后处理
     */
    private List<LayoutRegion> runInference(float[] inputData) throws Exception {
        byte[] model = readResourceBytes(MODEL_RESOURCE);
        long inferStart = System.currentTimeMillis();
        OrtEnvironment env = createOrtEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions();
             OrtSession session = env.createSession(model, options);
             OnnxTensor imageTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData),
                     new long[]{1, 3, MODEL_H, MODEL_W})) {

            // 打印模型元数据（首次运行核对契约）
            logSessionMetadata(session);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("image", imageTensor);

            try (OrtSession.Result result = session.run(inputs)) {
                // 按锚点数 N 将输出分桶为分类头 / 回归头
                Map<Long, float[][][]> clsByN = new HashMap<>();
                Map<Long, float[][][]> regByN = new HashMap<>();
                for (Map.Entry<String, OnnxValue> e : result) {
                    OnnxTensor tensor = (OnnxTensor) e.getValue();
                    float[][][] v = (float[][][]) tensor.getValue();
                    long n = v[0].length;
                    int channels = v[0][0].length;
                    if (channels == CDLA_CLASSES.length) {
                        clsByN.put(n, v);
                    } else if (channels == 4 * (REG_MAX + 1)) {
                        regByN.put(n, v);
                    } else {
                        log.warn("忽略未识别输出 {} channels={}", e.getKey(), channels);
                    }
                }

                List<Detection> detections = new ArrayList<>();
                for (int stride : STRIDES) {
                    int featH = (MODEL_H + stride - 1) / stride;
                    int featW = (MODEL_W + stride - 1) / stride;
                    long n = (long) featH * featW;
                    float[][][] cls = clsByN.get(n);
                    float[][][] reg = regByN.get(n);
                    if (cls == null || reg == null) {
                        log.warn("stride={} 缺少输出张量（cls={}, reg={}）", stride, cls != null, reg != null);
                        continue;
                    }
                    decodeStride(detections, stride, featH, featW, cls[0], reg[0]);
                }

                // 全局 NMS
                detections.sort((a, b) -> Float.compare(b.score(), a.score()));
                List<Detection> kept = new ArrayList<>();
                for (Detection d : detections) {
                    boolean suppressed = false;
                    for (Detection k : kept) {
                        if (iou(d, k) >= NMS_IOU) {
                            suppressed = true;
                            break;
                        }
                    }
                    if (!suppressed) {
                        kept.add(d);
                    }
                }

                // 归一化到 [0,1000]，按阅读顺序排序并重排 id（排序在 sortByReadingOrder 内完成）
                List<LayoutRegion> regions = new ArrayList<>();
                for (Detection d : kept) {
                    int l = Math.round(d.x0() / MODEL_W * 1000f);
                    int t = Math.round(d.y0() / MODEL_H * 1000f);
                    int r = Math.round(d.x1() / MODEL_W * 1000f);
                    int b = Math.round(d.y1() / MODEL_H * 1000f);
                    regions.add(new LayoutRegion(0, CDLA_CLASSES[d.clsId()], d.score(),
                            new int[]{l, t, r, b}));
                }
                sortByReadingOrder(regions);

                log.info("推理耗时：{} ms，检出 {} 个区域", System.currentTimeMillis() - inferStart, regions.size());
                return regions;
            }
        } finally {
            env.close();
        }
    }

    /**
     * 创建 OrtEnvironment，并在原生库加载失败时输出「JVM 版本 + 根因 + 修复方案」诊断。
     *
     * <p>本机已复现：项目 SDK（JBR 17.0.7）自带 MSVC CRT 过旧，与 onnxruntime 1.22.0 的 DLL 不兼容
     * （Windows「动态链接库(DLL)初始化例程失败」），纯 Java 侧无法绕过；改用标准 JDK 17+（如 Adoptium）即通过。</p>
     */
    private static OrtEnvironment createOrtEnvironment() {
        // JDK 版本兜底校验（仅拦截 1.x 老版本；JBR 原生库 CRT 兼容问题见下方 catch）
        String javaVersion = System.getProperty("java.version", "");
        log.info("java.version = {}", javaVersion);
        if (javaVersion.startsWith("1.")) {
            throw new IllegalStateException(
                    "当前 JDK 为 " + javaVersion + "，本测试需要 JDK 17+。请检查 JAVA_HOME / IDE 运行时 JRE 配置。");
        }
        try {
            return OrtEnvironment.getEnvironment();
        } catch (Throwable t) {
            log.error("onnxruntime 原生库加载失败（OrtEnvironment.getEnvironment()）: ", t);
            String javaHome = System.getProperty("java.home", "");
            String javaVendor = System.getProperty("java.vendor", "");
            boolean isJbr = javaHome.toLowerCase().contains("jbr")
                            || javaVendor.toLowerCase().contains("jetbrains");
            String detail;
            if (isJbr) {
                detail = """
                          根因: 当前为 JetBrains Runtime(JBR)，其自带的 MSVC 运行库(msvcp140/vcruntime140)版本过旧，与 onnxruntime 1.22.0 的 onnxruntime.dll 不兼容（DLL 初始化例程失败）。已复现: JBR 17.0.7 下 onnxruntime 1.19.2/1.20.0/1.22.0 全部失败；改用标准 JDK 17+（如 Adoptium / Oracle OpenJDK）同一测试即通过。
                          修复方案（任选其一）:
                            1) IDEA: Project Structure ▸ Project ▸ SDK 添加标准 JDK 17+（如 Adoptium 17+），并把 SDK 从 jbr-17 切换为它（或仅对测试: Run ▸ Edit Configurations ▸ Modify options ▸ JRE ▸ 选已安装的 JDK 17+）；
                            2) 升级 JBR 到 2025 年之后的新版（自带新版 CRT）；
                            3) 命令行: 用兼容的 JDK 17+ + JUnit Console Launcher 直跑。
                        """;
            } else {
                detail = """
                          可能原因: 系统缺少或版本不符的 MSVC 运行库（VC++ 2015-2022 Redistributable x64），或当前 JDK 自带 CRT 过旧。
                          建议: 安装最新 VC++ 2015-2022 Redistributable (x64)，或换用更新的 JDK。
                        """;
            }

            String message = """
                    onnxruntime 原生库加载失败（OrtEnvironment.getEnvironment()）
                      当前 JVM: %s %s @ %s
                    %s  原始异常: %s: %s
                    """.formatted(
                    javaVendor, javaVersion, javaHome,
                    detail,
                    rootCause(t).getClass().getSimpleName(), rootCause(t).getMessage());

            throw new IllegalStateException(message, t);
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    /**
     * 单尺度解码：DFL 软max期望值 → [l,t,r,b] 距离 → 以 anchor 中心加减得到 [x0,y0,x1,y1]
     */
    private void decodeStride(List<Detection> out, int stride, int featH, int featW,
                              float[][] cls, float[][] reg) {
        for (int i = 0; i < cls.length; i++) {
            // 最佳类别
            int bestCls = -1;
            float bestScore = 0f;
            for (int c = 0; c < CDLA_CLASSES.length; c++) {
                if (cls[i][c] > bestScore) {
                    bestScore = cls[i][c];
                    bestCls = c;
                }
            }
            if (bestScore < SCORE_THRESHOLD) {
                continue;
            }

            // DFL 解码：每坐标 8 个 bin 做 softmax，求期望值 = 归一化距离，再 ×stride 还原像素
            // （PicoDet GFL 回归目标按 stride 归一化，reg_max=7 即单边最大 7×stride 像素）
            float l = dfl(reg, i, 0) * stride;
            float t = dfl(reg, i, 1) * stride;
            float r = dfl(reg, i, 2) * stride;
            float b = dfl(reg, i, 3) * stride;

            int iy = i / featW;
            int ix = i % featW;
            float cx = (ix + 0.5f) * stride;
            float cy = (iy + 0.5f) * stride;

            float x0 = Math.max(0f, cx - l);
            float y0 = Math.max(0f, cy - t);
            float x1 = Math.min(MODEL_W, cx + r);
            float y1 = Math.min(MODEL_H, cy + b);
            if (x1 - x0 < 1f || y1 - y0 < 1f) {
                continue;
            }
            out.add(new Detection(bestCls, bestScore, x0, y0, x1, y1));
        }
    }

    /**
     * DFL 回归头第 q 个坐标（0=l,1=t,2=r,3=b）的期望距离
     */
    private static float dfl(float[][] reg, int anchorIdx, int q) {
        int base = q * (REG_MAX + 1);
        float max = Float.NEGATIVE_INFINITY;
        for (int k = 0; k <= REG_MAX; k++) {
            max = Math.max(max, reg[anchorIdx][base + k]);
        }
        float[] soft = new float[REG_MAX + 1];
        float sum = 0f;
        for (int k = 0; k <= REG_MAX; k++) {
            soft[k] = (float) Math.exp(reg[anchorIdx][base + k] - max);
            sum += soft[k];
        }
        float expect = 0f;
        for (int k = 0; k <= REG_MAX; k++) {
            expect += (soft[k] / sum) * k;
        }
        return expect;
    }

    private static float iou(Detection a, Detection b) {
        float x0 = Math.max(a.x0(), b.x0());
        float y0 = Math.max(a.y0(), b.y0());
        float x1 = Math.min(a.x1(), b.x1());
        float y1 = Math.min(a.y1(), b.y1());
        float inter = Math.max(0f, x1 - x0) * Math.max(0f, y1 - y0);
        float areaA = (a.x1() - a.x0()) * (a.y1() - a.y0());
        float areaB = (b.x1() - b.x0()) * (b.y1() - b.y0());
        return inter / (areaA + areaB - inter + 1e-9f);
    }

    /**
     * 拉伸 resize 到 w×h → /255 → ImageNet mean/std 归一化 → CHW float[]（两模型共用；通道步长 = w*h）
     */
    private static float[] resizeAndNormalize(BufferedImage src, int w, int h) {
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        float[] chw = new float[3 * w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = resized.getRGB(x, y);
                int off = y * w + x;
                chw[off] = (((rgb >> 16) & 0xFF) / 255f - MEAN[0]) / STD[0];
                chw[off + w * h] = (((rgb >> 8) & 0xFF) / 255f - MEAN[1]) / STD[1];
                chw[off + 2 * w * h] = ((rgb & 0xFF) / 255f - MEAN[2]) / STD[2];
            }
        }
        return chw;
    }

    /**
     * CDLA 预处理：拉伸 resize 到 608×800（宽×高） + /255 + ImageNet 归一化 → CHW
     */
    private static float[] preprocess(BufferedImage src) {
        return resizeAndNormalize(src, MODEL_W, MODEL_H);
    }

    /**
     * 依据区域构成推导整页 pageType：blank（空）/ 单一类型 / mixed（多类型）
     */
    private static String derivePageType(List<LayoutRegion> regions) {
        if (regions.isEmpty()) {
            return "blank";
        }
        long typeCount = regions.stream().map(r -> r.type).distinct().count();
        if (typeCount == 1) {
            return regions.get(0).type;
        }
        return "mixed";
    }

    /**
     * 粗略 ASCII 版面示意，便于肉眼核对区域分布
     */
    private static String renderAscii(List<LayoutRegion> regions) {
        int cols = 60;
        int rows = 40;
        char[][] grid = new char[rows][cols];
        for (char[] row : grid) {
            Arrays.fill(row, '.');
        }
        for (LayoutRegion r : regions) {
            char ch = switch (r.type) {
                case "table", "table_caption" -> '#';
                case "title" -> 'T';
                case "header" -> 'h';
                case "footer" -> 'o';
                case "text" -> 't';
                case "figure", "figure_caption" -> 'F';
                case "equation" -> '=';
                case "doc_title", "paragraph_title", "figure_title" -> 'T';
                case "display_formula", "inline_formula", "formula_number" -> '=';
                case "seal" -> 'S';
                case "image", "chart", "header_image", "footer_image" -> 'F';
                case "footnote" -> 'f';
                case "aside_text", "content", "vertical_text", "abstract" -> 't';
                case "reference", "reference_content" -> 'r';
                default -> '?';
            };
            int l = r.bbox[0] * cols / 1000;
            int rt = Math.min(cols - 1, r.bbox[2] * cols / 1000);
            int top = r.bbox[1] * rows / 1000;
            int bot = Math.min(rows - 1, r.bbox[3] * rows / 1000);
            for (int y = top; y <= bot; y++) {
                for (int x = l; x <= rt; x++) {
                    grid[y][x] = ch;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (char[] row : grid) {
            sb.append("  ").append(new String(row)).append('\n');
        }
        return sb.toString();
    }

    /**
     * PP-DocLayoutV3 三输入：CHW 归一化图像 + im_shape([800,800]) + scale_factor([800/原H, 800/原W])
     */
    private record V3Inputs(float[] chw, float[] imShape, float[] scaleFactor) {
    }

    /**
     * PP-DocLayoutV3 预处理：拉伸到 800×800 → /255 → ImageNet mean/std 归一化 → CHW。
     * 模型图内 image 直接进 Conv，无内置归一化，必须外部归一化；
     * im_shape 传目标边长 800（不是原图尺寸），scale_factor = [800/原H, 800/原W]，模型内部按
     * im_shape/scale_factor 还原出原图坐标（传错 im_shape 会导致所有框被放大 ~原尺寸/800 倍）。
     */
    private static V3Inputs preprocessV3(BufferedImage src) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        float[] chw = resizeAndNormalize(src, V3_INPUT, V3_INPUT);
        // im_shape = [800, 800]（目标边长，模型据此还原坐标）；scale_factor = [800/原H, 800/原W]
        float[] imShape = {V3_INPUT, V3_INPUT};
        float[] scaleFactor = {(float) V3_INPUT / srcH, (float) V3_INPUT / srcW};
        return new V3Inputs(chw, imShape, scaleFactor);
    }

    /**
     * PP-DocLayoutV3 推理：PaddleDetection 三输入（im_shape/image/scale_factor），输出 (maxDets,7) =
     * [label, score, x0,y0,x1,y1,read_order]，框已在原图像素坐标（模型后处理按 scale_factor 还原到原图），
     * 只需阈值过滤，无 NMS。模型为自包含 onnx（无外部权重），可直接 byte[] 加载。
     */
    private static List<LayoutRegion> runV3Inference(float[] chw, float[] imShape, float[] scaleFactor,
                                                     int srcW, int srcH) throws Exception {
        OrtEnvironment env = createOrtEnvironment();
        try {
            byte[] model = readResourceBytes(V3_MODEL_RESOURCE);
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions();
                 OrtSession session = env.createSession(model, options);
                 OnnxTensor imageTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw),
                         new long[]{1, 3, V3_INPUT, V3_INPUT});
                 OnnxTensor imShapeTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(imShape), new long[]{1, 2});
                 OnnxTensor scaleTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(scaleFactor), new long[]{1, 2})) {

                logSessionMetadata(session);
                Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
                inputs.put("im_shape", imShapeTensor);
                inputs.put("image", imageTensor);
                inputs.put("scale_factor", scaleTensor);

                try (OrtSession.Result result = session.run(inputs)) {
                    // 模型有 3 个输出：(maxDets,7) 检测 / num_dets / 语义掩码。显式挑二维且末维=7 的检测输出，
                    // 不依赖输出 map 的首元素顺序（键序非契约保证）。
                    String detOut = session.getOutputInfo().entrySet().stream()
                            .filter(e -> {
                                if (e.getValue().getInfo() instanceof TensorInfo ti) {
                                    long[] shape = ti.getShape();
                                    return shape.length == 2 && shape[1] == 7;
                                }
                                return false;
                            })
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("模型缺少 (maxDets,7) 检测输出"));
                    OnnxTensor t = (OnnxTensor) result.get(detOut).orElseThrow(
                            () -> new IllegalStateException("模型输出缺失：" + detOut));
                    // 输出为二维 (maxDets,7) = [label, score, x0,y0,x1,y1,read_order]
                    float[][] v = (float[][]) t.getValue();
                    log.info("PP-DocLayoutV3 输出 {}：shape={}，首行={}", detOut,
                            Arrays.toString(((TensorInfo) t.getInfo()).getShape()),
                            v.length > 0 ? Arrays.toString(v[0]) : "[]");

                    List<LayoutRegion> regions = new ArrayList<>();
                    for (float[] row : v) {
                        float score = row[1];
                        int label = (int) row[0];
                        if (score < V3_CONF || label < 0 || label >= V3_CLASSES.length) {
                            continue;
                        }
                        float x0 = row[2], y0 = row[3], x1 = row[4], y1 = row[5];
                        if (x1 - x0 < 1f || y1 - y0 < 1f) {
                            continue;
                        }
                        int l = Math.round(x0 / srcW * 1000f);
                        int top = Math.round(y0 / srcH * 1000f);
                        int r = Math.round(x1 / srcW * 1000f);
                        int b = Math.round(y1 / srcH * 1000f);
                        regions.add(new LayoutRegion(0, V3_CLASSES[label], score, new int[]{l, top, r, b}));
                    }
                    sortByReadingOrder(regions);
                    log.info("PP-DocLayoutV3 检出 {} 个区域", regions.size());
                    return regions;
                }
            }
        } finally {
            env.close();
        }
    }

    /**
     * 从 classpath 读取模型字节（自包含 onnx 可直接用）
     */
    private static byte[] readResourceBytes(String resource) throws Exception {
        try (InputStream in = LayoutAnalysisOnnxTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("classpath 找不到 " + resource);
            }
            return in.readAllBytes();
        }
    }

    /**
     * CDLA 模型未就绪时打印的下载 + 放置指引（模型未纳入版本控制，缺失时测试自动跳过）
     */
    private static String cdlaMissingHint() {
        return """
                模型未就绪，跳过 CDLA 测试。请下载 PP-Layout CDLA 自包含 onnx（PicoDet，输入 608×800（宽×高），10 类）：
                  https://huggingface.co/SWHL/RapidStructure/resolve/main/layout/layout_cdla.onnx
                直接放入 src/main/resources/onnx/layout_cdla.onnx 即可（输入 image float32 [1,3,800,608] 的裸头输出）。
                """;
    }

    /**
     * PP-DocLayoutV3 模型未就绪时打印的下载 + 放置指引
     */
    private static String v3MissingHint() {
        return """
                模型未就绪，跳过 PP-DocLayoutV3 测试。请下载自包含 ONNX（约 130MB，无外部权重，可直接 byte[] 加载）：
                  https://huggingface.co/alex-dinh/PP-DocLayoutV3-ONNX/resolve/main/PP-DocLayoutV3.onnx
                放入 src/main/resources/onnx/PP-DocLayoutV3.onnx 即可（无需 .data 外部权重）。
                预处理契约：800x800 拉伸 + /255 + ImageNet mean/std(0.485/0.456/0.406, 0.229/0.224/0.225)；
                三输入 im_shape=[800,800]、image=CHW 归一化张量、scale_factor=[800/原H, 800/原W]；输出 (300,7)。
                """;
    }

    /**
     * 每种版面类型一个固定颜色（覆盖 CDLA 10 类 + V3 25 类），便于可视化区分
     */
    private static Color colorForType(String type) {
        return switch (type) {
            case "title" -> new Color(0xE53935);          // 红
            case "text" -> new Color(0x1E88E5);           // 蓝
            case "table" -> new Color(0x43A047);          // 绿
            case "table_caption" -> new Color(0x8E24AA);  // 紫
            case "figure" -> new Color(0xFB8C00);         // 橙
            case "figure_caption" -> new Color(0x8D6E63); // 棕
            case "header" -> new Color(0x00ACC1);         // 青
            case "footer" -> new Color(0x546E7A);         // 蓝灰
            case "reference" -> new Color(0x6D4C41);      // 深棕
            case "equation" -> new Color(0x3949AB);       // 靛蓝
            // PP-DocLayoutV3 25 类配色
            case "doc_title", "paragraph_title" -> new Color(0xE53935); // 红（标题）
            case "figure_title" -> new Color(0xFB8C00);                 // 橙
            case "display_formula", "inline_formula", "formula_number" -> new Color(0x3949AB); // 靛蓝（公式）
            case "seal" -> new Color(0xC62828);                        // 深红（印章）
            case "footnote" -> new Color(0x6D4C41);                    // 深棕
            case "image", "chart", "header_image", "footer_image" -> new Color(0xFB8C00); // 橙（图）
            case "aside_text", "content", "vertical_text", "abstract" -> new Color(0x1E88E5); // 蓝（文本类）
            case "reference_content" -> new Color(0x6D4C41);           // 深棕（参考文献）
            default -> new Color(0x757575);                            // 灰
        };
    }

    /**
     * 可视化：将版面区域 bbox（[0,1000] [左,上,右,下]）逆映射回原图像素，在原图上画框并标注
     * 「#id 类型 置信度」，输出 PNG 到系统临时目录（java.io.tmpdir 下新建子目录）。
     *
     * <p>坐标逆映射说明：模型把原图拉伸到输入尺寸（CDLA 608×800 / V3 800×800，宽×高），bbox 按比例归一化到
     * {@code [0,1000]}（x→宽、y→高），因此反向映射为 {@code px = bbox * 原图宽高 / 1000}，与拉伸方向互逆，
     * 无需宽高比修正。</p>
     *
     * @param original 原始图像（只读，绘制在拷贝上，不修改入参）
     * @param regions  版面区域列表（含类型 + [0,1000] bbox）
     * @return 生成的 PNG 文件路径
     */
    private static Path renderRegionsToTempDir(BufferedImage original, List<LayoutRegion> regions) throws Exception {
        int srcW = original.getWidth();
        int srcH = original.getHeight();

        // 在原始尺寸的拷贝上绘制，避免污染调用方的原图
        BufferedImage annotated = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = annotated.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(original, 0, 0, null);

            // 线宽、字号随原图尺寸缩放，避免大图上糊成一条
            float stroke = Math.max(2f, srcH / 400f);
            int fontSize = Math.max(14, srcH / 80);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));

            for (LayoutRegion r : regions) {
                // [0,1000] → 原图像素
                int x = r.bbox[0] * srcW / 1000;
                int y = r.bbox[1] * srcH / 1000;
                int x2 = r.bbox[2] * srcW / 1000;
                int y2 = r.bbox[3] * srcH / 1000;

                Color c = colorForType(r.type);
                g.setColor(c);
                g.setStroke(new BasicStroke(stroke));
                g.drawRect(x, y, Math.max(1, x2 - x), Math.max(1, y2 - y));

                // 标签「#id 类型 置信度」：优先放框上方，贴近上边界时改放框内
                String label = "#" + r.id + " " + r.type + " " + (Math.round(r.confidence * 100) / 100.0);
                FontMetrics fm = g.getFontMetrics();
                int labelW = fm.stringWidth(label);
                int labelH = fm.getHeight();
                int lx = Math.max(0, Math.min(x, srcW - labelW));
                int ly = y - labelH - 4;
                if (ly < 0) {
                    ly = y + 2;
                }
                g.fillRect(lx, ly, labelW, labelH);
                g.setColor(Color.WHITE);
                g.drawString(label, lx, ly + fm.getAscent());
            }
        } finally {
            g.dispose();
        }

        // 输出到系统临时目录下新建的子目录：{java.io.tmpdir}/pap-layout-vis-*/layout_annotated.png
        Path dir = Files.createTempDirectory("pap-layout-vis-");
        Path out = dir.resolve("layout_annotated.png");
        ImageIO.write(annotated, "png", out.toFile());
        log.info("版面可视化已输出：{}（{} 个区域）", out.toAbsolutePath(), regions.size());
        return out;
    }

    private static void logSessionMetadata(OrtSession session) throws Exception {
        for (Map.Entry<String, NodeInfo> e : session.getInputInfo().entrySet()) {
            TensorInfo ti = (TensorInfo) e.getValue().getInfo();
            log.info("模型 INPUT  name={} type={} shape={}", e.getKey(), ti.type, Arrays.toString(ti.getShape()));
        }
        for (Map.Entry<String, NodeInfo> e : session.getOutputInfo().entrySet()) {
            TensorInfo ti = (TensorInfo) e.getValue().getInfo();
            log.info("模型 OUTPUT name={} type={} shape={}", e.getKey(), ti.type, Arrays.toString(ti.getShape()));
        }
    }

    /**
     * 版式分析结果区域：id / type / confidence / bbox([0,1000] [左,上,右,下])
     */
    private static final class LayoutRegion {
        int id;
        final String type;
        final float confidence;
        final int[] bbox;

        LayoutRegion(int id, String type, float confidence, int[] bbox) {
            this.id = id;
            this.type = type;
            this.confidence = confidence;
            this.bbox = bbox;
        }

        @Override
        public String toString() {
            return "#" + id + " " + type + " conf=" + Math.round(confidence * 100) / 100.0
                   + " bbox=" + Arrays.toString(bbox);
        }
    }
}
