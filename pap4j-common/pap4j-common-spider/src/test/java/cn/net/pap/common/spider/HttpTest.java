package cn.net.pap.common.spider;

import okhttp3.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HttpTest {

    private static final Clock clock = Clock.systemUTC();

    // 获取当前时间戳减去1970年的时间戳（毫秒）
    public static synchronized long getCurrentTimestampSinceEpochInMillis() {
        Instant now = Instant.now(clock);
        Instant epoch = Instant.ofEpochSecond(0);
        return now.toEpochMilli() - epoch.toEpochMilli();
    }

    public static void request(OkHttpClient httpClient, String url, String filePath, String orderSeq) {
        File file = new File(filePath);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), file))
                .addFormDataPart("fileName", file.getName())
                .addFormDataPart("orderSeq", orderSeq)

                .build();

        // 构建请求
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        // 发送请求
        try {
            Response response = httpClient.newCall(request).execute();
            boolean successful = response.isSuccessful();
            System.out.println(successful + " : " + response.body().string());
        } catch (IOException e) {
        }
    }

    // @Test
    public void request() throws Exception {
        OkHttpClient httpClient = new OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(2000);

        for (int idx = 0; idx < 2000; idx++) {
            int requestIdx = idx;
            executor.execute(() -> {
                String url = "/api?_t=" + getCurrentTimestampSinceEpochInMillis();
                request(httpClient, url, "\\dir\\" + String.format("%06d.jpg", requestIdx), requestIdx + "");
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

    }

}
