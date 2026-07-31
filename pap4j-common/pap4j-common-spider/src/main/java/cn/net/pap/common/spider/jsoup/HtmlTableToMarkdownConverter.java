package cn.net.pap.common.spider.jsoup;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTML 表格转 Markdown 转换器
 * 提供多维网格平铺还原算法，支持 rowspan、colspan 以及防嵌套表格、多级表头压平、隐藏元素过滤等健壮性处理。
 * <p>
 * 仅做结构转换，不做任何自然语言层面的语义推断（如前向填充、占位符替换等）。
 */
public class HtmlTableToMarkdownConverter {

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

                int rowspan = cell.hasAttr("rowspan") ? Integer.parseInt(cell.attr("rowspan")) : 1;
                int colspan = cell.hasAttr("colspan") ? Integer.parseInt(cell.attr("colspan")) : 1;
                // 钳制非法值：HTML 规范中 0 表示跨到行组/列组末尾，当前不支持，钳制为 1 避免数据丢失
                if (rowspan < 1) rowspan = 1;
                if (colspan < 1) colspan = 1;
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

        // 输出扁平化后的第一行表头
        sb.append("|");
        for (String header : finalHeaders) {
            sb.append(" ").append(header).append(" |");
        }
        sb.append("\n");

        // 输出 Markdown 分隔线
        sb.append("|");
        for (int c = 0; c < maxCols; c++) {
            sb.append(" --- |");
        }
        sb.append("\n");

        // 2. 输出数据行
        for (int r = headerRowCount; r < totalRows; r++) {
            Map<Integer, String> colMap = grid.getOrDefault(r, Collections.emptyMap());
            sb.append("|");
            for (int c = 0; c < maxCols; c++) {
                String val = colMap.getOrDefault(c, "");
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
}
