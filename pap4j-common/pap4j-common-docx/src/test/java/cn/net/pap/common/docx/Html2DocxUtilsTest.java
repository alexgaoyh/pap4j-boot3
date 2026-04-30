package cn.net.pap.common.docx;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Html2DocxUtilsTest {

    @Test
    public void html2DocxTest() throws Exception {
        // 这里是从富文本编辑器里面获得的一个 html。
        String editorHTML = "<p><b><span style=\"font-size: 18px;\">备注内容</span></b></p><p>备注增加图片内容</p><p><img src=\"http://gips3.baidu.com/it/u=3886271102,3123389489&amp;fm=3028&amp;app=3028&amp;f=JPEG&amp;fmt=auto?w=1280&amp;h=960\" style=\"width: 1280px;\"><br></p>\t\t";
        String destFilePath = Files.createTempFile("html2DocxTest", ".docx").toAbsolutePath().toString();
        try {
            boolean b = Html2DocxUtils.html2docx2UsingPOI(new StringBuffer(editorHTML), destFilePath);
            assertTrue(b);
        } finally {
            File file = new File(destFilePath);
            if (file.exists()) {
                file.delete();
            }
        }
    }

}
