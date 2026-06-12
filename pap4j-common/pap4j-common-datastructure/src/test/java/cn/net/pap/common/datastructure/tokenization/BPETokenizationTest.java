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

    @Test
    public void performanceTest() {
        BPETokenization bpe = new BPETokenization();
        List<String> trainingData = Arrays.asList("机器学习", "学习模型", "深度模型", "深度学习", "科技公司", "公鸡", "科技企业", "民营企业", "民营科技企业");
        bpe.train(trainingData);

        // 构建一个长度为 60000 字符的长文本（重复 4000 次）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4000; i++) {
            sb.append("深度学习模型机器学习科技公司");
        }
        String longText = sb.toString();

        long startTime = System.nanoTime();
        List<String> tokens = bpe.tokenize(longText);
        long endTime = System.nanoTime();

        log.info("BPE分词测试 - 文本长度: {}, 分词数: {}", longText.length(), tokens.size());
        log.info("分词耗时: {} ms", (endTime - startTime) / 1e6);
    }

}
