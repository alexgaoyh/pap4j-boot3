package cn.net.pap.common.spider.jsoup;

import cn.net.pap.common.spider.jsoup.dto.SpiderDTO;
import com.ibm.icu.text.BreakIterator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Range;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JsoupUtil {

    private static final Logger logger = LoggerFactory.getLogger(JsoupUtil.class);

    /**
     * 拼接生成 HTML
     *
     * @param spiderDTOList
     * @param indexList
     * @param outerCssBegin
     * @param outerCssEnd
     * @return
     */
    public static String parse(List<SpiderDTO> spiderDTOList, List<String> indexList, String outerCssBegin, String outerCssEnd) {
        StringBuilder sb = new StringBuilder();
        for (int dtoIdx = 0; dtoIdx < spiderDTOList.size(); dtoIdx++) {
            SpiderDTO spiderDTO = spiderDTOList.get(dtoIdx);
            for (String index : indexList) {
                String[] splitArray = index.split("-");
                if (splitArray[0].equals(dtoIdx + "")) {
                    sb.append(outerCssBegin);
                }
                if (splitArray[1].equals(dtoIdx + "")) {
                    sb.append(outerCssEnd);
                }
            }
            sb.append(convertDtoToSpan(spiderDTO));
        }

        return sb.toString();
    }

    public static String convertDtoToSpan(Object dto) {
        if (dto == null) {
            return "";
        }
        try {
            Document doc = Jsoup.parse("");
            Element span = doc.createElement("span");

            Field[] fields = dto.getClass().getDeclaredFields();
            for (Field field : fields) {
                // JPMS 风险提示：在 Java 9+ 模块化环境下，如果传入的 DTO 对象所在的包没有向当前模块开放（opens），此处会抛出 InaccessibleObjectException 异常。
                // 解决方案：可在该 DTO 的 module-info.java 中进行 opens 声明；或优先考虑重构为调用 public 的 Getter 方法获取属性值。
                field.setAccessible(true);
                String fieldName = field.getName();
                Object fieldValue = field.get(dto);
                if(fieldValue != null) {
                    span.attr(fieldName, fieldValue.toString());
                }
            }
            doc.body().appendChild(span);
            return doc.body().html();
        } catch (IllegalAccessException e) {
            logger.error("转换 DTO 到 span 失败", e);
            return "";
        }
    }

    /**
     * 按页码分组解析SpiderDTO列表，删除跨页的索引范围
     */
    public static Map<Integer, String> parseByPage(List<SpiderDTO> spiderDTOList, List<String> indexList, String outerCssBegin, String outerCssEnd) {
        // 按页码分组
        Map<Integer, List<SpiderDTO>> pageMap = spiderDTOList.stream().collect(Collectors.groupingBy(SpiderDTO::getPageNumber));

        Map<Integer, String> result = new HashMap<>();

        // 对每个页码的数据进行解析
        for (Map.Entry<Integer, List<SpiderDTO>> entry : pageMap.entrySet()) {
            Integer pageNumber = entry.getKey();
            List<SpiderDTO> pageDtoList = entry.getValue();

            // 获取当前页在原始列表中的索引范围
            List<Integer> pageOriginalIndices = getPageOriginalIndices(spiderDTOList, pageDtoList);

            // 获取当前页码对应的索引范围（删除跨页的索引）
            List<String> pageIndexList = getPageIndexList(pageOriginalIndices, indexList);

            String pageHtml = parseSinglePage(pageDtoList, pageOriginalIndices, pageIndexList, outerCssBegin, outerCssEnd);
            result.put(pageNumber, pageHtml);
        }

        return result;
    }

    /**
     * 获取当前页所有DTO在原始列表中的索引
     */
    private static List<Integer> getPageOriginalIndices(List<SpiderDTO> originalList, List<SpiderDTO> pageDtoList) {
        List<Integer> indices = new ArrayList<>();
        for (SpiderDTO pageDto : pageDtoList) {
            int index = originalList.indexOf(pageDto);
            if (index != -1) {
                indices.add(index);
            }
        }
        return indices;
    }

    /**
     * 获取当前页码对应的索引范围，删除跨页索引
     */
    private static List<String> getPageIndexList(List<Integer> pageOriginalIndices, List<String> indexList) {
        List<String> pageIndexList = new ArrayList<>();

        // 将当前页的索引转换为Set便于查找
        Set<Integer> pageIndexSet = new HashSet<>(pageOriginalIndices);

        for (String index : indexList) {
            String[] splitArray = index.split("-");
            int startIndex = Integer.parseInt(splitArray[0]);
            int endIndex = Integer.parseInt(splitArray[1]);

            // 只有当起始索引和结束索引都在当前页时，才保留这个索引范围
            if (pageIndexSet.contains(startIndex) && pageIndexSet.contains(endIndex)) {
                pageIndexList.add(index);
            }
        }

        return pageIndexList;
    }

    /**
     * 解析单个页码的数据
     */
    private static String parseSinglePage(List<SpiderDTO> pageDtoList, List<Integer> pageOriginalIndices, List<String> pageIndexList, String outerCssBegin, String outerCssEnd) {
        StringBuilder sb = new StringBuilder();

        // 创建当前页DTO到原始索引的映射
        Map<SpiderDTO, Integer> dtoToOriginalIndex = new HashMap<>();
        for (int i = 0; i < pageDtoList.size(); i++) {
            dtoToOriginalIndex.put(pageDtoList.get(i), pageOriginalIndices.get(i));
        }

        for (SpiderDTO spiderDTO : pageDtoList) {
            int originalIndex = dtoToOriginalIndex.get(spiderDTO);

            // 处理外层CSS包装 - 开始标签
            for (String index : pageIndexList) {
                String[] splitArray = index.split("-");
                int startIndex = Integer.parseInt(splitArray[0]);
                if (startIndex == originalIndex) {
                    sb.append(outerCssBegin);
                }
            }

            sb.append(convertDtoToSpan(spiderDTO));

            // 处理外层CSS包装 - 结束标签
            for (String index : pageIndexList) {
                String[] splitArray = index.split("-");
                int endIndex = Integer.parseInt(splitArray[1]);
                if (endIndex == originalIndex) {
                    sb.append(outerCssEnd);
                }
            }
        }

        return sb.toString();
    }

    /**
     * 把字符串按“所见字符（grapheme cluster）”切分
     * @param text
     * @return
     */
    public static List<String> splitToGraphemes(String text) {
        List<String> result = new ArrayList<>();
        BreakIterator it = BreakIterator.getCharacterInstance(Locale.CHINA);
        it.setText(text);

        int start = it.first();
        int end = it.next();

        while (end != BreakIterator.DONE) {
            String grapheme = text.substring(start, end);
            result.add(grapheme);
            start = end;
            end = it.next();
        }
        return result;
    }

    /**
     * 主方法：用 ICU4J 分割 + 连续匹配
     * @param html
     * @param keyword
     * @param css
     * @return
     */
    public static String highlightSequential(String html, String keyword, String css) {

        Document doc = Jsoup.parseBodyFragment(html);
        Elements chars = doc.select("span.chars");

        // 关键字切成“视觉字符”
        List<String> keyGraphemes = splitToGraphemes(keyword);
        int kLen = keyGraphemes.size();

        // 把 HTML 中每个“字符 span 的可见字”也切成 grapheme
        List<String> htmlGraphemes = new ArrayList<>();
        for (Element e : chars) {
            String grapheme = e.text();
            htmlGraphemes.addAll(splitToGraphemes(grapheme));
        }

        int total = htmlGraphemes.size();

        for (int start = 0; start < total; start++) {

            boolean matched = true;
            int kIndex = 0;
            int pos = start;

            // 内层连续匹配检查
            while (pos < total && kIndex < kLen) {
                if (!htmlGraphemes.get(pos).equals(keyGraphemes.get(kIndex))) {
                    matched = false;
                    break;   // 跳出内层 while，到外层继续下一个 start
                }
                pos++;
                kIndex++;
            }

            if (matched && kIndex == kLen) {
                // 找到从 start 到 start+kLen 的匹配
                for (int i = start; i < start + kLen; i++) {
                    Element e = chars.get(i);
                    String old = e.attr("style");
                    if (old == null || old.isEmpty()) {
                        e.attr("style", css);
                    } else {
                        e.attr("style", old + ";" + css);
                    }
                }
                break;
            }
        }

        return doc.body().html();
    }


    /**
     * 从 HTML 中移除指定属性匹配的 span 标签（保留标签内的文本内容）。
     *
     * <p>通过 Jsoup 的位置追踪获取开/闭标签在原始字符串中的字符偏移，直接在原串上做剪切，不经过 Jsoup 序列化，避免触发 HTML 规范化破坏原文。</p>
     *
     * <p>匹配分两层：属性名按 HTML 规范大小写不敏感（{@code DATA-TYPE}、{@code Data-Type} 等同）；
     * 属性值则在 jsoup 解析后的值上做等值比较（jsoup 会 trim 值两侧空白）、大小写敏感
     * （{@code TO_CITATION} 不等于 {@code to_citation}）。与属性书写顺序、单双引号写法、值内实体编码等无关。</p>
     *
     * <p>遇到未闭合 span、开/闭标签区间不平衡、区间重叠/逆序、剥离后仍有残留，或解析异常时，原样返回入参，不做任何修改。</p>
     *
     * @param html      待处理的 HTML 原文
     * @param attrName  要匹配的 span 属性名，如 {@code data-type}
     * @param attrValue 该属性需等于的值，如 {@code to_citation}
     * @return 剥离匹配 span 标签后的 HTML；无需处理或处理失败时返回原 HTML
     */
    public static String unwrapAttributeSpans(String html, String attrName, String attrValue) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        if (attrName == null || attrName.isEmpty()) {
            return html;
        }
        if (attrValue == null || attrValue.isEmpty()) {
            return html;
        }

        // 快速短路：原文中不存在该属性名（属性名大小写不敏感，取值无关），跳过 Jsoup 解析
        if (!Pattern.compile(Pattern.quote(attrName), Pattern.CASE_INSENSITIVE).matcher(html).find()) {
            return html;
        }

        try {
            Parser parser = Parser.htmlParser().setTrackPosition(true);
            Document doc = parser.parseInput(html, "");

            List<int[]> ranges = collectRemoveRanges(doc, attrName, attrValue);
            if (ranges == null || ranges.isEmpty()) {
                return html;
            }

            String cleaned = removeRanges(html, ranges, attrValue);
            if (cleaned == null) {
                return html;
            }

            return verifyCleaned(cleaned, attrName, attrValue) ? cleaned : html;
        } catch (Throwable e) {
            logger.error("[JsoupUtil] 剥离 {}={} 标签异常，降级返回原 HTML", attrName, attrValue, e);
            return html;
        }
    }

    /**
     * 收集属性值等于 {@code attrValue} 的 span 的开/闭标签在原始字符串中的字符区间 [start, end)。
     *
     * <p>用属性值等值比较（大小写敏感）替代 CSS 选择器，避免属性值含选择器元字符时被拼串改写语义，
     * 同时天然兼容值内实体编码（{@code attr()} 返回解码后的值）。</p>
     *
     * @return 区间列表；开/闭数量不平衡时返回 {@code null}（存在删一半风险）
     */
    private static List<int[]> collectRemoveRanges(Document doc, String attrName, String attrValue) {
        String normalizedAttrName = attrName.toLowerCase(Locale.ROOT);
        List<int[]> rangesToRemove = new ArrayList<>();
        int openCount = 0, closeCount = 0;
        for (Element span : doc.getElementsByTag("span")) {
            if (!attrValue.equals(span.attr(normalizedAttrName))) {
                continue;
            }
            // 开标签 <span ...>
            Range startRange = span.sourceRange();
            if (startRange.isTracked() && startRange.end().pos() > startRange.start().pos()) {
                rangesToRemove.add(new int[]{startRange.start().pos(), startRange.end().pos()});
                openCount++;
            }
            // 闭标签 </span>；isImplicit 表示隐式关闭（jsoup 生成零长度区间，如未闭合到 EOF、被父元素提前关闭、自闭合写法），跳过
            // end > start 为防御性检查：jsoup 当前版本隐式区间均零长度且已被上句过滤，此处兜底保证区间恒为正长度，防逆序区间拼接重复字符
            Range endRange = span.endSourceRange();
            if (endRange.isTracked() && !endRange.isImplicit()
                && endRange.end().pos() > endRange.start().pos()) {
                rangesToRemove.add(new int[]{endRange.start().pos(), endRange.end().pos()});
                closeCount++;
            }
        }

        if (rangesToRemove.isEmpty()) {
            return rangesToRemove;
        }

        // 开闭数量不一致，说明有 span 只能定位到开标签（闭标签被隐式关闭或缺失），
        // 继续擦除会留下悬空 </span>，整体放弃
        if (openCount != closeCount) {
            logger.warn("[JsoupUtil] {} span 开/闭标签区间数不平衡(开={} 闭={})，存在删一半风险，降级返回原 HTML",
                    attrName, openCount, closeCount);
            return null;
        }
        return rangesToRemove;
    }

    /**
     * 按偏移跳过需删除区间，其余字符从原始字符串直接复制。
     *
     * @return 剪切后的字符串；区间重叠或逆序时返回 {@code null}
     */
    static String removeRanges(String html, List<int[]> ranges, String attrValue) {
        // 按偏移排序，保证后续顺序拼接正确
        ranges.sort((a, b) -> Integer.compare(a[0], b[0]));

        StringBuilder sb = new StringBuilder(html.length());
        int lastIndex = 0;
        for (int[] range : ranges) {
            // 起点不早于已处理位置，且终点必须严格推进；否则说明区间重叠或逆序，放弃本次清洗
            if (range[0] >= lastIndex && range[1] > lastIndex) {
                sb.append(html, lastIndex, range[0]);
                lastIndex = range[1];
            } else {
                logger.warn("[JsoupUtil] 剥离 {} 区间重叠/逆序，擦除不完整，降级返回原 HTML", attrValue);
                return null;
            }
        }
        if (lastIndex < html.length()) {
            sb.append(html, lastIndex, html.length());
        }
        return sb.toString();
    }

    /**
     * 复查清理结果，确认不再残留属性值等于 {@code attrValue} 的 span。
     */
    static boolean verifyCleaned(String cleaned, String attrName, String attrValue) {
        String normalizedAttrName = attrName.toLowerCase(Locale.ROOT);
        Document doc = Jsoup.parse(cleaned);
        for (Element span : doc.getElementsByTag("span")) {
            if (attrValue.equals(span.attr(normalizedAttrName))) {
                logger.warn("[JsoupUtil] 剥离后仍检测到 {}={} span，清洗不完整，降级返回原 HTML", attrName, attrValue);
                return false;
            }
        }
        return true;
    }

}
