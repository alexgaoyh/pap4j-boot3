package cn.net.pap.common.spider;

import cn.net.pap.common.spider.jsoup.JsoupUtil;
import cn.net.pap.common.spider.jsoup.dto.SpiderDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class JsoupUtilTest {

    @Test
    public void parseTest() {
        List<SpiderDTO> spiderDTOList = new ArrayList<>();
        spiderDTOList.add(new SpiderDTO("0", "0", "0"));
        spiderDTOList.add(new SpiderDTO("1", "1", "1"));
        spiderDTOList.add(new SpiderDTO("2", "2", "2"));
        spiderDTOList.add(new SpiderDTO("3", "3", "3"));
        spiderDTOList.add(new SpiderDTO("4", "4", "4"));
        spiderDTOList.add(new SpiderDTO("5", "5", "5"));
        spiderDTOList.add(new SpiderDTO("6", "6", "6"));
        spiderDTOList.add(new SpiderDTO("7", "7", "7"));

        List<String> indexList = new ArrayList<>();
        indexList.add("0-2");
        indexList.add("4-6");

        String parse = JsoupUtil.parse(spiderDTOList, indexList, "<span class=\"outerClassName\">", "</span>");
        System.out.println(parse);
    }

}
