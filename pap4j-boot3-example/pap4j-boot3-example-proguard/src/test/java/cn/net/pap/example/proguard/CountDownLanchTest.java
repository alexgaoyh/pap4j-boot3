package cn.net.pap.example.proguard;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CountDownLanchTest {

    //@Test
    public void service() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://127.0.0.1:30000/batch";

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 1; i <= 10; i++) {
            executor.execute(() -> {
                String result = restTemplate.getForObject(url, String.class);
                System.out.println("result = " + result);
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();
    }

}
