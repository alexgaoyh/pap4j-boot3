package cn.net.pap.common.pdf;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.*;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 1、获取某一个 PDF 下的所有字体.
 * <p>
 * 2、尝试替换字体，改为嵌入式字体，从而在某些场景下，达到缩小PDF大小的效果.
 */
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
        List<FontInfo> fontInfos = listAllFonts("format.pdf");
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

    /**
     * 重新生成 PDF ， 重置字体
     * 如果原 PDF 中字体是未嵌入，这里重置字体后，因为字体嵌入了，从而可以缩小 PDF 。
     *
     * @throws Exception
     */
    // @Test
    public void reGenePdfTest() throws Exception {
        String src = "C:/Users/86181/Desktop/GBT 9237-2017.pdf";
        String dest = "C:/Users/86181/Desktop/output.pdf";
        String font = "C:/Windows/Fonts/simsun.ttc,0";

        PdfReader reader = new PdfReader(src);

        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(dest));
        document.open();

        final PdfContentByte cb = writer.getDirectContent();

        // 使用本机中文字体；iText 会自动子集化
        final BaseFont bf = BaseFont.createFont(font, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            Rectangle ps = reader.getPageSizeWithRotation(page);
            float width = ps.getWidth();
            float height = ps.getHeight();
            float maxSize = 14400f; // 如果超出 14400，就缩小比例
            float scale = Math.min(maxSize / width, maxSize / height);
            document.setPageSize(new Rectangle(width * scale, height * scale));
            document.newPage();

            // --- 第一步：渲染文字（在图像下层）
            PdfReaderContentParser parser = new PdfReaderContentParser(reader);
            parser.processContent(page, new RenderListener() {
                @Override
                public void beginTextBlock() {
                }

                @Override
                public void endTextBlock() {
                }

                @Override
                public void renderImage(ImageRenderInfo renderInfo) { /* 跳过图像 */ }

                @Override
                public void renderText(TextRenderInfo info) {
                    try {
                        String text = info.getText();
                        if (text == null || text.isEmpty()) return;

                        LineSegment base = info.getBaseline();
                        Vector s = base.getStartPoint();
                        Vector e = base.getEndPoint();

                        float x = s.get(Vector.I1);
                        float y = s.get(Vector.I2);

                        float dx = e.get(Vector.I1) - x;
                        float dy = e.get(Vector.I2) - y;
                        double angleRad = Math.atan2(dy, dx);
                        float cos = (float) Math.cos(angleRad);
                        float sin = (float) Math.sin(angleRad);

                        float ascentY = info.getAscentLine().getStartPoint().get(Vector.I2);
                        float descentY = info.getDescentLine().getStartPoint().get(Vector.I2);
                        float fontSize = Math.max(0.1f, Math.abs(ascentY - descentY));

                        cb.saveState();
                        cb.beginText();
                        cb.setFontAndSize(bf, fontSize);
                        cb.setTextMatrix(cos, sin, -sin, cos, x, y);
                        cb.showText(text);
                        cb.endText();
                        cb.restoreState();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            // --- 第二步：渲染图像（在文字上层）
            parser.processContent(page, new RenderListener() {
                @Override
                public void beginTextBlock() {
                }

                @Override
                public void endTextBlock() {
                }

                @Override
                public void renderText(TextRenderInfo info) { /* 跳过文字 */ }

                @Override
                public void renderImage(ImageRenderInfo renderInfo) {
                    try {
                        PdfImageObject image = renderInfo.getImage();
                        if (image == null) return;

                        Image img = Image.getInstance(image.getImageAsBytes());
                        Matrix m = renderInfo.getImageCTM();

                        cb.addImage(img, m.get(Matrix.I11), m.get(Matrix.I12), m.get(Matrix.I21), m.get(Matrix.I22), m.get(Matrix.I31), m.get(Matrix.I32));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }

        document.close();
        reader.close();
    }

}