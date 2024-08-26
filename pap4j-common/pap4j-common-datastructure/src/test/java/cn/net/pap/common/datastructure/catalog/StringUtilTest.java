package cn.net.pap.common.datastructure.catalog;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 一些字符串验证的单元测试
 */
public class StringUtilTest {

    /**
     * CSS 转换
     */
    @Test
    public void convertCSS() {
        Map<String, String> cssMap = new HashMap<String, String>();
        cssMap.put("className1", "font-size: 14px;");
        cssMap.put("className2", "font-size: 14px;font-weight: bold;");
        String s = convertToCSSString(cssMap);
        System.out.println(s);
    }

    /**
     * 转换为友好的 CSS 格式.
     * @param cssData   key - name ; value - cssContent
     * @return
     */
    private String convertToCSSString(Map<String, String> cssData) {
        StringBuilder cssStringBuilder = new StringBuilder();

        for (Map.Entry<String, String> entry : cssData.entrySet()) {
            String selector = entry.getKey();
            String cssContent = entry.getValue();

            cssStringBuilder.append(selector).append(" {\n");
            String cssContenSplit = checkByNewlineOrSemicolon(cssContent);
            String[] lines = cssContent.split(cssContenSplit);
            for (String line : lines) {
                cssStringBuilder.append("    ").append(line.trim()).append(cssContenSplit);
                if(!cssContenSplit.equals("\n")) {
                    cssStringBuilder.append("\n");
                }
            }
            cssStringBuilder.append("}\n\n");
        }

        return cssStringBuilder.toString();
    }

    /**
     * 判断采用那种分隔符
     * @param cssContent
     * @return
     */
    private static String checkByNewlineOrSemicolon(String cssContent) {
        if (cssContent.contains("\n")) {
            return "\n";
        } else {
            return ";";
        }
    }
}
