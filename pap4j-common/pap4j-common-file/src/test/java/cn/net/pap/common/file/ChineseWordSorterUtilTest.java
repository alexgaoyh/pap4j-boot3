package cn.net.pap.common.file;

import cn.net.pap.common.file.chinese.ChineseWordSorterUtil;
import org.junit.jupiter.api.Test;

public class ChineseWordSorterUtilTest {

    //@Test
    public void testAdd() {
        String basePath = "C:\\Users\\86181\\Desktop\\chinese";
        ChineseWordSorterUtil.add(basePath, "\uD840\uDC00\uD840\uDC01");
        ChineseWordSorterUtil.add(basePath, "\uD840\uDC02");
        ChineseWordSorterUtil.add(basePath, "\uD840\uDC03");
        ChineseWordSorterUtil.add(basePath, "\uD840\uDC04");
        ChineseWordSorterUtil.add(basePath, "\uD840\uDC05");
        ChineseWordSorterUtil.add(basePath, "\uD840\uDC06");
        ChineseWordSorterUtil.add(basePath, "\uD840\uDC07");
    }


}
