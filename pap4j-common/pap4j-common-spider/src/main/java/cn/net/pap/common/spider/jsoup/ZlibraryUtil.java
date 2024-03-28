package cn.net.pap.common.spider.jsoup;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ZlibraryUtil {

    public static List<String> readFile(String file) {
        List<String> list = new ArrayList<>();
        String str;
        try {
            LineNumberReader reader = new LineNumberReader(new FileReader(file));
            while ((str = reader.readLine()) != null) {
                if (!str.isEmpty()) {
                    list.add(str);
                }
            }
        } catch (IOException e) {
        }
        return list;
    }

    /**
     * @param url         下载的url
     * @param cookie      登录后的cookie
     * @param baseSaveDir 存储到本地的路径
     * @param baseDomain  下载资源增加的前缀
     * @throws Exception
     */
    public static void getBookInfo(String url, String cookie, String baseSaveDir, String baseDomain) throws Exception {
        Connection connection = Jsoup.connect(url);
        java.util.Map<String, String> header = new java.util.HashMap<String, String>();
        header.put("cookie", cookie);
        Document document = connection.proxy("127.0.0.1", 7890).headers(header).get();
        Element cardBook = document.selectFirst("div[class=col-sm-3 details-book-cover-container]").child(0);
        Optional<Attribute> idOption = cardBook.attributes().asList().stream().filter(e -> e.getKey().equals("id")).findFirst();
        Optional<Attribute> isbnOption = cardBook.attributes().asList().stream().filter(e -> e.getKey().equals("isbn")).findFirst();
        Optional<Attribute> authorOption = cardBook.attributes().asList().stream().filter(e -> e.getKey().equals("author")).findFirst();
        Optional<Attribute> titleOption = cardBook.attributes().asList().stream().filter(e -> e.getKey().equals("title")).findFirst();
        String imageUrl = cardBook.childNodes().get(1).attr("data-src");
        String bookDownloadUrl = baseDomain + document.getElementsByClass("addDownloadedBook").attr("href");

        String downloadBusName = titleOption.get().getValue() + "[" + authorOption.get().getValue() + "]" + "{" + idOption.get().getValue() + "}";

        downloadImg(imageUrl, baseSaveDir + File.separator + downloadBusName, isbnOption.get().getValue() + ".jpg");
        downloadNet(bookDownloadUrl, baseSaveDir + File.separator + downloadBusName, isbnOption.get().getValue() + ".epub", cookie);
    }

    public static void downloadImg(String urlString, String baseSaveDir, String filename) throws Exception {
        URL url = new URL(urlString);
        URLConnection con = url.openConnection();
        con.setConnectTimeout(5000 * 1000);
        InputStream is = con.getInputStream();
        byte[] bs = new byte[1024];
        int len;
        int i = filename.length();
        File dir = new File(baseSaveDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        OutputStream os = new FileOutputStream(baseSaveDir + File.separator + filename);
        while ((len = is.read(bs)) != -1) {
            os.write(bs, 0, len);
        }
        os.close();
        is.close();
    }

    public static void downloadNet(String urlToDownload, String saveFilePath, String fileName, String cookie) throws MalformedURLException {
        try {
            URL url = new URL(urlToDownload);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Cookie", cookie);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(saveFilePath + File.separator + fileName);

                int bytesRead;
                byte[] buffer = new byte[4096];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();
            } else {
            }
            connection.disconnect();
        } catch (IOException e) {
        }
    }

}
