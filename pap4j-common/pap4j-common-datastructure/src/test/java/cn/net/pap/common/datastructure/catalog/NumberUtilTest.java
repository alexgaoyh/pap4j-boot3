package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.number.NumberUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NumberUtilTest {

    @Test
    public void formatRangesTest() {
        List<Long> numbers = new ArrayList<>();
        numbers.add(1L);
        numbers.add(2L);
        numbers.add(3L);
        numbers.add(4L);
        numbers.add(5L);
        numbers.add(7L);
        numbers.add(8L);
        numbers.add(9L);
        numbers.add(12L);

        String formatted = NumberUtil.formatRanges(numbers);
        List<Long> reNumbers = NumberUtil.formatRanges(formatted);
        assertTrue(numbers.equals(reNumbers));

    }

}
