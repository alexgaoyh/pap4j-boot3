package cn.net.pap.common.datastructure.sequence;

import org.junit.jupiter.api.Test;

public class SequenceAlignmentUtilTest {

    @Test
    public void test() {
        String seq1 = "ALEXGAOYH";
        String seq2 = "ALEXGAOH";

        String[] result = SequenceAlignmentUtil.needlemanWunsch(seq1, seq2);
        System.out.println("Aligned Sequence 1: " + result[0]);
        System.out.println("Aligned Sequence 2: " + result[1]);

    }


}
