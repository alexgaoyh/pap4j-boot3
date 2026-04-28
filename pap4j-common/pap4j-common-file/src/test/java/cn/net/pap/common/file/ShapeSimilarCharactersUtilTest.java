package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

public class ShapeSimilarCharactersUtilTest {
    private static final Logger log = LoggerFactory.getLogger(ShapeSimilarCharactersUtilTest.class);

    @Test
    public void similar() {
        ShapeSimilarCharactersUtil ssc = new ShapeSimilarCharactersUtil();
        try {
            // 学觉党鸴、墩礅暾憝、毋母每
            ssc.loadFile(TestResourceUtil.getFile("形近字.txt").getAbsolutePath().toString());
            char queryChar = '党'; // 替换为你要查询的字
            Set<Character> similarChars = ssc.querySimilarCharacters(queryChar);
            log.info("{}", "与 '" + queryChar + "' 形近的字有: " + similarChars);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
