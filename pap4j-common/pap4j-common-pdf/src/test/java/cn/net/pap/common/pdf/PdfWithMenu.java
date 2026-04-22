package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.ChapterDTO;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.BaseFont;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfWithMenu {

    // @Test
    public void geneTest() throws Exception {
        gene("example.pdf");
    }

    private Font initSimsunFont() throws Exception {
        BaseFont baseFont = BaseFont.createFont(TestResourceUtil.getFile("simsun.ttc").getAbsolutePath() + ",0",
                BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
        return new Font(baseFont, 12);
    }

    // 生成目录
    public void generateTOC(Document document, Font font , List<ChapterDTO> chapters) throws DocumentException {
        // 添加目录标题
        document.add(new Phrase("目录\n\n", font));

        // 添加目录项
        for (ChapterDTO chapter : chapters) {
            Anchor link = new Anchor(chapter.getTitle() + "..........................", font);
            link.setReference("#" + chapter.getAnchorName());
            document.add(link);
            document.add(Chunk.NEWLINE);
        }
    }

    // 生成内容页
    private void generateContent(Document document, Font font , List<ChapterDTO> chapters) throws DocumentException {
        for (ChapterDTO chapter : chapters) {
            // 添加章节锚点
            Anchor anchor = new Anchor(chapter.getTitle() + "\n", font);
            anchor.setName(chapter.getAnchorName());
            document.add(anchor);

            // 添加章节内容
            document.add(new Phrase(chapter.getContent() + "\n\n", font));
        }
    }

    public void gene(String fileName) throws Exception {
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fileName));
        document.open();
        Font font = initSimsunFont();

        ChapterDTO chapterDTO1 = new ChapterDTO("第一章", "第一章的内容是如何实现对应的一系列跳转。", "section1");
        ChapterDTO chapterDTO2 = new ChapterDTO("第二章", "第二章讨论PDF生成的最佳实践。", "section2");
        ChapterDTO chapterDTO3 = new ChapterDTO("第三章", "第三章介绍高级功能实现。", "section3");
        List<ChapterDTO> chapterDTOList = new ArrayList<ChapterDTO>();
        chapterDTOList.add(chapterDTO1);
        chapterDTOList.add(chapterDTO2);
        chapterDTOList.add(chapterDTO3);

        // 实际的生成
        generateTOC(document, font, chapterDTOList);
        document.newPage();
        generateContent(document, font, chapterDTOList);
        document.close();
    }

}
