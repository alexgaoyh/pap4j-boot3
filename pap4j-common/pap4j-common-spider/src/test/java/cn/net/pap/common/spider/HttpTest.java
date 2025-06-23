package cn.net.pap.common.spider;

import okhttp3.*;
import org.assertj.core.util.Arrays;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

}
