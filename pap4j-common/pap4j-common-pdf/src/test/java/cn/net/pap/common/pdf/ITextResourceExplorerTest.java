package cn.net.pap.common.pdf;

import com.itextpdf.text.pdf.*;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

            // 先缓存所有图像对象，方便之后判断是否被引用为遮罩
            Map<PdfObject, PdfName> allImageObjects = new HashMap<>();

            // 遍历图像对象
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

                allImageObjects.put(obj, name); // 记录，用于反查遮罩引用

                System.out.println(" Image: " + name);

                PdfObject filter = imgDict.get(PdfName.FILTER);
                PdfObject decodeParms = imgDict.get(PdfName.DECODEPARMS);
                System.out.println("  Filter: " + filter);
                System.out.println("  DecodeParms: " + decodeParms);

                // 检查是否包含 JBIG2GLOBALS
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

                // 检查是否为遮罩图像
                boolean isMask = false;

                PdfBoolean imageMask = imgDict.getAsBoolean(PdfName.IMAGEMASK);
                if (imageMask != null && imageMask.booleanValue()) {
                    isMask = true;
                    System.out.println("  >>> ImageMask: true (this image is a stencil mask)");
                }

                PdfObject mask = imgDict.get(PdfName.MASK);
                if (mask != null) {
                    isMask = true;
                    if (mask.isArray()) {
                        System.out.println("  >>> Color Mask detected: " + mask);
                    } else {
                        System.out.println("  >>> Explicit Mask image: " + mask);
                    }
                }

                PdfArray decode = imgDict.getAsArray(PdfName.DECODE);
                if (decode != null) {
                    System.out.println("  >>> Decode array: " + decode);
                }

                PdfDictionary group = imgDict.getAsDict(PdfName.GROUP);
                if (group != null && PdfName.TRANSPARENCY.equals(group.getAsName(PdfName.S))) {
                    System.out.println("  >>> Transparency group detected");
                }

                if (!isMask) {
                    System.out.println("  >>> No direct mask detected for this image.");
                }

                System.out.println();
            }

            // 检查是否被作为其他图像的 SMask
            for (PdfName name : xobjects.getKeys()) {
                PdfDictionary dict = xobjects.getAsDict(name);
                if (dict == null || !PdfName.IMAGE.equals(dict.getAsName(PdfName.SUBTYPE))) continue;

                PdfObject smask = dict.get(PdfName.SMASK);
                if (smask != null && allImageObjects.containsKey(smask)) {
                    PdfName maskedImgName = allImageObjects.get(smask);
                    System.out.println("  >>> Image " + maskedImgName + " is used as SMask for image " + name);
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
