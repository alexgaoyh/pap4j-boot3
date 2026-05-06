package cn.net.pap.common.datastructure.simHash;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class SimHashTest {

    private static final Logger log = LoggerFactory.getLogger(SimHashTest.class);

    @Test
    public void testSimHash() {
        // 示例文本
        String text1 = "这是 一个简单的文本，用于测试SimHash算法。";
        String text2 = "这是 一个简单的文档，用于演示SimHash的使用。";

        // 计算SimHash
        BigInteger simHash1 = SimHash.computeSimHash(text1);
        BigInteger simHash2 = SimHash.computeSimHash(text2);

        // 输出结果
        log.info("SimHash 1: {}", simHash1.toString(16));
        log.info("SimHash 2: {}", simHash2.toString(16));

        // 计算汉明距离
        int distance = SimHash.hammingDistance(simHash1, simHash2);
        log.info("汉明距离: {}", distance);
    }

}
