package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

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

}
