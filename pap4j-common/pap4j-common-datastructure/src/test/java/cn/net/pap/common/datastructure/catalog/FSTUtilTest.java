package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.fst.FST;
import cn.net.pap.common.datastructure.fst.FSTUtil;
import cn.net.pap.common.datastructure.fst.ValueLocationDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

public class FSTUtilTest {

    @Test
    public void test() throws Exception {
        FST dict = new FST();
        for(int i = 0; i < 10000000; i++) {
            dict.addWord(i + "");
        }
        dict.addWord("分词");
        dict.addWord("彭胜");
        dict.addWord("彭胜文");

        String text = "试一试分词效果，我得名字叫彭胜文，曾用名是彭胜,我18岁";
        List<ValueLocationDTO> result = FSTUtil.maxMatchLocation(text, dict);
        System.out.println(result);

        dict.removeWord("18");
        dict.removeWord("彭胜文");
        String text2 = "试一试分词效果，我得名字叫彭胜文，曾用名是彭胜,我18岁";
        List<ValueLocationDTO> result2 = FSTUtil.maxMatchLocation(text2, dict);
        System.out.println(result2);

    }
}
