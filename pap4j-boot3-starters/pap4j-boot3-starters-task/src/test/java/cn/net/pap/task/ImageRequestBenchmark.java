package cn.net.pap.task;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ImageRequestBenchmark {

    private static final Logger log = LoggerFactory.getLogger(ImageRequestBenchmark.class);

    private static final String IMAGE_URL = "";
    private static final int TEST_COUNT = 10000; // 测试次数

    // @Test
    public void benchmarkTest() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        List<Long> responseTimes = new ArrayList<>();

        log.info("开始测试图像请求响应时间...");
        log.info("测试URL: {}", IMAGE_URL);
        log.info("测试次数: {}", TEST_COUNT);
        log.info("----------------------------------");

        for (int i = 1; i <= TEST_COUNT; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(IMAGE_URL)).timeout(Duration.ofSeconds(15)).header("cookie", "alexgaoyh").build();

                long startTime = System.nanoTime();

                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                long duration = (System.nanoTime() - startTime) / 1_000_000; // 转为毫秒
                responseTimes.add(duration);

                log.info("第 {} 次请求 - 状态码: {}, 响应时间: {} ms, 图像大小: {} KB", i, response.statusCode(), duration, response.body().length / 1024);

            } catch (Exception e) {
                log.error("第 {} 次请求失败: ", i, e);
                responseTimes.add(-1L); // 用-1表示失败
            }
        }

        printStatistics(responseTimes);
    }

    private static void printStatistics(List<Long> responseTimes) {
        log.info("\n============== 测试结果统计 ==============");
        log.info("成功请求次数: {}/{}", responseTimes.stream().filter(t -> t > 0).count(), TEST_COUNT);

        log.info("平均响应时间: {} ms", responseTimes.stream().filter(t -> t > 0).mapToLong(Long::longValue).average().orElse(0));

        log.info("最短响应时间: {} ms", responseTimes.stream().filter(t -> t > 0).mapToLong(Long::longValue).min().orElse(0));

        log.info("最长响应时间: {} ms", responseTimes.stream().filter(t -> t > 0).mapToLong(Long::longValue).max().orElse(0));
    }
}
