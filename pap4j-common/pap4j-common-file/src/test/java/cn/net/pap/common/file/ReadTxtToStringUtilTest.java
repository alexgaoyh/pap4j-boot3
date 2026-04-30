package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ReadTxtToStringUtilTest {
    private static final Logger log = LoggerFactory.getLogger(ReadTxtToStringUtilTest.class);

    @Test
    public void test1() throws IOException {
        File file = TestResourceUtil.getFile("txt/中文文件.txt");
        String txtPath = file.getAbsolutePath();
        String encode = ReadTxtToStringUtil.detectCharsetUsingICU4J(txtPath);
        log.info("{}", encode);
        String content = Files.readString(Paths.get(txtPath), Charset.forName(encode));
        log.info("{}", content);
    }

    @Test
    public void detectCharsetUsingICU4JTest() throws IOException {
        File file = TestResourceUtil.getFile("txt/ftp_426586352426085054.txt");
        String e = ReadTxtToStringUtil.detectCharsetUsingICU4J(file.getAbsolutePath());
        log.info("{}", e);
    }

    @Test
    public void readTxtToStringTest() throws IOException {
        String utf8 = ReadTxtToStringUtil.readFileContent(TestResourceUtil.getFile("txt/utf8.txt"));
        String utf8bom = ReadTxtToStringUtil.readFileContent(TestResourceUtil.getFile("txt/utf8bom.txt"));
        String gbk = ReadTxtToStringUtil.readFileContent(TestResourceUtil.getFile("txt/gbk.txt"));
        String big5 = ReadTxtToStringUtil.readFileContent(TestResourceUtil.getFile("txt/big5.txt"));
        String gb2312 = ReadTxtToStringUtil.readFileContent(TestResourceUtil.getFile("txt/gb2312.txt"));
        log.info("{}", utf8 + "\n\n" + utf8bom + "\n\n" + gbk + "\n\n" + big5 + "\n\n" + gb2312);
    }

    @Test
    public void createBomFileTest() throws IOException {
        File file = new File("test_bom.txt");
        try {
            // UTF-8 BOM 字节序列
            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            try (OutputStream os = new FileOutputStream(file)) {
                // 写入BOM
                os.write(bom);
                // 写入内容（UTF-8编码）
                os.write("你好，这是一个带BOM的UTF-8文件。".getBytes("UTF-8"));
            }
            log.info("{}", "文件已创建: " + file.getAbsolutePath());

            try (InputStream is = new FileInputStream(file)) {
                byte[] head = new byte[3];
                if (is.read(head) == 3 &&
                        head[0] == (byte) 0xEF &&
                        head[1] == (byte) 0xBB &&
                        head[2] == (byte) 0xBF) {
                    log.info("文件包含UTF-8 BOM");
                } else {
                    log.info("文件无BOM");
                }
            }
        } finally {
            if (file.exists()) {
                file.delete();
            }
        }
    }

}
