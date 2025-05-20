package cn.net.pap.task;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ImageRequestBenchmark {

    private static final String IMAGE_URL = "";
    private static final int TEST_COUNT = 10000; // 测试次数

    // @Test
    public void benchmarkTest() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        List<Long> responseTimes = new ArrayList<>();

        System.out.println("开始测试图像请求响应时间...");
        System.out.println("测试URL: " + IMAGE_URL);
        System.out.println("测试次数: " + TEST_COUNT);
        System.out.println("----------------------------------");

        for (int i = 1; i <= TEST_COUNT; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(IMAGE_URL)).timeout(Duration.ofSeconds(15)).header("cookie", "alexgaoyh").build();

                long startTime = System.nanoTime();

                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                long duration = (System.nanoTime() - startTime) / 1_000_000; // 转为毫秒
                responseTimes.add(duration);

                System.out.printf("第 %2d 次请求 - 状态码: %d, 响应时间: %d ms, 图像大小: %d KB%n", i, response.statusCode(), duration, response.body().length / 1024);

            } catch (Exception e) {
                System.err.println("第 " + i + " 次请求失败: " + e.getMessage());
                responseTimes.add(-1L); // 用-1表示失败
            }
        }

        printStatistics(responseTimes);
    }

    private static void printStatistics(List<Long> responseTimes) {
        System.out.println("\n============== 测试结果统计 ==============");
        System.out.println("成功请求次数: " + responseTimes.stream().filter(t -> t > 0).count() + "/" + TEST_COUNT);

        System.out.println("平均响应时间: " + responseTimes.stream().filter(t -> t > 0).mapToLong(Long::longValue).average().orElse(0) + " ms");

        System.out.println("最短响应时间: " + responseTimes.stream().filter(t -> t > 0).mapToLong(Long::longValue).min().orElse(0) + " ms");

        System.out.println("最长响应时间: " + responseTimes.stream().filter(t -> t > 0).mapToLong(Long::longValue).max().orElse(0) + " ms");
    }
}
