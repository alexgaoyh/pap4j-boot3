package cn.net.pap.common.docx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadDocDocxUtilsTest {

    // @Test
    public void testReadDocDocx() {
        String doc = ReadDocDocUtils.readWord("C:\\Users\\86181\\Desktop\\doc.doc");
        String docx = ReadDocDocUtils.readWord("C:\\Users\\86181\\Desktop\\docx.docx");
        assertTrue(!doc.equals(""));
        assertTrue(!docx.equals(""));
    }

}
