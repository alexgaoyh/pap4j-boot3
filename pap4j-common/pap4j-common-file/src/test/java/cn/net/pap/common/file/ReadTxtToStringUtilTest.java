package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.io.*;

public class ReadTxtToStringUtilTest {

    // @Test
    public void readTxtToStringTest() throws IOException {
        String utf8 = ReadTxtToStringUtil.readFileContent(new File("utf8.txt"));
        String utf8bom = ReadTxtToStringUtil.readFileContent(new File("utf8bom.txt"));
        String gbk = ReadTxtToStringUtil.readFileContent(new File("gbk.txt"));
        String big5 = ReadTxtToStringUtil.readFileContent(new File("big5.txt"));
        String gb2312 = ReadTxtToStringUtil.readFileContent(new File("gb2312.txt"));
        System.out.println(utf8 + "\n\n" + utf8bom + "\n\n" + gbk + "\n\n" + big5 + "\n\n" + gb2312);
    }

    // @Test
    public void createBomFileTest() throws IOException {
        File file = new File("test_bom.txt");
        // UTF-8 BOM 字节序列
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        try (OutputStream os = new FileOutputStream(file)) {
            // 写入BOM
            os.write(bom);
            // 写入内容（UTF-8编码）
            os.write("你好，这是一个带BOM的UTF-8文件。".getBytes("UTF-8"));
        }
        System.out.println("文件已创建: " + file.getAbsolutePath());

        try (InputStream is = new FileInputStream(file)) {
            byte[] head = new byte[3];
            if (is.read(head) == 3 &&
                    head[0] == (byte) 0xEF &&
                    head[1] == (byte) 0xBB &&
                    head[2] == (byte) 0xBF) {
                System.out.println("文件包含UTF-8 BOM");
            } else {
                System.out.println("文件无BOM");
            }
        }
        file.delete();
    }

}
