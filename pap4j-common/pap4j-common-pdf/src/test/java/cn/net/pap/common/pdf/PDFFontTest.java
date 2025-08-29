package cn.net.pap.common.pdf;

import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFFontTest {

    /**
     * 字体信息类，包含字体名称和是否嵌入的标志
     */
    public static class FontInfo {
        private String name;
        private boolean embedded;
        private Integer pageNumber;

        public FontInfo(String name, boolean embedded, Integer pageNumber) {
            this.name = name;
            this.embedded = embedded;
            this.pageNumber = pageNumber;
        }

        public String getName() {
            return name;
        }

        public boolean isEmbedded() {
            return embedded;
        }

        public Integer getPageNumber() {
            return pageNumber;
        }

        @Override
        public String toString() {
            return name + " (" + (embedded ? "embedded" : "not embedded") + ")" + " (page: " + pageNumber + ")";
        }
    }

    /**
     * 获得 PDF 内的所有字体信息
     *
     * @throws IOException
     */
    @Test
    public void pdfTest() throws IOException {
        List<FontInfo> fontInfos = listAllFonts("input.pdf");
        for (FontInfo fontInfo : fontInfos) {
            System.out.println(fontInfo);
        }
    }

    /**
     * Creates a list containing information about all fonts within the src PDF file.
     *
     * @param src the path to a PDF file
     * @throws IOException
     */
    public List<FontInfo> listAllFonts(String src) throws IOException {
        List<FontInfo> fontList = new ArrayList<>();
        PdfReader reader = new PdfReader(src);
        PdfDictionary resources;
        for (int k = 1; k <= reader.getNumberOfPages(); ++k) {
            resources = reader.getPageN(k).getAsDict(PdfName.RESOURCES);
            processResource(fontList, resources, k);
        }
        reader.close();
        return fontList;
    }

    /**
     * Finds out if the font is an embedded subset font
     *
     * @param name
     * @return true if the name denotes an embedded subset font
     */
    private boolean isEmbeddedSubset(String name) {
        return name != null && name.length() > 8 && name.charAt(7) == '+';
    }

    private void processFont(PdfDictionary font, List<FontInfo> fontList, Integer pageNumber) {
        String name = font.getAsName(PdfName.BASEFONT).toString();
        boolean isEmbedded = false;

        if (isEmbeddedSubset(name)) {
            isEmbedded = true;
        } else {
            PdfDictionary desc = font.getAsDict(PdfName.FONTDESCRIPTOR);

            if (desc == null) {
                // 检查是否有子字体
                PdfArray descendant = font.getAsArray(PdfName.DESCENDANTFONTS);
                if (descendant != null) {
                    for (int i = 0; i < descendant.size(); i++) {
                        PdfDictionary dic = descendant.getAsDict(i);
                        processFont(dic, fontList, pageNumber);
                    }
                    return;
                }
            } else {
                // 检查是否嵌入
                if (desc.get(PdfName.FONTFILE) != null ||  // Type 1 embedded
                        desc.get(PdfName.FONTFILE2) != null || // TrueType embedded
                        desc.get(PdfName.FONTFILE3) != null) { // Other embedded font types
                    isEmbedded = true;
                }
            }
        }

        // 添加到字体列表
        fontList.add(new FontInfo(name.substring(1), isEmbedded, pageNumber));
    }

    /**
     * Extracts the fonts from page or XObject resources.
     *
     * @param fontList the list to store font information
     * @param resource the resources dictionary
     */
    public void processResource(List<FontInfo> fontList, PdfDictionary resource, Integer pageNumber) {
        if (resource == null) return;

        // 处理XObject中的字体
        PdfDictionary xobjects = resource.getAsDict(PdfName.XOBJECT);
        if (xobjects != null) {
            for (PdfName key : xobjects.getKeys()) {
                processResource(fontList, xobjects.getAsDict(key), pageNumber);
            }
        }

        // 处理当前资源中的字体
        PdfDictionary fonts = resource.getAsDict(PdfName.FONT);
        if (fonts == null) return;

        PdfDictionary font;
        for (PdfName key : fonts.getKeys()) {
            font = fonts.getAsDict(key);
            processFont(font, fontList, pageNumber);
        }
    }
}