package cn.net.pap.common.spider.jsoup;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 表格转 Markdown 转换器
 * 提供多维网格平铺还原算法，支持 rowspan、colspan 以及防嵌套表格、多级表头压平、隐藏元素过滤等健壮性处理。
 * <p>
 * 仅做结构转换，不做任何自然语言层面的语义推断（如前向填充、占位符替换等）。
 */
public class HtmlTableToMarkdownConverter {

    private static final Logger log = LoggerFactory.getLogger(HtmlTableToMarkdownConverter.class);

    /**
     * 将包含表格的 HTML 片段转换为 Markdown 格式的表格。
     *
     * @param htmlTable HTML 表格字符串
     * @return 转换后的 Markdown 表格
     */
    public static String convert(String htmlTable) {
        if (htmlTable == null || htmlTable.isBlank()) {
            return "";
        }

        Document doc = Jsoup.parse(htmlTable);
        // 仅抓取最外层的第一层 table，避免 nested table 相互干扰
        Element table = doc.selectFirst("table");
        if (table == null) {
            return "";
        }

        // 过滤隐藏表格
        if (isHidden(table)) {
            return "";
        }

        // 按照 standard logical order 重组所有直接隶属当前表格的行：thead -> direct tr -> tbody -> tfoot
        Elements rows = new Elements();
        rows.addAll(table.select("> thead > tr"));
        rows.addAll(table.select("> tr")); // 处于 table 根节点下的直接 tr
        rows.addAll(table.select("> tbody > tr"));
        rows.addAll(table.select("> tfoot > tr"));

        // 过滤隐藏行
        rows.removeIf(HtmlTableToMarkdownConverter::isHidden);

        if (rows.isEmpty()) {
            return "";
        }

        // 智能探测表头行数：连续全部为 th 单元格的行均归为表头
        int headerRowCount = detectHeaderRowCount(rows);

        // 用于模拟坐标网格：Map<rowIndex, Map<colIndex, cellValue>>
        Map<Integer, Map<Integer, String>> grid = new HashMap<>();
        int maxCols = 0;

        for (int r = 0; r < rows.size(); r++) {
            Element row = rows.get(r);
            // 仅选取直接隶属于该行的列元素，且排除隐藏列，防御嵌套
            Elements cells = row.select("> th, > td");
            cells.removeIf(HtmlTableToMarkdownConverter::isHidden);

            int c = 0;
            for (Element cell : cells) {
                // 探测当前行 r 是否已被上一行跨行 rowspan 侵占，跳过已被填充的列
                while (grid.computeIfAbsent(r, k -> new HashMap<>()).containsKey(c)) {
                    c++;
                }

                int rowspan = parseSpanValue(cell, "rowspan");
                int colspan = parseSpanValue(cell, "colspan");
                String text = cleanCellText(cell);

                // 将值平铺写入对应的行和列范围
                for (int i = 0; i < rowspan; i++) {
                    for (int j = 0; j < colspan; j++) {
                        int targetRow = r + i;
                        int targetCol = c + j;
                        grid.computeIfAbsent(targetRow, k -> new HashMap<>()).put(targetCol, text);
                        maxCols = Math.max(maxCols, targetCol + 1);
                    }
                }
                c += colspan;
            }
        }

        int totalRows = rows.size();

        // 对齐校验：与 validateMarkdownTableStructure 保持一致的严格规则——参差行（某行单元格少于最大列数）
        // 视为无效表格，直接拒绝（返回空串），避免产出含空洞的 Markdown 造成静默数据丢失
        for (int r = 0; r < totalRows; r++) {
            int colCount = grid.getOrDefault(r, Collections.emptyMap()).size();
            if (colCount < maxCols) {
                return "";
            }
        }

        // 构建 Markdown 字符串
        StringBuilder sb = new StringBuilder();

        // 1. 处理表头行
        // GFM 规范下只允许单行表头。如果 headerRowCount > 1，则将多级表头压平合并（如 "地区/线上"）
        List<String> finalHeaders = new ArrayList<>();
        for (int c = 0; c < maxCols; c++) {
            List<String> headerParts = new ArrayList<>();
            for (int r = 0; r < headerRowCount; r++) {
                String val = grid.getOrDefault(r, Collections.emptyMap()).getOrDefault(c, "").trim();
                if (!val.isEmpty()) {
                    headerParts.add(val);
                }
            }
            // 过滤连续重复相邻词（如 "地区/地区" -> "地区"）
            List<String> uniqueParts = new ArrayList<>();
            for (String part : headerParts) {
                if (uniqueParts.isEmpty() || !uniqueParts.get(uniqueParts.size() - 1).equals(part)) {
                    uniqueParts.add(part);
                }
            }
            finalHeaders.add(String.join("/", uniqueParts));
        }

        // 合并「表头文本完全相同」的相邻列（colspan 展开产生的重复列）
        // 例：二级指标(colspan=2) 展开成 [二级指标, 二级指标] → 合并回一列；数据子值内容零丢失。
        List<int[]> mergeRuns = new ArrayList<>();
        int s = 0;
        while (s < finalHeaders.size()) {
            int e = s;
            while (e + 1 < finalHeaders.size()
                    && !finalHeaders.get(s).isEmpty()
                    && finalHeaders.get(e + 1).equals(finalHeaders.get(s))) {
                e++;
            }
            if (e > s) {
                mergeRuns.add(new int[]{s, e});
            }
            s = e + 1;
        }
        // 输出列序列：非合并列原样保留；合并段只保留段首列
        Set<Integer> mergedAway = new HashSet<>();
        for (int[] run : mergeRuns) {
            for (int c = run[0] + 1; c <= run[1]; c++) {
                mergedAway.add(c);
            }
        }
        List<Integer> outCols = new ArrayList<>();
        for (int c = 0; c < maxCols; c++) {
            if (!mergedAway.contains(c)) {
                outCols.add(c);
            }
        }

        // 输出扁平化后的第一行表头
        sb.append("|");
        for (int c : outCols) {
            sb.append(" ").append(finalHeaders.get(c)).append(" |");
        }
        sb.append("\n");

        // 输出 Markdown 分隔线
        sb.append("|");
        for (int c : outCols) {
            sb.append(" --- |");
        }
        sb.append("\n");

        // 2. 输出数据行
        for (int r = headerRowCount; r < totalRows; r++) {
            Map<Integer, String> colMap = grid.getOrDefault(r, Collections.emptyMap());
            sb.append("|");
            for (int c : outCols) {
                // 若当前列是某合并段的段首，则拼接该段全部子值：全相同保留一份，不同用 " " 拼接
                int[] run = null;
                for (int[] cand : mergeRuns) {
                    if (cand[0] == c) {
                        run = cand;
                        break;
                    }
                }
                String val;
                if (run != null) {
                    List<String> parts = new ArrayList<>();
                    for (int cc = run[0]; cc <= run[1]; cc++) {
                        String v = colMap.getOrDefault(cc, "").trim();
                        if (!v.isEmpty()) {
                            parts.add(v);
                        }
                    }
                    val = (parts.stream().distinct().count() <= 1)
                            ? (parts.isEmpty() ? "" : parts.get(0))
                            : String.join(" ", parts);
                } else {
                    val = colMap.getOrDefault(c, "");
                }
                sb.append(" ").append(val).append(" |");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 自动探测表头行数：连续全部为 th 单元格的行均归为表头；若未探测到 th 行，默认首行作为表头。
     */
    private static int detectHeaderRowCount(Elements rows) {
        int detected = 0;
        for (Element row : rows) {
            Elements directCells = row.select("> th, > td");
            directCells.removeIf(HtmlTableToMarkdownConverter::isHidden);

            if (directCells.isEmpty()) {
                continue; // 跳过完全隐藏的行，避免提前中断探测
            }

            long tdCount = directCells.stream().filter(c -> c.tagName().equalsIgnoreCase("td")).count();
            if (tdCount == 0) {
                detected++;
            } else {
                break;
            }
        }
        return detected == 0 ? 1 : detected;
    }

    /**
     * 安全解析 rowspan/colspan 属性值。
     * <p>
     * 脏数据防御：属性缺失、空串、非数字、带空白等一律兜底为 1，避免 NumberFormatException 中断整表转换。
     * HTML 规范中 0 表示跨到行组/列组末尾，当前不支持，同样钳制为 1 避免数据丢失。
     *
     * @param cell     单元格元素
     * @param attrName 属性名（rowspan 或 colspan）
     * @return 解析后的跨度值，非法输入返回 1
     */
    private static int parseSpanValue(Element cell, String attrName) {
        String raw = cell.attr(attrName);
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value < 1 ? 1 : value;
        } catch (NumberFormatException e) {
            log.warn("解析表格 {} 属性失败，非法值 '{}' 按 1 处理", attrName, raw);
            return 1;
        }
    }

    /**
     * 判断节点是否被设置为隐藏。
     */
    private static boolean isHidden(Element element) {
        if (element == null) {
            return false;
        }
        if (element.hasAttr("hidden")) {
            return true;
        }
        String style = element.attr("style");
        if (style != null && !style.isBlank()) {
            String s = style.toLowerCase().replaceAll("\\s+", "");
            if (s.contains("display:none") || s.contains("visibility:hidden")) {
                return true;
            }
        }
        // 递归检查父节点是否隐藏，防御整行/整表被隐藏
        Element parent = element.parent();
        if (parent != null && !parent.tagName().equalsIgnoreCase("body")) {
            return isHidden(parent);
        }
        return false;
    }

    /**
     * 清理单元格内的文本信息，使其符合 Markdown 规范。
     */
    private static String cleanCellText(Element cell) {
        if (cell == null) {
            return "";
        }

        // 使用占位符替代换行和段落，避免被 Jsoup.text() 剥离
        String html = cell.html();
        html = html.replaceAll("(?i)<br\\s*/?>", " __BR_PLACEHOLDER__ ");
        html = html.replaceAll("(?i)<p\\s*[^>]*>", " __BR_PLACEHOLDER__ ");
        html = html.replaceAll("(?i)</p>", " </p> __BR_PLACEHOLDER__ ");

        // 重新转回纯文本，移除其他 HTML 元素，保留语义文本
        String text = Jsoup.parseBodyFragment(html).body().text();

        // 还原换行占位符为 <br> 标签
        text = text.replace("__BR_PLACEHOLDER__", "<br>");

        // 转义管道符
        text = text.replace("|", "\\|");

        // 压缩冗余的连续空白字符
        text = text.replaceAll("\\s+", " ").trim();

        // 清理首尾多余的 <br> 标签
        text = text.replaceAll("^(<br>\\s*)+", "");
        text = text.replaceAll("(<br>\\s*)+$", "");

        return text.trim();
    }

    // =========================================================================
    // VLM 多模态大模型表格提取后置清洗与静态校验增强方法
    // =========================================================================

    /**
     * 自动清除包裹在 HTML table 外面的 markdown 代码块标记，例如 ```html \n <table>...</table> \n ```
     * 采用确定的前缀/后缀清理方式，彻底消除 ReDoS 复杂正则匹配带来的性能与回溯风险。
     *
     * @param content 原始模型输出
     * @return 清理后的文本
     */
    public static String cleanCodeBlocks(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String result = content;
        result = result.replaceAll("(?i)```(?:html|xml|xhtml)?\\s*<table", "<table");
        result = result.replaceAll("</table>\\s*```", "</table>");
        return result;
    }

    /**
     * 静态解析并转换 Markdown 中的 HTML 表格，并做扁平化处理。
     *
     * @param content 包含 HTML <table> 标签的 Markdown 文本
     * @return 转换平铺展开后的规范 Markdown 文本
     */
    public static String convertHtmlTablesToMarkdown(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        // 1. 清除代码块标记
        String cleaned = cleanCodeBlocks(content);

        // 2. 查找 <table> 元素
        Pattern pattern = Pattern.compile("(?si)<table\\b[^>]*>.*?</table>");
        Matcher matcher = pattern.matcher(cleaned);

        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        boolean hasTable = false;

        while (matcher.find()) {
            hasTable = true;
            String tableHtml = matcher.group();
            sb.append(cleaned, lastEnd, matcher.start());

            try {
                // 执行网格还原与数据平铺填充
                String markdownTable = convert(tableHtml);
                sb.append("\n\n").append(markdownTable).append("\n\n");
            } catch (Exception e) {
                log.error("【表格后置处理】转换 HTML <table> 失败，将保留原 HTML 内容。错误: {}", e.getMessage(), e);
                sb.append(tableHtml);
            }

            lastEnd = matcher.end();
        }
        sb.append(cleaned.substring(lastEnd));

        if (hasTable) {
            log.info("【表格后置处理】已检测到并成功转换 HTML <table> 为 Markdown 表格。");
        } else {
            log.info("【表格后置处理】未检测到 HTML 表格，无需转换（或已使用模型自带的 Markdown 表格输出）。");
        }

        // 规范化多余换行
        return sb.toString().replaceAll("(\\r?\\n\\s*){3,}", "\n\n").trim();
    }

    /**
     * 对 Markdown 中包含的 HTML <table> 结构进行静态规则与对齐校验。
     *
     * @param markdownResult 原始模型输出
     * @return 校验通过返回 null，校验失败返回具体的错误诊断信息
     */
    public static String validateMarkdownTableStructure(String markdownResult) {
        if (markdownResult == null || markdownResult.isBlank()) {
            return "模型返回的 Markdown 内容为空。";
        }

        // 1. 自动清除代码块标记
        String cleaned = cleanCodeBlocks(markdownResult);

        // 如果包含 <table 却不包含 </table>，说明明显未闭合
        if (cleaned.toLowerCase().contains("<table") && !cleaned.toLowerCase().contains("</table>")) {
            return "检测到未完整闭合的 HTML 表格标签（例如缺少 </table>, </td> 或 </th>）。";
        }

        // 2. 查找 <table> 元素
        Pattern pattern = Pattern.compile("(?si)<table\\b[^>]*>.*?</table>");
        Matcher matcher = pattern.matcher(cleaned);

        boolean foundTable = false;
        while (matcher.find()) {
            foundTable = true;
            String tableHtml = matcher.group();

            // 检查 HTML table 标签的基本闭合完整性
            if (!tableHtml.toLowerCase().contains("</table>")
                    || (!tableHtml.toLowerCase().contains("</td>") && !tableHtml.toLowerCase().contains("</th>"))) {
                return "检测到未完整闭合的 HTML 表格标签（例如缺少 </table>, </td> 或 </th>）。";
            }

            try {
                // 校验 HTML 表格的对齐结构
                Document doc = Jsoup.parse(tableHtml);
                Element table = doc.selectFirst("table");
                if (table != null) {
                    Elements rows = new Elements();
                    rows.addAll(table.select("> thead > tr"));
                    rows.addAll(table.select("> tr"));
                    rows.addAll(table.select("> tbody > tr"));
                    rows.addAll(table.select("> tfoot > tr"));
                    rows.removeIf(HtmlTableToMarkdownConverter::isHidden);

                    if (!rows.isEmpty()) {
                        Map<Integer, Map<Integer, String>> grid = new HashMap<>();
                        int maxCols = 0;
                        for (int r = 0; r < rows.size(); r++) {
                            Element row = rows.get(r);
                            Elements cells = row.select("> th, > td");
                            cells.removeIf(HtmlTableToMarkdownConverter::isHidden);
                            int c = 0;
                            for (Element cell : cells) {
                                while (grid.computeIfAbsent(r, k -> new HashMap<>()).containsKey(c)) {
                                    c++;
                                }
                                int rowspan = parseSpanValue(cell, "rowspan");
                                int colspan = parseSpanValue(cell, "colspan");
                                String text = cell.text();
                                for (int i = 0; i < rowspan; i++) {
                                    for (int j = 0; j < colspan; j++) {
                                        grid.computeIfAbsent(r + i, k -> new HashMap<>()).put(c + j, text);
                                        maxCols = Math.max(maxCols, c + j + 1);
                                    }
                                }
                                c += colspan;
                            }
                        }
                        // 检查是否有行长度不一致
                        for (int r = 0; r < rows.size(); r++) {
                            int colCount = grid.getOrDefault(r, Collections.emptyMap()).size();
                            if (colCount < maxCols) {
                                return "Markdown 表格各行列数不一致！预期有 " + maxCols + " 列，但该行有 "
                                        + colCount + " 列。不匹配的行数据: " + rows.get(r).text();
                            }
                        }
                    }
                }

                // 尝试转换该 HTML 表格
                String markdownTable = convert(tableHtml);
                if (markdownTable == null || markdownTable.isBlank()) {
                    return "HTML 表格转换后得到的 Markdown 表格为空，请检查 table/tr/td/th 的 HTML 标签层次。";
                }

                // 静态结构列数齐平校验，兼容 Windows 的 \r\n 换行符
                String[] lines = markdownTable.split("\\r?\\n");
                int expectedCols = -1;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    // Markdown 表格行必须以 '|' 开始和结束
                    if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) {
                        return "转换得到的 Markdown 表格行未遵循规范格式（未以 '|' 开头和结尾）: " + trimmed;
                    }

                    // 统计管道符数量（排除转义的 '\|'，并且防御 '\\|' 场景）
                    int cols = countSeparators(trimmed);
                    if (expectedCols == -1) {
                        expectedCols = cols;
                    } else if (expectedCols != cols) {
                        return "Markdown 表格各行列数不一致！预期有 " + (expectedCols - 1) + " 列，但该行有 "
                                + (cols - 1) + " 列。不匹配的行数据: " + trimmed;
                    }
                }
            } catch (Exception e) {
                return "解析/转换 HTML <table> 遇到异常: " + e.getMessage();
            }
        }

        if (!foundTable) {
            return "未在输出的 Markdown 中检测到任何有效的 HTML <table> 标签，表格提取可能丢失或生成为其他格式。";
        }

        return null; // 校验通过
    }

    /**
     * 辅助统计行内排除转义外的 '|' 个数。
     * 采用状态机思想，精确处理奇偶个反斜杠转义符号，防止 `\\|` 等场景发生漏判。
     *
     * @param line 行文本
     * @return 逻辑分隔符数
     */
    public static int countSeparators(String line) {
        if (line == null) {
            return 0;
        }
        int count = 0;
        int backslashes = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                backslashes++;
            } else if (c == '|') {
                // 如果管道符前有偶数个反斜杠（包括0个），说明管道符未被转义
                if (backslashes % 2 == 0) {
                    count++;
                }
                backslashes = 0; // 重置计数
            } else {
                backslashes = 0; // 重置计数
            }
        }
        return count;
    }
}
