package cn.net.pap.common.spider;

import okhttp3.*;
import org.assertj.core.util.Arrays;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpTest {

    private static final Logger log = LoggerFactory.getLogger(HttpTest.class);

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

        ExecutorService executor = new ThreadPoolExecutor(
                50,
                50,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(100),
                r -> new Thread(r, "request-test-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch latch = new CountDownLatch(2000);

        try {
            for (int idx = 0; idx < 2000; idx++) {
                int requestIdx = idx;
                executor.execute(() -> {
                    String url = "/api?_t=" + getCurrentTimestampSinceEpochInMillis();
                    request(httpClient, url, "\\dir\\" + String.format("%06d.jpg", requestIdx), requestIdx + "");
                    latch.countDown();
                });
            }

            latch.await();
        } finally {
            executor.shutdown();
            try {
                // 等待 2 秒让未完成的任务结束
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                    log.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

    }

    /**
     * auto-completion html check in jsoup
     */
    @Test
    public void completionHtmlTest() {
        String html = "<span class='class1'>Text 1</span>"
                + "<span class='class2'>Text 2</span>"
                + "<span class='class1'>Text 3</span>";

        String html2 = "<span class='class1'>Text 1(completion)</span>"
                + "<span class='class2'>Text 2(completion)</span>"
                + "<span class='class1'>Text 3(completion)";

        String[] strArray = Arrays.array(html, html2);
        for(String tmp :strArray){
            Document doc = Jsoup.parse(tmp);
            Elements spans = doc.select("span");
            for (Element span : spans) {
                String className = span.className();
                String text = span.text();
                System.out.println("Class: " + className + " - Text: " + text);
            }
            System.out.println();
        }
    }

    // @Test
    public void httpResponseTest() {
        String url = "http://192.168.1.66:5555/00035_00.jpg";
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Origin", "http://127.0.0.1:6060").GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status Code: " + response.statusCode());
            System.out.println("\nResponse Headers:");
            response.headers().map().forEach((key, values) -> {
                System.out.print(key + ": ");
                for (String value : values) {
                    System.out.print(value + " ");
                }
                System.out.println();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void removeAttrTest() {
        String html = "<div>\n" +
                "    <span style=\"color:red\" class=\"text\">直接子级 span</span>\n" +
                "    <p>\n" +
                "        <span data-attr=\"value\" class=\"highlight\">嵌套在 p 里的 span</span>\n" +
                "    </p>\n" +
                "    <div>\n" +
                "        <section>\n" +
                "            <span id=\"deep\" style=\"font-weight:bold\">深层嵌套的 span</span>\n" +
                "        </section>\n" +
                "    </div>\n" +
                "</div>";
        Document doc = Jsoup.parse(html);
        Elements spans = doc.select("span");
        String[] attributesToRemove = {"style", "data-attr", "class"};
        for (Element span : spans) {
            for (String attr : attributesToRemove) {
                span.removeAttr(attr);
            }
        }
        System.out.println(doc.body().html());
    }

    // @Test
    @org.junit.jupiter.api.Disabled("Requires local environment/dataset")
    public void downloadFileURLTest() {
        java.nio.file.Path tempFile = null;
        try {
            String ftpURL = "ftp://" +
                    "bj" + ":" +
                    "123456" + "@" +
                    "192.168.1.115" + ":" +
                    "21" + "/" +
                    "600tiff/2.tiff";
            URL url = new URL(ftpURL);
            URLConnection connection = url.openConnection();

            // 创建输出目录（如果不存在）
            tempFile = java.nio.file.Files.createTempFile("downloaded_", ".tiff");
            File outputFile = tempFile.toFile();

            // 使用try-with-resources确保流关闭
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (tempFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 拆分 html
     * @param html
     * @return
     */
    public String splitSpanChars(String html) {
        Document doc = Jsoup.parse(html);
        Elements charsSpans = doc.select("span.chars");

        for (Element span : charsSpans) {
            String text = span.text();
            if (text.length() > 1) {
                for (int idx = 0; idx < text.length(); idx++) {
                    String part = text.substring(idx, idx + 1);
                    Element newSpan = new Element("span");
                    newSpan.attributes().addAll(span.attributes());
                    newSpan.text(part);
                    span.before(newSpan);
                }
                span.remove();
            }
        }
        return doc.html();
    }

    /**
     * 极简异步 POST (JSON)
     * * @param url      请求地址
     * @param jsonBody 请求体
     */
    public static boolean sendPostBlindly(String url, String jsonBody) {
        try {
            HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody))
                    .timeout(Duration.ofSeconds(3))
                    .build();
            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding()).exceptionally(e -> {
                log.warn("sendPostBlindly", e);
                return null;
            });;
            return true;
        } catch (Exception ignore) {
            log.warn("sendPostBlindly", ignore);
            // 默默吞掉异常
            return false;
        }
    }

    // @Test
    public void sendPostBlindlyTest() throws Exception {
        long s = System.nanoTime();
        boolean b = sendPostBlindly("http://127.0.0.1:30000/echo/jsonSleep", "{\"a\":\"a\",\"b\":1}");
        Thread.sleep(1000);  // 等待1秒
        System.out.println(b + " 秒: " + (System.nanoTime() - s) / 1_000_000_000.0);
    }


}
