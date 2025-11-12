package cn.net.pap.common.spider.jsoup;

import cn.net.pap.common.spider.jsoup.dto.SpiderDTO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JsoupUtil {

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
                field.setAccessible(true);
                String fieldName = field.getName();
                Object fieldValue = field.get(dto);
                span.attr(fieldName, fieldValue.toString());
            }
            doc.body().appendChild(span);
            return doc.body().html();
        } catch (IllegalAccessException e) {
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

}
