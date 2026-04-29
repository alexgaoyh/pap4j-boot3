package cn.net.pap.common.datastructure.fstunicode.eclipse;

import cn.net.pap.common.datastructure.fst.ValueLocationDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

public class FSTUnicodeUtilTest {

    @Test
    public void lengthTest() {
        int idx = 0;
        for (char b1 = '\u4E00'; b1 <= '\u9FA5'; b1++) {
            idx++;
        }
        System.out.println(idx);
    }

    @Test
    public void hanziUnicodeTest() {
        FSTUnicode dict = new FSTUnicode();

        long start = System.currentTimeMillis();
        int idx = 0;
        int count = 0;
        // 预分配码点数组，重用避免重复分配
        char[] buffer = new char[2]; // 复用缓冲区
        for (char b1 = '\u4E00'; b1 <= '\u9FA5'; b1++) {
            buffer[0] = b1;
            for (char b2 = '\u4E00'; b2 <= '\u9FA5'; b2++) {
                if (b1 != b2) {
                    buffer[1] = b2;
                    dict.addWord(buffer, 2); // 直接传 char[] + 长度
                    count++;
                    if (count % 1000000 == 0) {
                        System.out.println(count);
                    }
                }
            }
            if (count > 80000000) {
                dict.rehash();
                break;
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("word count : " + count + " ; timeMillis " + (end - start));

        dict.addWord("分词");
        dict.addWord("彭胜");
        dict.addWord("彭胜文");
        dict.addWord("18");
        String text = "试一试分词效果，我得名字叫彭胜文，曾用名是彭胜,我18岁";
        List<ValueLocationDTO> result = FSTUnicodeUtil.maxMatchLocation(text, dict);
        System.out.println(result);

        dict.removeWord("18");
        dict.removeWord("彭胜文");
        String text2 = "试一试分词效果，我得名字叫彭胜文，曾用名是彭胜,我18岁";
        List<ValueLocationDTO> result2 = FSTUnicodeUtil.maxMatchLocation(text2, dict);
        System.out.println(result2);
    }

}
