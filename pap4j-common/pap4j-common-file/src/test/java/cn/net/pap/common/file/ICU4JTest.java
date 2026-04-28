package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Collator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ICU4JTest {
    private static final Logger log = LoggerFactory.getLogger(ICU4JTest.class);

    @Test
    public void test1() {
        // 提供符合不同语言习惯的字符串排序。 - 拼音
        Collator collator = Collator.getInstance(Locale.CHINA);
        List<String> words = Arrays.asList("张三", "李四", "王五");
        words.sort(collator);
        log.info("{}", words);

        words = Arrays.asList("王五", "张三", "李四");
        // unicode
        words.sort(Comparator.naturalOrder());
        log.info("{}", words);
    }

    @Test
    public void test2() {
        String text = "👨‍👩‍👧‍👦𠀀Hello";
        BreakIterator bi = BreakIterator.getCharacterInstance(Locale.CHINA);
        bi.setText(text);

        int start = bi.first();
        for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
            log.info("{}", text.substring(start, end));
        }
    }

}
