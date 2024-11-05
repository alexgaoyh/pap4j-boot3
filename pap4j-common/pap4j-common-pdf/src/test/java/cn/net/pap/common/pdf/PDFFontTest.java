package cn.net.pap.common.pdf;

import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

public class PDFFontTest {

    /**
     * 获得 PDF 内的字体
     * @throws IOException
     */
    @Test
    public void pdfTest() throws IOException {
        Set<String> strings = listFonts("C:\\Users\\86181\\Desktop\\pap.pdf");
        System.out.println(strings);
    }

    /**
     * Creates a set containing information about the not-embedded fonts within the src PDF file.
     *
     * @param src the path to a PDF file
     * @throws IOException
     */
    public Set<String> listFonts(String src) throws IOException {
        Set<String> set = new TreeSet<String>();
        PdfReader reader = new PdfReader(src);
        PdfDictionary resources;
        for (int k = 1; k <= reader.getNumberOfPages(); ++k) {
            resources = reader.getPageN(k).getAsDict(PdfName.RESOURCES);
            processResource(set, resources);
        }
        reader.close();
        return set;
    }

    /**
     * Finds out if the font is an embedded subset font
     *
     * @param name
     * @return true if the name denotes an embedded subset font
     */
    private boolean isEmbeddedSubset(String name) {
        //name = String.format("%s subset (%s)", name.substring(8), name.substring(1, 7));
        return name != null && name.length() > 8 && name.charAt(7) == '+';
    }

    private void processFont(PdfDictionary font, Set<String> set) {
        String name = font.getAsName(PdfName.BASEFONT).toString();
        if (isEmbeddedSubset(name)) {
            System.out.println(name + " is embedded subset");
            return;
        }

        PdfDictionary desc = font.getAsDict(PdfName.FONTDESCRIPTOR);

        //nofontdescriptor
        if (desc == null) {
            PdfArray descendant = font.getAsArray(PdfName.DESCENDANTFONTS);

            if (descendant == null) {
                set.add(name.substring(1));
            } else {
                for (int i = 0; i < descendant.size(); i++) {
                    PdfDictionary dic = descendant.getAsDict(i);
                    processFont(dic, set);
                }
            }
        }
        /**
         * (Type 1) embedded
         */
        else if (desc.get(PdfName.FONTFILE) != null)
            ;
        /**
         * (TrueType) embedded
         */
        else if (desc.get(PdfName.FONTFILE2) != null)
            ;
        /**
         * " (" + font.getAsName(PdfName.SUBTYPE).toString().substring(1) + ") embedded"
         */
        else if (desc.get(PdfName.FONTFILE3) != null)
            ;
        else {
            set.add(name.substring(1));
        }
    }

    /**
     * Extracts the names of the not-embedded fonts from page or XObject resources.
     *
     * @param set      the set with the font names
     * @param resource the resources dictionary
     */
    public void processResource(Set<String> set, PdfDictionary resource) {
        if (resource == null)
            return;
        PdfDictionary xobjects = resource.getAsDict(PdfName.XOBJECT);
        if (xobjects != null) {
            for (PdfName key : xobjects.getKeys()) {
                processResource(set, xobjects.getAsDict(key));
            }
        }
        PdfDictionary fonts = resource.getAsDict(PdfName.FONT);
        if (fonts == null)
            return;
        PdfDictionary font;
        for (PdfName key : fonts.getKeys()) {
            font = fonts.getAsDict(key);
            processFont(font, set);
        }
    }

}
