package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class StringInternTest {

    /**
     * String.intern() 的使用，下面方法加与不加的区别
     * 在缓存数据的时候，如果数据的信息可控，是否这样处理更好？
     *
     * @throws Exception
     */
    @Test
    public void memoryIntern() throws Exception {
        StringBuffer sb = new StringBuffer();
        for(char c = '\u4E00'; c <= '\u9FA5'; c++) {
            sb.append(String.valueOf(c));
        }
        List<String> strList = new ArrayList<>();
        while (true) {
            String s = sb.toString().intern();
            // String s = sb.toString();
            strList.add(s);
            if(false) {
                break;
            }
        }
    }

}
