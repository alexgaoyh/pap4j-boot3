package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

public class ShapeSimilarCharactersUtilTest {

    @Test
    public void similar() {
        ShapeSimilarCharactersUtil ssc = new ShapeSimilarCharactersUtil();
        try {
            // 学觉党鸴、墩礅暾憝、毋母每
            ssc.loadFile(TestResourceUtil.getFile("形近字.txt").getAbsolutePath().toString());
            char queryChar = '党'; // 替换为你要查询的字
            Set<Character> similarChars = ssc.querySimilarCharacters(queryChar);
            System.out.println("与 '" + queryChar + "' 形近的字有: " + similarChars);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
