package cn.net.pap.common.datastructure.sequence;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SequenceAlignmentUtilTest {

    private static final Logger log = LoggerFactory.getLogger(SequenceAlignmentUtilTest.class);

    @Test
    public void test() {
        String seq1 = "ALEXGAOYH";
        String seq2 = "ALEXGAOH";

        String[] result = SequenceAlignmentUtil.needlemanWunsch(seq1, seq2);
        log.info("Aligned Sequence 1: {}", result[0]);
        log.info("Aligned Sequence 2: {}", result[1]);

    }


}
