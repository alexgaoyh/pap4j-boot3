package cn.net.pap.common.pdf;

import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * iText PDF 文本层字形与字号/坐标异常检测单元测试
 * <p>
 * 用于定位 PDF 文本层中“字号非法 (如 1.#INF00) / 坐标溢出”的异常字形，解析对应字体资源、CID、Unicode 码位及上下文环境。
 */
public class ItextCheckTest {

    private static final Logger log = LoggerFactory.getLogger(ItextCheckTest.class);

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\[[^\\]]*\\]|<<[\\s\\S]*?>>|/[^\\s/\\[\\]<>()]+|<[0-9A-Fa-f]*>|\\([^)]*\\)|\\S+");
    private static final Pattern BF_CHAR_BLOCK = Pattern.compile("beginbfchar(.*?)endbfchar", Pattern.DOTALL);
    private static final Pattern BF_CHAR_ENTRY = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>");
    private static final Pattern BF_RANGE_BLOCK = Pattern.compile("beginbfrange(.*?)endbfrange", Pattern.DOTALL);
    private static final Pattern BF_RANGE_ENTRY = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>");
    private static final Pattern TJ_HEX_ENTRY = Pattern.compile("<([0-9A-Fa-f]+)>");

    /**
     * PDF 文本层字形元素记录
     *
     * @param ch   映射得到的文本字符
     * @param font 字体资源名称
     * @param cid  CID 码位
     * @param x    横坐标 Tm (x)
     * @param y    纵坐标 Tm (y)
     * @param size 字号文本
     * @param bad  是否属于非法/损坏字形
     */
    public record GlyphItem(String ch, String font, int cid, double x, double y, String size, boolean bad) {
    }

    /**
     * 检测 PDF 页面中字号非法或坐标异常的字形
     *
     * @throws Exception 检测处理过程中的异常
     */
    @Test
    @DisplayName("检测 PDF 页面中字号非法或坐标异常的字形")
    public void check1Test() throws Exception {
        File pdfFile = null;
        PdfReader reader = null;
        List<GlyphItem> items;
        try {
            pdfFile = TestResourceUtil.getFile("fontsize-out.pdf");
            Assertions.assertTrue(pdfFile.exists(), "测试资源文件不存在: fontsize-out.pdf");

            try (java.io.InputStream is = new java.io.FileInputStream(pdfFile)) {
                reader = new PdfReader(is);
                int targetPage = 1;
                Map<String, Map<Integer, String>> cmaps = extractFontCmaps(reader, targetPage);
                byte[] content = reader.getPageContent(targetPage);
                Assertions.assertNotNull(content, "页面内容流不能为空");

                items = parseContent(new String(content, StandardCharsets.ISO_8859_1), cmaps);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (pdfFile != null && pdfFile.exists()) {
                boolean deleted = pdfFile.delete();
                Assertions.assertTrue(deleted, "临时文件删除应成功: " + pdfFile.getName());
            }
        }

        Assertions.assertNotNull(items, "解析字形列表不应为空");

        String report = generateReport(items);
        log.info("[PDF-Check] 字形异常检测完成，总字形数: {}, 报告概要:\n{}", items.size(), report);
    }

    /**
     * 验证修复逻辑：将含有 1.#INF00 异常字号的 PDF 修复后，二次检测确认不再存在异常字形
     *
     * @throws Exception 测试执行异常
     */
    @Test
    @DisplayName("修复 PDF 页面中的非法数值并验证字形合法性")
    public void fixAndVerifyTest() throws Exception {
        File srcFile = null;
        File dstFile = null;
        PdfReader patchedReader = null;
        try {
            srcFile = TestResourceUtil.getFile("fontsize-out.pdf");
            Assertions.assertTrue(srcFile.exists(), "测试资源文件不存在: fontsize-out.pdf");

            dstFile = File.createTempFile("patched_", ".pdf");

            // 执行修复逻辑
            patchPdf(srcFile.getAbsolutePath(), dstFile.getAbsolutePath());
            Assertions.assertTrue(dstFile.exists() && dstFile.length() > 0, "修复后的目标文件应有效存在");

            // 重新读取修复后的文件，二次检测字形
            try (java.io.InputStream is = new java.io.FileInputStream(dstFile)) {
                patchedReader = new PdfReader(is);
                int targetPage = 1;
                Map<String, Map<Integer, String>> cmaps = extractFontCmaps(patchedReader, targetPage);
                byte[] content = patchedReader.getPageContent(targetPage);
                Assertions.assertNotNull(content, "修复后的页面内容流不能为空");

                List<GlyphItem> items = parseContent(new String(content, StandardCharsets.ISO_8859_1), cmaps);
                List<GlyphItem> badItems = items.stream().filter(GlyphItem::bad).toList();

                // 核心断言：修复后非法字形数应为 0
                Assertions.assertEquals(0, badItems.size(), "修复后不应再存在任何非法/溢出字形");
                log.info("[PDF-Check] 修复后二次校验通过，非法字形数量为 0，文本层总字形数: {}", items.size());
            }
        } finally {
            if (patchedReader != null) {
                patchedReader.close();
            }
            if (srcFile != null && srcFile.exists()) {
                boolean delSrc = srcFile.delete();
                Assertions.assertTrue(delSrc, "源文件删除应成功: " + srcFile.getName());
            }
            if (dstFile != null && dstFile.exists()) {
                boolean delDst = dstFile.delete();
                Assertions.assertTrue(delDst, "目标文件删除应成功: " + dstFile.getName());
            }
        }
    }

    /**
     * 修复 PDF 页面内容流中的非法数值（如 1.#INF00、极值 Tz 与溢出坐标）并输出修复后的 PDF
     *
     * @param src 输入 PDF 文件路径
     * @param dst 修复后输出的 PDF 文件路径
     * @throws Exception 处理或写出过程中的异常
     */
    public static void patchPdf(String src, String dst) throws Exception {
        PdfReader reader = null;
        PdfStamper stamper = null;
        try (java.io.InputStream is = new java.io.FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            reader = new PdfReader(is);

            // 1) 取出页面内容流（iText 自动解压 FlateDecode）
            byte[] content = reader.getPageContent(1);
            String s = new String(content, StandardCharsets.ISO_8859_1);

            // 2) 统计前面正确的字号并计算平均值
            double avgFontSize = calculateAverageFontSize(s);
            String avgSizeStr = String.format(java.util.Locale.ROOT, "%.6f", avgFontSize);
            log.info("[PDF-Patch] 统计得到平均字号: {}", avgSizeStr);

            // 3) 修复非法字号与异常 Tz 水平缩放
            s = s.replace("1.#INF00", avgSizeStr)
                    .replace("-2147483648 Tz", "100 Tz");

            // 4) 【可选调用】修复异常溢出坐标（通过前后字形中点动态插值恢复排版显示位置）
            s = interpolateBadCoordinates(s);

            byte[] fixed = s.getBytes(StandardCharsets.ISO_8859_1);

            // 5) 找到内容流对象（可能是单个引用，也可能是数组，如 [38 0 R]）
            PdfDictionary page = reader.getPageN(1);
            PdfObject contents = page.get(PdfName.CONTENTS);
            PdfObject arr = PdfReader.getPdfObject(contents);
            PRStream stream;
            if (arr != null && arr.isArray()) {
                stream = (PRStream) PdfReader.getPdfObject(((PdfArray) arr).getAsIndirectObject(0));
            } else {
                stream = (PRStream) arr;
            }

            // 4) setData 会自动去掉旧 /Filter、重新 Flate 压缩、更新 /Length
            if (stream != null) {
                stream.setData(fixed);
            }

            // 5) PdfStamper 写出并重建 xref
            stamper = new PdfStamper(reader, fos);
            stamper.close();
            stamper = null;
            log.info("[PDF-Patch] 修复并生成 PDF 成功: {}", dst);
        } finally {
            if (stamper != null) {
                try {
                    stamper.close();
                } catch (Exception e) {
                    log.error("[PDF-Patch] 关闭 PdfStamper 失败: ", e);
                }
            }
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * 统计 PDF 内容流中合法的字号并求其平均值
     *
     * @param contentStream 页面内容流文本
     * @return 平均字号（若无合法字号则默认兜底为 12.0）
     */
    private static double calculateAverageFontSize(String contentStream) {
        Matcher tfMatcher = Pattern.compile("(/\\S+)\\s+([^\\s]+)\\s+Tf").matcher(contentStream);
        List<Double> validSizes = new ArrayList<>();
        while (tfMatcher.find()) {
            String sizeStr = tfMatcher.group(2);
            try {
                double size = Double.parseDouble(sizeStr);
                if (size > 0 && !Double.isInfinite(size) && !Double.isNaN(size)) {
                    validSizes.add(size);
                }
            } catch (Exception ignored) {
                // 忽略如 1.#INF00 等非法字号
            }
        }
        return validSizes.isEmpty() ? 12.0 : validSizes.stream().mapToDouble(Double::doubleValue).average().orElse(12.0);
    }

    /**
     * 【可选函数】修复 PDF 内容流中的异常溢出坐标：
     * <p>
     * 通过扫描坏字（坐标绝对值溢出或非法的 Tm 矩阵），自动寻找其前后相邻的合法字形坐标，
     * 计算其中点位置插值进行替换，将偏离可视区域或被移出页面的字形精准放回版面中。
     *
     * @param contentStream 待修复的页面内容流文本
     * @return 修复坐标插值后的内容流文本
     */
    private static String interpolateBadCoordinates(String contentStream) {
        Pattern tmPattern = Pattern.compile("1\\.000000 0\\.000000 0\\.000000 1\\.000000 ([^\\s]+) ([^\\s]+) Tm");
        Matcher tmM = tmPattern.matcher(contentStream);
        List<int[]> badMatches = new ArrayList<>();
        List<double[]> coords = new ArrayList<>();
        while (tmM.find()) {
            double x = 0, y = 0;
            boolean isBad = false;
            try {
                x = Double.parseDouble(tmM.group(1));
                y = Double.parseDouble(tmM.group(2));
                if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y) || Math.abs(x) > 1e6 || Math.abs(y) > 1e6) {
                    isBad = true;
                }
            } catch (Exception e) {
                isBad = true;
            }
            if (isBad) {
                badMatches.add(new int[]{tmM.start(1), tmM.end(2), coords.size()});
            }
            coords.add(new double[]{x, y, isBad ? 1.0 : 0.0});
        }

        if (badMatches.isEmpty()) {
            return contentStream;
        }

        StringBuilder sb = new StringBuilder(contentStream);
        // 倒序替换以保证前面的索引不会失效
        for (int i = badMatches.size() - 1; i >= 0; i--) {
            int[] bm = badMatches.get(i);
            int coordIdx = bm[2];
            double prevX = 40.8, prevY = 600.0;
            double nextX = 40.8, nextY = 600.0;
            // 向上寻找前一个合法坐标
            for (int p = coordIdx - 1; p >= 0; p--) {
                if (coords.get(p)[2] == 0.0) {
                    prevX = coords.get(p)[0];
                    prevY = coords.get(p)[1];
                    break;
                }
            }
            // 向下寻找后一个合法坐标
            for (int n = coordIdx + 1; n < coords.size(); n++) {
                if (coords.get(n)[2] == 0.0) {
                    nextX = coords.get(n)[0];
                    nextY = coords.get(n)[1];
                    break;
                }
            }
            double fixedX = (prevX + nextX) / 2.0;
            double fixedY = (prevY + nextY) / 2.0;
            String fixedCoordStr = String.format(java.util.Locale.ROOT, "%.6f %.6f", fixedX, fixedY);
            sb.replace(bm[0], bm[1], fixedCoordStr);
            log.info("[PDF-Patch] 动态修复坏坐标 #{} 为中点坐标: {}", coordIdx, fixedCoordStr);
        }
        return sb.toString();
    }

    /**
     * 提取指定页面的字体资源及 ToUnicode CMap 映射表
     *
     * @param reader  PdfReader 实例
     * @param pageNum 页码（从 1 开始）
     * @return 字体名称与对应字符编码映射表
     * @throws Exception 资源读取或解析异常
     */
    private Map<String, Map<Integer, String>> extractFontCmaps(PdfReader reader, int pageNum) throws Exception {
        Map<String, Map<Integer, String>> fontCmaps = new LinkedHashMap<>();
        PdfDictionary page = reader.getPageN(pageNum);
        if (page == null) {
            return fontCmaps;
        }
        PdfDictionary resources = page.getAsDict(PdfName.RESOURCES);
        if (resources == null) {
            return fontCmaps;
        }
        PdfDictionary fonts = resources.getAsDict(PdfName.FONT);
        if (fonts == null) {
            return fontCmaps;
        }

        for (PdfName key : fonts.getKeys()) {
            PdfDictionary fd = (PdfDictionary) PdfReader.getPdfObject(fonts.get(key));
            Map<Integer, String> map = new HashMap<>();
            if (fd != null) {
                PdfObject tuo = fd.get(PdfName.TOUNICODE);
                if (tuo != null) {
                    byte[] cmb = PdfReader.getStreamBytes((PRStream) PdfReader.getPdfObject(tuo));
                    map = parseCMap(new String(cmb, StandardCharsets.ISO_8859_1));
                }
            }
            String name = key.toString().substring(1);
            fontCmaps.put(name, map);
            log.info("[PDF-Font] 解析字体资源 /{}，ToUnicode 映射码位数: {}", name, map.size());
        }
        return fontCmaps;
    }

    /**
     * 模拟 iText 内容流状态机，解析页面文本及字形位置属性
     *
     * @param text  内容流文本
     * @param cmaps 字体映射字典
     * @return 字形列表
     */
    private List<GlyphItem> parseContent(String text, Map<String, Map<Integer, String>> cmaps) {
        List<GlyphItem> items = new ArrayList<>();
        List<String[]> cur = new ArrayList<>();
        String font = null;
        String size = null;
        double tx = 0;
        double ty = 0;
        boolean fontBad = false;

        Matcher m = TOKEN_PATTERN.matcher(text);
        while (m.find()) {
            String t = m.group();
            String c = classify(t);
            if (!"op".equals(c)) {
                cur.add(new String[]{t, c});
                continue;
            }

            if ("Tf".equals(t)) {
                for (String[] o : cur) {
                    if ("name".equals(o[1])) {
                        font = o[0].substring(1);
                    }
                }
                size = null;
                fontBad = false;
                for (String[] o : cur) {
                    if ("number".equals(o[1])) {
                        size = o[0];
                    }
                    if ("badnum".equals(o[1])) {
                        size = o[0];
                        fontBad = true;
                    }
                }
            } else if ("Tm".equals(t)) {
                List<Double> nums = new ArrayList<>();
                for (String[] o : cur) {
                    if ("number".equals(o[1])) {
                        nums.add(Double.parseDouble(o[0]));
                    }
                }
                if (nums.size() >= 6) {
                    tx = nums.get(nums.size() - 2);
                    ty = nums.get(nums.size() - 1);
                }
            } else if ("Tj".equals(t) || "TJ".equals(t)) {
                List<String> hexes = new ArrayList<>();
                for (String[] o : cur) {
                    if ("string".equals(o[1]) && o[0].startsWith("<")) {
                        hexes.add(o[0]);
                    }
                    if ("array".equals(o[1])) {
                        Matcher h = TJ_HEX_ENTRY.matcher(o[0]);
                        while (h.find()) {
                            hexes.add("<" + h.group(1) + ">");
                        }
                    }
                }
                for (String h : hexes) {
                    String hex = h.substring(1, h.length() - 1).replaceAll("\\s", "");
                    for (int i = 0; i + 4 <= hex.length(); i += 4) {
                        int cid = Integer.parseInt(hex.substring(i, i + 4), 16);
                        String ch = cmaps.getOrDefault(font, Collections.emptyMap()).getOrDefault(cid, "?");
                        items.add(new GlyphItem(ch, font, cid, tx, ty, size, fontBad));
                    }
                }
            }
            cur.clear();
        }
        return items;
    }

    /**
     * 汇总异常字形并生成排查报告文本
     *
     * @param items 字形列表
     * @return 格式化检测报告
     */
    private String generateReport(List<GlyphItem> items) {
        StringBuilder out = new StringBuilder();
        List<Integer> badIdx = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).bad()) {
                badIdx.add(i);
            }
        }

        out.append(String.format("文本层共 %d 个字形；其中字号非法的: %d 个%n%n", items.size(), badIdx.size()));

        for (int idx : badIdx) {
            GlyphItem it = items.get(idx);
            String cp = it.ch().codePointCount(0, it.ch().length()) == 1 ? String.format("U+%04X", it.ch().codePointAt(0)) : "?";
            String uniName = it.ch().codePointCount(0, it.ch().length()) == 1 ? Character.getName(it.ch().codePointAt(0)) : "?";
            out.append("============================================================\n");
            out.append(String.format("第 %d 个字形%n", idx + 1));
            out.append(String.format("  字体资源 : /%s%n", it.font()));
            out.append(String.format("  CID      : 0x%04X%n", it.cid()));
            out.append(String.format("  Unicode  : %s   %s%n", cp, uniName));
            out.append(String.format("  字符     : %s%n", it.ch()));
            out.append(String.format("  字号     : %s   <-- 非法(1.#INF00)%n", it.size()));
            out.append(String.format("  坐标 Tm  : x=%s  y=%s  <-- 溢出%n", it.x(), it.y()));
            out.append(String.format("  前后文   : ...%s...%n", buildContext(items, idx, 12)));
        }

        if (!badIdx.isEmpty()) {
            String target = items.get(badIdx.get(0)).ch();
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).ch().equals(target)) {
                    all.add(i);
                }
            }

            StringBuilder pos = new StringBuilder();
            for (int i = 0; i < all.size(); i++) {
                if (i > 0) {
                    pos.append(", ");
                }
                pos.append(all.get(i) + 1);
            }
            out.append(String.format("%n字符 %s 在文本层共出现 %d 次，位置: [%s]%n", target, all.size(), pos));
            out.append("全部出现处的前后文:\n");
            for (int i : all) {
                out.append("   ...").append(buildContext(items, i, 18)).append("...\n");
            }

            out.append("\n每个坏字周围字形的坐标:\n");
            for (int i : badIdx) {
                out.append("  --- ");
                for (int j = Math.max(0, i - 2); j < Math.min(items.size(), i + 3); j++) {
                    out.append(items.get(j).ch()).append('、');
                }
                out.append("---\n");
                for (int j = Math.max(0, i - 2); j < Math.min(items.size(), i + 3); j++) {
                    GlyphItem n = items.get(j);
                    out.append(String.format("      %-6s x=%-16s y=%-16s size=%s%s%n", n.ch(), n.x(), n.y(), n.size(), n.bad() ? "  <== 坏字" : ""));
                }
            }
        }
        return out.toString();
    }

    /**
     * 解析 ToUnicode CMap 流内容
     *
     * @param cmap CMap 流文本
     * @return CID 到 Unicode 映射表
     */
    private Map<Integer, String> parseCMap(String cmap) {
        Map<Integer, String> map = new HashMap<>();
        Matcher b = BF_CHAR_BLOCK.matcher(cmap);
        while (b.find()) {
            Matcher p = BF_CHAR_ENTRY.matcher(b.group(1));
            while (p.find()) {
                map.put(Integer.parseInt(p.group(1), 16), utf16be(p.group(2)));
            }
        }
        Matcher r = BF_RANGE_BLOCK.matcher(cmap);
        while (r.find()) {
            Matcher p = BF_RANGE_ENTRY.matcher(r.group(1));
            while (p.find()) {
                int lo = Integer.parseInt(p.group(1), 16);
                int hi = Integer.parseInt(p.group(2), 16);
                int dst = Integer.parseInt(p.group(3), 16);
                for (int i = lo; i <= hi; i++) {
                    map.put(i, new String(Character.toChars(dst + i - lo)));
                }
            }
        }
        return map;
    }

    /**
     * 将 16 进制字符串转换为 UTF-16BE 字符
     *
     * @param hex 16 进制字符串
     * @return 转换后的字符串
     */
    private String utf16be(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return new String(b, StandardCharsets.UTF_16BE);
    }

    /**
     * 内容流 Token 类型分类
     *
     * @param t Token 文本
     * @return Token 类型分类标识
     */
    private String classify(String t) {
        if (t.indexOf('#') >= 0) return "badnum";
        if (t.startsWith("/")) return "name";
        if (t.startsWith("<") || t.startsWith("(")) return "string";
        if (t.startsWith("[")) return "array";
        try {
            Double.parseDouble(t);
            return "number";
        } catch (Exception e) {
            return "op";
        }
    }

    /**
     * 截取坏字前后上下文
     *
     * @param items 字形列表
     * @param idx   目标字形索引
     * @param n     前后截取的字符数量
     * @return 带有标记的上下文文本
     */
    private String buildContext(List<GlyphItem> items, int idx, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, idx - n); i < Math.min(items.size(), idx + n); i++) {
            sb.append(i == idx ? "【" + items.get(i).ch() + "】" : items.get(i).ch());
        }
        return sb.toString();
    }
}