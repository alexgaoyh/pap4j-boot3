package cn.net.pap.common.spider.jsoup;

import cn.net.pap.common.spider.jsoup.dto.SpiderDTO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.lang.reflect.Field;
import java.util.List;

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

}
