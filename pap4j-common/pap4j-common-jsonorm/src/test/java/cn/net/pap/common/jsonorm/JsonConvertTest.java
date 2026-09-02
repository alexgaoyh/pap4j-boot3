package cn.net.pap.common.jsonorm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JsonConvertTest - 针对复杂/多变 JSON 结构转换的设计示范单元测试
 *
 * <p>【设计思想说明】
 * 本测试展示了“面向多变 JSON 的轻量级转换方案”（基于 JsonPath 提取 + 仅定义输出 DTO）。
 *
 * <p>1. 痛点背景：
 * 传统的反序列化方案要求定义一整套与源 JSON 结构完全一致的 Java DTO 类（例如之前的 InputRoot, InputData 等）。
 * 当输入 JSON 格式庞大且包含大量无关字段、或者后续有多种异构的输入格式时，会产生大量一次性使用的冗余类定义，导致代码臃肿。
 *
 * <p>2. 本类所用设计思路：
 * <ul>
 *   <li><b>“只定义输出，不定义输入”</b>：彻底干掉输入端所有的 Record/DTO 类定义。
 *   <li><b>“JsonPath 动态提取”</b>：利用 {@link com.jayway.jsonpath.JsonPath} 语法对源 JSON 进行按需定位和提取（如使用 {@code $.data.textArr[*][*]} 实现多维数组的就地平铺/扁平化），直接拿到基础类型构成的 Map/List。
 *   <li><b>“强类型输出装配”</b>：在 Converter 中将提取的数据直接映射组装到强类型的输出 DTO（{@link OutputRoot} 等记录类）中，保障后续业务消费时的类型安全。
 * </ul>
 */
public class JsonConvertTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void convertTest() throws Exception {
        // 1. 模拟输入数据
        String jsonInput = """
                {
                  "data": {
                    "lanArr": [
                      {"categ":2,"bbox":[2885,1200,3662,6150],"id":"a1"},
                      {"categ":2,"bbox":[21,1259,1624,6111],"id":"a2"}
                    ],
                    "textArr": [
                      [
                        {"categ":0,"bbox":[3278,1307,3529,5227],"id":"1","textList":[
                          {"bbox":[3268,1379,3514,1582],"txt":"〓","score":1},
                          {"bbox":[3275,1582,3507,1800],"txt":"東","score":1}
                        ],"hang_id":0,"isHead":true},
                        {"bbox":[3413,5217,3549,6041],"id":"2","categ":3,"textList":[
                          {"bbox":[3411,5230,3562,5406],"txt":"泛","score":1}
                        ],"hang_id":0},
                        {"bbox":[3262,5218,3389,6046],"id":"3","categ":3,"textList":[
                          {"bbox":[3241,5223,3391,5393],"txt":"王","score":1}
                        ],"hang_id":0},
                        {"bbox":[3092,1335,3237,4582],"id":"4","categ":3,"textList":[
                          {"bbox":[3084,1379,3227,1576],"txt":"𠘇","score":1}
                        ],"hang_id":1},
                        {"bbox":[2934,1367,3072,4167],"id":"5","categ":3,"textList":[
                          {"bbox":[2907,1372,3070,1596],"txt":"覽","score":1},
                          {"bbox":[2920,3939,3063,4123],"txt":"也","score":1}
                        ],"hang_id":1},
                        {"categ":0,"bbox":[2959,4547,3221,6053],"id":"6","textList":[
                          {"bbox":[2948,4550,3227,4788],"txt":"俛","score":1}
                        ],"hang_id":1}
                      ],
                      [
                        {"categ":0,"bbox":[1374,1996,1606,2647],"id":"7","textList":[
                          {"bbox":[1335,1986,1613,2210],"txt":"謝","score":1}
                        ],"hang_id":0}
                      ],
                      [
                        {"categ":0,"bbox":[2623,1370,2878,2009],"id":"8","textList":[
                          {"bbox":[2606,1358,2879,1596],"txt":"復","score":1}
                        ],"hang_id":0}
                      ]
                    ],
                    "outTextArr": [
                      {"bbox":[2142,2010,2252,2207],"txt":"月","score":1},
                      {"bbox":[2142,2221,2279,2418],"txt":"七","score":1}
                    ]
                  }
                }
                """;

        OutputRoot output = OcrConverter.convert(jsonInput);

        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
    }

    /**
     * 领域模型 & 核心转换逻辑 (OcrConverter)
     */
    public static class OcrConverter {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OcrConverter.class);

        /**
         * 简易转换重载（不传元数据时使用默认值）
         */
        public static OutputRoot convert(String jsonInput) {
            return convert(jsonInput, "", 0, 0);
        }

        /**
         * 标准转换方法（支持传入外部元数据）
         */
        @SuppressWarnings("unchecked")
        public static OutputRoot convert(String jsonInput, String imagePageName, int height, int width) {
            if (jsonInput == null || jsonInput.isBlank()) {
                return new OutputRoot(Collections.emptyList(), imagePageName, height, width);
            }

            DocumentContext ctx = JsonPath.parse(jsonInput);
            List<LineInfo> lineInfos = new ArrayList<>();

            // 1. 解析 textArr (多维数组平铺)
            List<Map<String, Object>> textItems = null;
            try {
                textItems = ctx.read("$.data.textArr[*][*]");
            } catch (Exception e) {
                log.error("解析 textArr (多维数组平铺) 失败", e);
                // Ignore if path not found
            }
            if (textItems != null) {
                for (Map<String, Object> item : textItems) {
                    if (item == null) continue;
                    lineInfos.add(buildLineInfo(item));
                }
            }

            // 2. 解析 outTextArr (单字行归一化)
            List<Map<String, Object>> outTextItems = null;
            try {
                outTextItems = ctx.read("$.data.outTextArr[*]");
            } catch (Exception e) {
                log.error("解析 outTextArr (单字行归一化) 失败", e);
                // Ignore if path not found
            }
            if (outTextItems != null) {
                for (Map<String, Object> item : outTextItems) {
                    if (item == null) continue;
                    lineInfos.add(buildLineInfoFromSingleChar(item));
                }
            }

            // 3. 计算 Block 包围盒 (对所有行的 BBox 求并集)
            BBox blockBox = lineInfos.stream().map(LineInfo::bbox).filter(Objects::nonNull).reduce(BBox::union).orElse(null);

            String blockRect = blockBox != null ? blockBox.toRectString() : "";

            // 4. 平铺构建全局 Chars 并注入制表符/换行符
            List<OcrChar> flatChars = buildFlatChars(lineInfos);

            // 5. 组装输出结构
            Block block = new Block(2, blockRect, lineInfos, flatChars);
            PageBox pageBox = new PageBox(List.of(block));

            // 6. 返回带有全局元数据的结果
            return new OutputRoot(List.of(pageBox), imagePageName, height, width);
        }

        /**
         * 从普通的 TextItem 构建 LineInfo
         */
        @SuppressWarnings("unchecked")
        private static LineInfo buildLineInfo(Map<String, Object> item) {
            List<Integer> bboxList = (List<Integer>) item.get("bbox");
            BBox lineBox = BBox.of(bboxList);
            
            Object categObj = item.get("categ");
            boolean isAncientNote = categObj != null && ((Number) categObj).intValue() == 3;

            List<OcrChar> chars = new ArrayList<>();
            List<Map<String, Object>> textList = (List<Map<String, Object>>) item.get("textList");
            if (textList != null) {
                for (Map<String, Object> ci : textList) {
                    List<Integer> charBbox = (List<Integer>) ci.get("bbox");
                    String txt = (String) ci.get("txt");
                    Number score = (Number) ci.get("score");
                    int confidence = score != null ? (int) Math.round(score.doubleValue() * 1000) : 0;
                    chars.add(new OcrChar(txt, null, confidence, BBox.of(charBbox)));
                }
            }
            return new LineInfo(isAncientNote, lineBox, chars);
        }

        /**
         * 将 outTextArr 中的单字包装为单行 LineInfo
         */
        @SuppressWarnings("unchecked")
        private static LineInfo buildLineInfoFromSingleChar(Map<String, Object> ci) {
            List<Integer> bboxList = (List<Integer>) ci.get("bbox");
            BBox box = BBox.of(bboxList);
            String txt = (String) ci.get("txt");
            Number score = (Number) ci.get("score");
            int confidence = score != null ? (int) Math.round(score.doubleValue() * 1000) : 0;
            OcrChar ocrChar = new OcrChar(txt, null, confidence, box);
            return new LineInfo(false, box, List.of(ocrChar));
        }

        /**
         * 平铺所有字符，并在头部加 \t ，行间加 \n
         */
        private static List<OcrChar> buildFlatChars(List<LineInfo> lineInfos) {
            List<OcrChar> flat = new ArrayList<>();
            if (lineInfos.isEmpty()) {
                return flat;
            }

            // 头部注入 \t
            flat.add(OcrChar.createMarker("\t"));

            for (int i = 0; i < lineInfos.size(); i++) {
                flat.addAll(lineInfos.get(i).Chars());
                // 行与行之间注入 \n，最后一行后不加
                if (i < lineInfos.size() - 1) {
                    flat.add(OcrChar.createMarker("\n"));
                }
            }
            return flat;
        }
    }

    // ==========================================
    // 领域对象: BBox (坐标与范围计算边界自治)
    // ==========================================

    public record BBox(int x1, int y1, int x2, int y2) {
        public static BBox of(List<Integer> bboxList) {
            if (bboxList == null || bboxList.size() < 4) {
                return null;
            }
            return new BBox(bboxList.get(0), bboxList.get(1), bboxList.get(2), bboxList.get(3));
        }

        public String toRectString() {
            return String.format("%d, %d, %d, %d", x1, y1, x2 - x1, y2 - y1);
        }

        public BBox union(BBox other) {
            if (other == null) return this;
            return new BBox(Math.min(this.x1, other.x1), Math.min(this.y1, other.y1), Math.max(this.x2, other.x2), Math.max(this.y2, other.y2));
        }
    }

    // ==========================================
    // 输出 DTO 定义 (Java Records)
    // ==========================================

    public record OutputRoot(@JsonProperty("PageBoxs") List<PageBox> pageBoxs,
                             @JsonProperty("ImagePageName") String imagePageName, @JsonProperty("Height") int height,
                             @JsonProperty("Width") int width) {
    }

    public record PageBox(@JsonProperty("Blocks") List<Block> blocks) {
    }

    public record Block(@JsonProperty("RgnType") int rgnType, @JsonProperty("rect") String rect,
                        @JsonProperty("LineInfos") List<LineInfo> lineInfos,
                        @JsonProperty("Chars") List<OcrChar> chars) {
    }

    // 内部临时携带 BBox 实体，不参与 JSON 序列化
    public record LineInfo(@JsonProperty("IsAncientNote") boolean isAncientNote,
                           @com.fasterxml.jackson.annotation.JsonIgnore BBox bbox,
                           @JsonProperty("Chars") List<OcrChar> Chars) {
        @JsonProperty("rect")
        public String getRect() {
            return bbox != null ? bbox.toRectString() : "";
        }
    }

    // 内部临时携带 BBox 实体，不参与 JSON 序列化
    public record OcrChar(@JsonProperty("Code") String code, @JsonProperty("CharImage") String charImage,
                          @JsonProperty("ConfidenceLevel") int confidenceLevel,
                          @com.fasterxml.jackson.annotation.JsonIgnore BBox bbox) {
        @JsonProperty("rect")
        public String getRect() {
            return bbox != null ? bbox.toRectString() : "";
        }

        public static OcrChar createMarker(String code) {
            return new OcrChar(code, null, 999, null);
        }
    }
}