package cn.net.pap.common.jsonorm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

        OutputRoot output = OcrConverter.convert(objectMapper.readValue(jsonInput, InputRoot.class));

        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
    }

    /**
     * 领域模型 & 核心转换逻辑 (OcrConverter)
     */
    public static class OcrConverter {

        /**
         * 简易转换重载（不传元数据时使用默认值）
         */
        public static OutputRoot convert(InputRoot input) {
            return convert(input, "", 0, 0);
        }

        /**
         * 标准转换方法（支持传入外部元数据）
         */
        public static OutputRoot convert(InputRoot input, String imagePageName, int height, int width) {
            if (input == null || input.data() == null) {
                return new OutputRoot(Collections.emptyList(), imagePageName, height, width);
            }

            InputData data = input.data();
            List<LineInfo> lineInfos = new ArrayList<>();

            // 1. 解析 textArr (多维数组平铺)
            if (data.textArr() != null) {
                for (List<TextItem> col : data.textArr()) {
                    if (col == null) continue;
                    for (TextItem item : col) {
                        lineInfos.add(buildLineInfo(item));
                    }
                }
            }

            // 2. 解析 outTextArr (单字行归一化)
            if (data.outTextArr() != null) {
                for (CharItem item : data.outTextArr()) {
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
        private static LineInfo buildLineInfo(TextItem item) {
            BBox lineBox = BBox.of(item.bbox());
            boolean isAncientNote = item.categ() == 3;

            List<OcrChar> chars = new ArrayList<>();
            if (item.textList() != null) {
                for (CharItem ci : item.textList()) {
                    chars.add(new OcrChar(ci.txt(), null, ci.score() != null ? (int) Math.round(ci.score() * 1000) : 0, BBox.of(ci.bbox())));
                }
            }
            return new LineInfo(isAncientNote, lineBox, chars);
        }

        /**
         * 将 outTextArr 中的单字包装为单行 LineInfo
         */
        private static LineInfo buildLineInfoFromSingleChar(CharItem ci) {
            BBox box = BBox.of(ci.bbox());
            OcrChar ocrChar = new OcrChar(ci.txt(), null, ci.score() != null ? (int) Math.round(ci.score() * 1000) : 0, box);
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
    // 输入 DTO 定义 (Java Records)
    // ==========================================

    public record InputRoot(InputData data) {
    }

    public record InputData(List<LanItem> lanArr, List<List<TextItem>> textArr, List<CharItem> outTextArr) {
    }

    public record LanItem(int categ, List<Integer> bbox, String id) {
    }

    public record TextItem(int categ, List<Integer> bbox, String id, List<CharItem> textList,
                           @JsonProperty("hang_id") int hangId, @JsonProperty("isHead") Boolean isHead) {
    }

    public record CharItem(List<Integer> bbox, String txt, Double score) {
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