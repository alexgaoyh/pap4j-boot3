package cn.net.pap.common.datastructure.tokenization;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class BPETokenizationTest {

    private static final Logger log = LoggerFactory.getLogger(BPETokenizationTest.class);

    @Test
    public void firstTest() {
        BPETokenization bpe = new BPETokenization();
        List<String> trainingData = Arrays.asList("机器学习", "学习模型", "深度模型", "深度学习", "科技公司", "公鸡", "科技企业", "民营企业", "民营科技企业");

        // 训练BPE模型
        bpe.train(trainingData);

        // 输出更新后的词表
        log.info("更新后的词表：");
        log.info("{}", bpe.getVocab());

        // 测试分词
        String testText = "深度学习模型";
        log.info("\n分词结果：");
        log.info("{}", bpe.tokenize(testText));
    }

}
