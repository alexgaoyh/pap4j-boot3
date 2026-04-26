package cn.net.pap.common.docx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadDocDocxUtilsTest {

    @Test
    public void testReadDocDocx() {
        String doc = ReadDocDocUtils.readWord(TestResourceUtil.getFile("doc.doc").getAbsolutePath().toString());
        String docx = ReadDocDocUtils.readWord(TestResourceUtil.getFile("docx.docx").getAbsolutePath().toString());
        assertTrue(!doc.equals(""));
        assertTrue(!docx.equals(""));
    }

}
