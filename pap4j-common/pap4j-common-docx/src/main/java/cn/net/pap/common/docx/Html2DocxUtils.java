package cn.net.pap.common.docx;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public class Html2DocxUtils {

    private static final StringBuffer htmlHeader = new StringBuffer("<html><head><meta charset=\"utf-8\"><title></title></head><body>");

    private static final StringBuffer htmlFotter = new StringBuffer("</body></html>");

    public static boolean html2docx2UsingPOI(StringBuffer html, String docxAbsPath) {
        FileOutputStream outputStream = null;
        ByteArrayInputStream bais = null;
        try {
            outputStream = new FileOutputStream(docxAbsPath);

            StringBuffer all = new StringBuffer().append(htmlHeader).append(html).append(htmlFotter);
            String converted = image2Base64Convert(all.toString());

            byte[] b = converted.getBytes();
            bais = new ByteArrayInputStream(b);
            POIFSFileSystem poifs = new POIFSFileSystem();
            DirectoryEntry directory = poifs.getRoot();
            //WordDocument名称不允许修改
            directory.createDocument("WordDocument", bais);

            poifs.writeFilesystem(outputStream);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bais != null) {
                try {
                    bais.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }


    public static String image2Base64Convert(String html) throws IOException {
        Document doc = Jsoup.parse(html);
        Elements images = doc.select("img[src]");

        for (Element image : images) {
            String src = image.attr("src");
            if (src.startsWith("http")) {
                // 将网络图片转换为Base64编码
                String base64Image = convertImageToBase64(src);
                image.attr("src", base64Image);
            }
            // 处理样式属性
            String style = image.attr("style");
            if (style != null && !style.isEmpty()) {
                Boolean widthBool = false;
                // 解析样式属性并转换为img标签支持的设置
                String[] styleRules = style.split(";");
                for (String rule : styleRules) {
                    rule = rule.trim();
                    if (rule.startsWith("width")) {
                        image.attr("width", rule.substring(rule.indexOf(":") + 1).trim());
                        widthBool = true;
                    }
                    if (rule.startsWith("height")) {
                        image.attr("height", rule.substring(rule.indexOf(":") + 1).trim());
                    }
                }
                if (widthBool == false) {
                    image.attr("width", "530");
                }
                image.attr("style", "max-width: 100%; max-height: 100%; height: auto; width: auto;");
            }
        }
        return doc.html();
    }

    private static String convertImageToBase64(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.connect();

        // 获取内容类型
        String contentType = connection.getContentType();
        String mimeType = "data:image/";

        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            mimeType += "jpeg;base64,";
        } else if (contentType.contains("png")) {
            mimeType += "png;base64,";
        } else if (contentType.contains("gif")) {
            mimeType += "gif;base64,";
        } else {
        }

        InputStream input = connection.getInputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        input.close();
        byte[] imageBytes = output.toByteArray();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // 返回完整的Base64编码字符串，包括MIME类型
        return mimeType + base64Image;
    }
}
