package cn.net.pap.example.spring.ai;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.FloatBuffer;
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
 * <p>对应 {@code HETEROGENEOUS_JPG_GOVERNANCE_PIPELINE_DESIGN.md} §4.5「备选实现：专用版面分析小模型」的
 * Java 集成路径 1（{@code com.microsoft.onnxruntime:onnxruntime} 直接加载 .onnx）。
 * 读取 {@code src/test/resources/test_doc_page.jpg}（HG/T 5982 标准页：标题 + 段落 + 大表格混排），返回版式分析结果。</p>
 *
 * <h3>一、模型与预处理契约</h3>
 * <ul>
 *   <li><b>模型</b>：{@code src/main/resources/onnx/layout_cdla.onnx} —— PP-Layout <b>PicoDet</b> 检测模型（CDLA 10 类），
 *       固定输入 {@code image float32 [1,3,800,608]}；输出 4 组分类头 {@code [1,N,10]} + 4 组 DFL 回归头 {@code [1,N,32]}
 *       （stride 8/16/32/64，reg_max=7，N=ceil(H/s)×ceil(W/s)=7600/1900/475/130）。<b>裸头输出</b>，
 *       后处理在本测试内完成：DFL softmax 期望值 <b>×stride</b> → anchor 中心加减距离 → 阈值 0.5 → NMS 0.5。</li>
 *   <li><b>预处理</b>：拉伸 resize 到 608x800 → /255 → ImageNet mean/std（0.485/0.456/0.406，0.229/0.224/0.225）→ CHW。
 *       <b>关键坑</b>：PicoDet 回归目标按 stride 归一化，DFL 期望值必须 ×stride，否则框坍缩成 ~3px 碎条。</li>
 *   <li><b>输出</b>：遵循设计文档 §4.2 契约 —— bbox 归一化到 {@code [0,1000]} 整数、顺序 {@code [左,上,右,下]}，
 *       附带 {@code pageType} / {@code type} / {@code confidence}，以 JSON + ASCII 版面图打印。
 *       CDLA 10 类：text / title / figure / figure_caption / table / table_caption / header / footer / reference / equation。</li>
 * </ul>
 *
 * <h3>二、运行方式</h3>
 * <ul>
 *   <li><b>IDEA</b>：右键直接运行本测试。若报 {@code UnsatisfiedLinkError: ...onnxruntime.dll: 动态链接库(DLL)初始化例程失败}，
 *       是项目 SDK（JBR 17.0.7）自带 MSVC CRT 过旧所致 —— 修复与完整排查见技能
 *       {@code .ai/skills/native-lib-load-debug/SKILL.md}（Run ▸ Edit Configurations ▸ Modify options ▸ JRE 切已安装的标准 JDK 17+）。</li>
 *   <li><b>Maven</b>（模块目录下；工作区有未提交修改时跳过 git-commit-id 校验）：
 *       <pre>{@code mvn "-Ddefault.skip=true" "-Dtest=LayoutAnalysisOnnxTest" test}</pre></li>
 *   <li><b>不经 mvn 直跑</b>：IDEA 等价方式（java + JUnit Console Launcher）的完整命令见技能 native-lib-load-debug。</li>
 * </ul>
 */
public class LayoutAnalysisOnnxTest {

    private static final Logger log = LoggerFactory.getLogger(LayoutAnalysisOnnxTest.class);

    /** 模型固定输入尺寸（CHW，来自模型元数据 image [1,3,800,608]） */
    private static final int MODEL_H = 800;
    private static final int MODEL_W = 608;

    /** PaddleDetection NormalizeImage：/255 后按 ImageNet mean/std 归一化 */
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    /** PicoDet 多尺度输出对应的 stride（与模型输出锚点数 ceil(H/s)*ceil(W/s)=7600/1900/475/130 对应） */
    private static final int[] STRIDES = {8, 16, 32, 64};
    /** DFL 分布回归分箱数 = REG_MAX+1（回归头每坐标 8 个 bin → 4×8=32） */
    private static final int REG_MAX = 7;

    /** CDLA 10 类（PaddleOCR 输出顺序） */
    private static final String[] CDLA_CLASSES = {
            "text", "title", "figure", "figure_caption", "table", "table_caption",
            "header", "footer", "reference", "equation"
    };

    private static final float SCORE_THRESHOLD = 0.5f;
    private static final float NMS_IOU = 0.5f;

    private static final String MODEL_RESOURCE = "onnx/layout_cdla.onnx";
    private static final String IMAGE_RESOURCE = "test_doc_page.jpg";

    private static byte[] modelBytes;
    private static ObjectMapper objectMapper;

    /** 一次检测：类 id、置信度、输入坐标空间 [x0,y0,x1,y1] */
    private record Detection(int clsId, float score, float x0, float y0, float x1, float y1) {}

    @BeforeAll
    static void init() throws Exception {
        // ---------- 环境变量问题：JDK 版本强校验 ----------
        String javaVersion = System.getProperty("java.version");
        log.info("java.version = {}", javaVersion);
        if (javaVersion.startsWith("1.")) {
            throw new IllegalStateException(
                    "当前 JDK 为 " + javaVersion + "，本测试需要 JDK 17+。请检查 JAVA_HOME / IDE 运行时 JRE 配置。");
        }

        objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        try (InputStream in = LayoutAnalysisOnnxTest.class.getClassLoader().getResourceAsStream(MODEL_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("classpath 找不到 " + MODEL_RESOURCE);
            }
            modelBytes = in.readAllBytes();
        }
        log.info("模型加载成功：{}，{} bytes", MODEL_RESOURCE, modelBytes.length);
    }

    @Test
    @DisplayName("test_doc_page.jpg 版式分析：读取图像 → onnxruntime 推理 → 输出区域图（[0,1000] bbox）")
    void layoutAnalysisOnTestDocPage() throws Exception {
        BufferedImage image;
        try (InputStream in = LayoutAnalysisOnnxTest.class.getClassLoader().getResourceAsStream(IMAGE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("classpath 找不到 " + IMAGE_RESOURCE);
            }
            image = ImageIO.read(in);
        }
        int srcW = image.getWidth();
        int srcH = image.getHeight();
        log.info("源图像：{}，{} x {} px", IMAGE_RESOURCE, srcW, srcH);

        // 预处理：拉伸 resize 到 608x800 + ImageNet 归一化 → CHW float[]
        long preStart = System.currentTimeMillis();
        float[] inputData = preprocess(image);
        log.info("预处理耗时：{} ms", System.currentTimeMillis() - preStart);

        List<LayoutRegion> regions = runInference(inputData);

        // 组装设计文档 §4.2 区域图契约结果
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

        log.info("\n==================== 版式分析结果（layout_cdla.onnx）====================\n{}",
                objectMapper.writeValueAsString(regionMap));
        log.info("版面 ASCII 示意（# 表格 / t 文字 / T 标题 / h 页眉 / o 页脚 / . 空白）：\n{}", renderAscii(regions));

        // 该标准页至少含一个大表格 + 正文文字，检出非空且含表格/文字即通过
        assertTrue(!regions.isEmpty(), "应至少检出 1 个版面区域");
        boolean hasTableOrText = regions.stream().anyMatch(r -> r.type.equals("table") || r.type.equals("text"));
        assertTrue(hasTableOrText, "应检出 table 或 text 区域，实际：" + regions);
    }

    /** onnxruntime 推理 + PicoDet 后处理 */
    private List<LayoutRegion> runInference(float[] inputData) throws Exception {
        long inferStart = System.currentTimeMillis();
        OrtEnvironment env = createOrtEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions();
             OrtSession session = env.createSession(modelBytes, options);
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

                // 归一化到 [0,1000] + 按阅读顺序（上→下、左→右）排序
                List<LayoutRegion> regions = new ArrayList<>();
                int id = 1;
                for (Detection d : kept) {
                    int l = Math.round(d.x0() / MODEL_W * 1000f);
                    int t = Math.round(d.y0() / MODEL_H * 1000f);
                    int r = Math.round(d.x1() / MODEL_W * 1000f);
                    int b = Math.round(d.y1() / MODEL_H * 1000f);
                    regions.add(new LayoutRegion(id++, CDLA_CLASSES[d.clsId()], d.score(),
                            new int[]{l, t, r, b}));
                }
                regions.sort(Comparator.comparingInt((LayoutRegion x) -> x.bbox[1])
                        .thenComparingInt(x -> x.bbox[0]));
                // 排序后重排 id
                for (int i = 0; i < regions.size(); i++) {
                    regions.get(i).id = i + 1;
                }

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
     * （Windows「动态链接库(DLL)初始化例程失败」），纯 Java staging 无法绕过；完整排查见技能
     * {@code .ai/skills/native-lib-load-debug/SKILL.md}。</p>
     */
    private static OrtEnvironment createOrtEnvironment() {
        try {
            return OrtEnvironment.getEnvironment();
        } catch (Throwable t) {
            String javaHome = System.getProperty("java.home", "");
            String javaVendor = System.getProperty("java.vendor", "");
            String javaVersion = System.getProperty("java.version", "");
            boolean isJbr = javaHome.toLowerCase().contains("jbr")
                    || javaVendor.toLowerCase().contains("jetbrains");
            String detail;
            if (isJbr) {
                detail = """
                          根因: 当前为 JetBrains Runtime(JBR)，其自带的 MSVC 运行库(msvcp140/vcruntime140)版本过旧，与 onnxruntime 1.22.0 的 onnxruntime.dll 不兼容（DLL 初始化例程失败）。已复现: JBR 17.0.7 下 onnxruntime 1.19.2/1.20.0/1.22.0 全部失败；改用标准 JDK 17+（如 Adoptium / Oracle OpenJDK）同一测试即通过。
                          修复方案（任选其一）:
                            1) IDEA: Project Structure ▸ Project ▸ SDK 添加标准 JDK 17+（如 Adoptium 17+），并把 SDK 从 jbr-17 切换为它（或仅对测试: Run ▸ Edit Configurations ▸ Modify options ▸ JRE ▸ 选已安装的 JDK 17+）；
                            2) 升级 JBR 到 2025 年之后的新版（自带新版 CRT）；
                            3) 命令行: 用兼容的 JDK 17+ + JUnit Console Launcher 直跑（完整命令见技能 native-lib-load-debug）。
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

    /** 单尺度解码：DFL 软max期望值 → [l,t,r,b] 距离 → 以 anchor 中心加减得到 [x0,y0,x1,y1] */
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

    /** DFL 回归头第 q 个坐标（0=l,1=t,2=r,3=b）的期望距离 */
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

    /** 拉伸 resize 到 608x800 + /255 + ImageNet 归一化 → CHW */
    private static float[] preprocess(BufferedImage src) {
        BufferedImage resized = new BufferedImage(MODEL_W, MODEL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, MODEL_W, MODEL_H, null);
        g.dispose();

        float[] data = new float[3 * MODEL_H * MODEL_W];
        for (int y = 0; y < MODEL_H; y++) {
            for (int x = 0; x < MODEL_W; x++) {
                int rgb = resized.getRGB(x, y);
                int rr = (rgb >> 16) & 0xFF;
                int gg = (rgb >> 8) & 0xFF;
                int bb = rgb & 0xFF;
                int off = y * MODEL_W + x;
                data[off] = (rr / 255f - MEAN[0]) / STD[0];
                data[off + MODEL_H * MODEL_W] = (gg / 255f - MEAN[1]) / STD[1];
                data[off + 2 * MODEL_H * MODEL_W] = (bb / 255f - MEAN[2]) / STD[2];
            }
        }
        return data;
    }

    /** 依据区域构成推导整页 pageType（设计文档 §4.1 枚举） */
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

    /** 粗略 ASCII 版面示意，便于肉眼核对区域分布 */
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

    /** 版式分析结果区域：id / type / confidence / bbox([0,1000] [左,上,右,下]) */
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
