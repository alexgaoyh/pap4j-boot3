package cn.net.pap.common.pdf;

import com.itextpdf.text.pdf.*;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;

public class ITextResourceExplorerTest {

    private static Set<PRIndirectReference> visited = new HashSet<>();

    @Test
    public void print() throws Exception {
        PdfReader reader = new PdfReader(new FileInputStream("input.pdf"));
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            PdfDictionary pageDict = reader.getPageN(i);
            System.out.println(">>> Page " + i);
            exploreObject(pageDict, reader, 0);
        }

        System.out.println("----------------------------------------------------------------------------");

        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            System.out.println("Page " + i);
            PdfDictionary pageDict = reader.getPageN(i);
            PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
            if (resources == null) continue;
            PdfDictionary xobjects = resources.getAsDict(PdfName.XOBJECT);
            if (xobjects == null) continue;

            for (PdfName name : xobjects.getKeys()) {
                PdfObject obj = xobjects.get(name);
                PdfDictionary imgDict = null;
                if (obj.isIndirect()) {
                    imgDict = (PdfDictionary) PdfReader.getPdfObject(obj);
                } else if (obj.isDictionary()) {
                    imgDict = (PdfDictionary) obj;
                }
                if (imgDict == null) continue;

                PdfName subtype = imgDict.getAsName(PdfName.SUBTYPE);
                if (!PdfName.IMAGE.equals(subtype)) continue;

                System.out.println(" Image: " + name);

                PdfObject filter = imgDict.get(PdfName.FILTER);
                PdfObject decodeParms = imgDict.get(PdfName.DECODEPARMS);
                System.out.println("  Filter: " + filter);
                System.out.println("  DecodeParms: " + decodeParms);

                if (decodeParms != null) {
                    if (decodeParms.isDictionary()) {
                        PdfDictionary dpDict = (PdfDictionary) decodeParms;
                        PdfObject jbig2Globals = dpDict.get(PdfName.JBIG2GLOBALS);
                        if (jbig2Globals != null) {
                            System.out.println("  >>> Found JBIG2GLOBALS: " + jbig2Globals);
                        }
                    } else if (decodeParms.isArray()) {
                        PdfArray dpArray = (PdfArray) decodeParms;
                        for (PdfObject dpObj : dpArray) {
                            if (dpObj.isDictionary()) {
                                PdfDictionary dpDict = (PdfDictionary) dpObj;
                                PdfObject jbig2Globals = dpDict.get(PdfName.JBIG2GLOBALS);
                                if (jbig2Globals != null) {
                                    System.out.println("  >>> Found JBIG2GLOBALS in array: " + jbig2Globals);
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    private static void exploreObject(PdfObject obj, PdfReader reader, int level) {
        if (obj == null) return;

        String indent = "  ".repeat(level);

        if (obj.isIndirect()) {
            PRIndirectReference ref = (PRIndirectReference) obj;
            if (visited.contains(ref)) return;
            visited.add(ref);

            obj = PdfReader.getPdfObject(obj);
            System.out.println(indent + "Indirect Reference: " + ref.getNumber() + " ->");
            exploreObject(obj, reader, level + 1);
        } else if (obj.isDictionary()) {
            PdfDictionary dict = (PdfDictionary) obj;
            for (PdfName key : dict.getKeys()) {
                PdfObject value = dict.get(key);
                System.out.println(indent + key + " => " + value);
                if (key.equals(PdfName.JBIG2GLOBALS)) {
                    System.out.println(indent + ">>> Found JBIG2GLOBALS: " + value);
                }
                exploreObject(value, reader, level + 1);
            }
        } else if (obj.isArray()) {
            PdfArray array = (PdfArray) obj;
            for (PdfObject element : array) {
                exploreObject(element, reader, level + 1);
            }
        } else if (obj.isStream()) {
            PdfStream stream = (PdfStream) obj;
            System.out.println(indent + "Stream with length: " + stream.length());
            exploreObject(stream.getAsDict(PdfName.RESOURCES), reader, level + 1);
        }
    }

}
