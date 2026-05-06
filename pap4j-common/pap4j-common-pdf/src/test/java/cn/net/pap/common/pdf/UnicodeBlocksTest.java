package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.unicode.UnicodeBlocks;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * https://www.unicode.org/Public/UNIDATA/Blocks.txt
 * https://www.unicode.org/Public/16.0.0/ucd/Blocks.txt
 */
public class UnicodeBlocksTest {

    private static final Logger log = LoggerFactory.getLogger(UnicodeBlocksTest.class);

    @Test
    public void test1() throws IOException {
        URL url = new URL("https://www.unicode.org/Public/UNIDATA/Blocks.txt");
        try (InputStream in = url.openStream()) {
            UnicodeBlocks ub = new UnicodeBlocks(in);

            log.info("{}", ub.getBlockName("¥".codePointAt(0)));
            log.info("{}", ub.getBlockName("ϕ".codePointAt(0)));
            log.info("{}", ub.getBlockName("嘂".codePointAt(0)));
            log.info("{}", ub.getBlockName("☃".codePointAt(0)));
        }
    }

    @Test
    public void test2() throws IOException {
        URL url = new URL("https://www.unicode.org/Public/UNIDATA/Blocks.txt");
        try (InputStream in = url.openStream()) {
            UnicodeBlocks ub = new UnicodeBlocks(in);

            String fontName = "Arial";
            String blockName = "Basic Latin";

            boolean result = ub.checkFontCoverage(fontName, blockName);
            log.info("字体 {} 是否完整覆盖区块 [{}]? {}",
                    fontName, blockName, result ? "是" : "否");

        }
    }

    @Test
    public void test3() throws IOException {
        URL url = new URL("https://www.unicode.org/Public/UNIDATA/Blocks.txt");
        try (InputStream in = url.openStream()) {
            UnicodeBlocks ub = new UnicodeBlocks(in);

            ub.checkAllFontsAndBlocks();

        }
    }

    @Test
    public void test4() throws IOException {
        URL url = new URL("https://www.unicode.org/Public/UNIDATA/Blocks.txt");
        try (InputStream in = url.openStream()) {
            UnicodeBlocks ub = new UnicodeBlocks(in);

            ub.printBlockCharacters("CJK Unified Ideographs");

        }
    }


}

