package cn.net.pap.common.datastructure.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class DataPipelineTest {

    private static final Logger log = LoggerFactory.getLogger(DataPipelineTest.class);

    @Test
    @DisplayName("Function数据清洗与多级管道处理（Pipeline）")
    public void test1() {
        // 步骤 1：去除两端空格
        Function<String, String> trimSpace = String::trim;

        // 步骤 2：屏蔽敏感词（比如将 "bad" 替换为 "***"）
        Function<String, String> filterWords = str -> str.replace("bad", "***");

        // 步骤 3：统一转换为大写
        Function<String, String> toUpperCase = String::toUpperCase;

        // 核心：把所有步骤融合成一个单一的流水线函数
        Function<String, String> cleanDataPipeline = trimSpace.andThen(filterWords).andThen(toUpperCase);

        // 测试一条很脏的数据
        String rawData = "   this is a very bad day   ";
        String cleanData = cleanDataPipeline.apply(rawData);

        log.info("清洗前: '{}'", rawData);
        log.info("清洗后: '{}'", cleanData); // 输出: "THIS IS A VERY *** DAY"
    }

}
