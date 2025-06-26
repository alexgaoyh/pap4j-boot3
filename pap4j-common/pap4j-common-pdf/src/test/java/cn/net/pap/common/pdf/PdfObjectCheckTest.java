package cn.net.pap.common.pdf;

import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfObjectCheckTest {

    // @Test
    public void refTest() throws Exception {
        String file = "input.pdf";
        PdfReader reader = new PdfReader(file);
        deepScan(reader);
        reader.close();
    }

    private void deepScan(PdfReader reader) {
        System.out.println("\n=== 对象扫描 ===");

        Map<Integer, String> objectTypes = new HashMap<>();

        for (int num = 0; num < reader.getXrefSize(); num++) {
            try {
                PdfObject obj = reader.getPdfObject(num);
                if (obj != null) {
                    objectTypes.put(num, determineObjectType(obj));
                }
            } catch (Exception ignored) {
            }
        }

        for (int num = 0; num < reader.getXrefSize(); num++) {
            try {
                PdfObject obj = reader.getPdfObject(num);
                if (obj == null) {
                    String refType = findReferenceType(reader, num);
                    System.err.printf("对象 #%d [%s] 缺失 | 被引用方式: %s%n", num, objectTypes.getOrDefault(num, "未知类型"), refType);
                    continue;
                }

                if (obj.isIndirect()) {
                    PRIndirectReference ref = (PRIndirectReference) obj;
                    if (ref.getNumber() < 0) {
                        System.err.printf("异常引用: 对象 #%d [%s] 有无效的引用编号%n", num, objectTypes.getOrDefault(num, "未知类型"));
                    }
                }
            } catch (Exception e) {
                System.err.printf("解析对象 #%d 时崩溃: %s%n", num, e.getClass().getSimpleName());
            }
        }
    }

    private String determineObjectType(PdfObject obj) {
        if (obj == null) return "NULL";
        if (obj.isDictionary()) {
            PdfDictionary dict = (PdfDictionary) obj;
            if (dict.get(PdfName.TYPE) != null) {
                return "字典(" + dict.get(PdfName.TYPE) + ")";
            }
            if (dict.get(PdfName.SUBTYPE) != null) {
                return "字典/" + dict.get(PdfName.SUBTYPE);
            }
            return "字典";
        }
        if (obj.isArray()) return "数组";
        if (obj.isStream()) return "流";
        if (obj.isString()) return "字符串";
        if (obj.isNumber()) return "数字";
        if (obj.isBoolean()) return "布尔";
        if (obj.isNull()) return "NULL";
        if (obj.isIndirect()) return "间接引用";
        return obj.getClass().getSimpleName();
    }

    private String findReferenceType(PdfReader reader, int targetObjNum) {
        List<String> references = new ArrayList<>();

        for (int num = 0; num < reader.getXrefSize(); num++) {
            try {
                PdfObject obj = reader.getPdfObject(num);
                if (obj instanceof PdfDictionary) {
                    checkDictForReference((PdfDictionary) obj, targetObjNum, num, references);
                } else if (obj instanceof PdfArray) {
                    checkArrayForReference((PdfArray) obj, targetObjNum, num, references);
                }
            } catch (Exception ignored) {
            }
        }

        return references.isEmpty() ? "未被引用(可能为游离对象)" : String.join(", ", references);
    }

    private void checkDictForReference(PdfDictionary dict, int targetNum, int parentNum, List<String> results) {
        for (PdfName key : dict.getKeys()) {
            PdfObject val = dict.get(key);
            if (val instanceof PRIndirectReference) {
                PRIndirectReference ref = (PRIndirectReference) val;
                if (ref.getNumber() == targetNum) {
                    results.add(String.format("通过 #%d 的键 %s 引用", parentNum, key));
                }
            }
        }
    }

    private void checkArrayForReference(PdfArray array, int targetNum, int parentNum, List<String> results) {
        for (int i = 0; i < array.size(); i++) {
            PdfObject elem = array.getPdfObject(i);
            if (elem instanceof PRIndirectReference) {
                PRIndirectReference ref = (PRIndirectReference) elem;
                if (ref.getNumber() == targetNum) {
                    results.add(String.format("通过 #%d 数组的第 %d 项引用", parentNum, i));
                }
            }
        }
    }

    // @Test
    public void testFontUsageCheck() throws Exception {
        String pdfPath = "input.pdf";
        PdfReader reader = new PdfReader(pdfPath);
        int numPages = reader.getNumberOfPages();

        Pattern tfPattern = Pattern.compile("/(\\S+)\\s+\\d*\\.?\\d*\\s+Tf");

        for (int i = 1; i <= numPages; i++) {
            System.out.println("\n=== 检查 Page " + i + " ===");

            PdfDictionary pageDict = reader.getPageN(i);
            PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
            PdfDictionary fontDict = resources != null ? resources.getAsDict(PdfName.FONT) : null;

            Set<String> declaredFonts = new HashSet<>();
            if (fontDict != null) {
                for (PdfName key : fontDict.getKeys()) {
                    declaredFonts.add(key.toString());
                }
                System.out.println("声明的字体名: " + declaredFonts);
            } else {
                System.out.println("此页缺少 Font 字典");
            }

            byte[] contentBytes = reader.getPageContent(i);
            String contentStr = new String(contentBytes, "ISO-8859-1");
            Matcher matcher = tfPattern.matcher(contentStr);

            Set<String> usedFonts = new HashSet<>();
            while (matcher.find()) {
                String fontName = "/" + matcher.group(1);
                usedFonts.add(fontName);
            }

            System.out.println("内容流中用到的字体名: " + usedFonts);

            for (String used : usedFonts) {
                if (!declaredFonts.contains(used)) {
                    System.out.println("警告: 内容流用到了未声明的字体: " + used);
                }
            }
        }

        reader.close();
    }

    // @Test
    public void testFontResourcesAndTextExtraction() throws Exception {
        String pdfPath = "input.pdf";
        PdfReader reader = new PdfReader(pdfPath, null, true);
        int numPages = reader.getNumberOfPages();
        Set<Integer> checkedObjects = new HashSet<>();

        for (int i = 1; i <= numPages; i++) {
            System.out.println("\n=== 页面 " + i + " 字体资源检查 ===");
            checkPageFontResources(reader, i, checkedObjects);

            System.out.println("\n=== 页面 " + i + " 尝试提取文本 ===");
            try {
                String text = PdfTextExtractor.getTextFromPage(reader, i);
                System.out.println("提取文本成功，前100字符: " + (text.length() > 100 ? text.substring(0, 100) + "..." : text));
            } catch (Exception e) {
                System.out.println("提取文本时报错: " + e.getMessage());
                e.printStackTrace(System.out);
                System.out.println("再次打印页面字体资源详细信息，协助定位问题：");
                checkPageFontResourcesDetailed(reader, i);
            }
        }

        reader.close();
    }

    private void checkPageFontResources(PdfReader reader, int pageNum, Set<Integer> checkedObjects) {
        PdfDictionary pageDict = reader.getPageN(pageNum);
        if (pageDict == null) {
            System.out.println("页面字典缺失");
            return;
        }
        PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
        if (resources == null) {
            System.out.println("页面缺少 Resources 字典");
            return;
        }
        PdfDictionary fontDict = resources.getAsDict(PdfName.FONT);
        if (fontDict == null) {
            System.out.println("页面缺少 Font 字典");
            return;
        }
        for (PdfName fontName : fontDict.getKeys()) {
            PdfObject fontObj = fontDict.get(fontName);
            if (fontObj == null) {
                System.out.println("字体 " + fontName + " 是 null");
                continue;
            }
            if (fontObj.isNull()) {
                System.out.println("字体 " + fontName + " 是 PdfNull");
                continue;
            }
            if (!(fontObj instanceof PRIndirectReference)) {
                System.out.println("字体 " + fontName + " 不是间接引用，而是 " + fontObj.getClass().getSimpleName());
                continue;
            }
            PRIndirectReference ref = (PRIndirectReference) fontObj;
            int objNum = ref.getNumber();
            if (checkedObjects.contains(objNum)) continue;
            checkedObjects.add(objNum);
            PdfObject resolved = reader.getPdfObject(objNum);
            if (resolved == null) {
                System.out.println("字体 " + fontName + " 引用对象缺失: objNum=" + objNum);
            } else if (!(resolved instanceof PdfDictionary)) {
                System.out.println("字体 " + fontName + " 对象不是字典: objNum=" + objNum + ", 类型=" + resolved.getClass().getSimpleName());
            } else {
                PdfDictionary fontRes = (PdfDictionary) resolved;
                PdfName subtype = fontRes.getAsName(PdfName.SUBTYPE);
                PdfName baseFont = fontRes.getAsName(PdfName.BASEFONT);
                System.out.println("字体 " + fontName + " objNum=" + objNum + ", /Subtype=" + (subtype != null ? subtype : "缺失") + ", /BaseFont=" + (baseFont != null ? baseFont : "缺失"));
            }
        }
    }

    private void checkPageFontResourcesDetailed(PdfReader reader, int pageNum) {
        PdfDictionary pageDict = reader.getPageN(pageNum);
        if (pageDict == null) {
            System.out.println("页面字典缺失");
            return;
        }
        PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
        if (resources == null) {
            System.out.println("页面缺少 Resources 字典");
            return;
        }
        PdfDictionary fontDict = resources.getAsDict(PdfName.FONT);
        if (fontDict == null) {
            System.out.println("页面缺少 Font 字典");
            return;
        }
        System.out.println("页面 " + pageNum + " /Font 字典详细内容:");
        for (PdfName key : fontDict.getKeys()) {
            PdfObject value = fontDict.get(key);
            System.out.println("字体名：" + key + ", 类型：" + (value == null ? "null" : value.getClass().getSimpleName()) + ", 是否为 PdfNull：" + (value != null && value.isNull()));
        }
    }

    // @Test
    public void testFontResourceIntegrity() throws Exception {
        String pdfPath = "input.pdf";
        File file = new File(pdfPath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + pdfPath);
        }

        PdfReader reader = new PdfReader(pdfPath, null, true);
        Set<Integer> checkedObjects = new HashSet<>();

        int numPages = reader.getNumberOfPages();
        System.out.println("总页数: " + numPages);

        for (int i = 1; i <= numPages; i++) {
            PdfDictionary pageDict = reader.getPageN(i);
            if (pageDict == null) {
                System.out.println("页面 " + i + " 字典缺失");
                continue;
            }
            PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
            if (resources == null) {
                System.out.println("页面 " + i + " 缺少 Resources 字典");
                continue;
            }

            PdfDictionary fontDict = resources.getAsDict(PdfName.FONT);
            if (fontDict == null) {
                System.out.println("页面 " + i + " 缺少 Font 字典");
                continue;
            }

            for (PdfName fontName : fontDict.getKeys()) {
                PdfObject fontObj = fontDict.get(fontName);
                if (fontObj == null || fontObj.isNull()) {
                    System.out.println("页面 " + i + " 的字体 " + fontName + " 引用为 null");
                    continue;
                }

                if (!(fontObj instanceof PRIndirectReference)) {
                    System.out.println("页面 " + i + " 的字体 " + fontName + " 不是间接引用，而是: " + fontObj.getClass().getSimpleName());
                    continue;
                }

                PRIndirectReference ref = (PRIndirectReference) fontObj;
                int objNum = ref.getNumber();

                if (checkedObjects.contains(objNum)) continue;

                checkedObjects.add(objNum);
                PdfObject resolved = reader.getPdfObject(objNum);

                if (resolved == null) {
                    System.out.println("页面 " + i + " 的字体 " + fontName + " 引用对象缺失: objNum=" + objNum);
                } else if (!(resolved instanceof PdfDictionary)) {
                    System.out.println("页面 " + i + " 的字体 " + fontName + " 对象不是字典: objNum=" + objNum + ", type=" + resolved.getClass().getSimpleName());
                } else {
                    PdfDictionary fontRes = (PdfDictionary) resolved;
                    PdfName subtype = fontRes.getAsName(PdfName.SUBTYPE);
                    PdfName baseFont = fontRes.getAsName(PdfName.BASEFONT);
                    System.out.println("页面 " + i + " 字体 " + fontName + " objNum=" + objNum + ", /Subtype=" + (subtype != null ? subtype : "缺失") + ", /BaseFont=" + (baseFont != null ? baseFont : "缺失"));

                    if (subtype == null) {
                        System.out.println("页面 " + i + " 的字体 " + fontName + " 缺少 /Subtype");
                    }
                    if (baseFont == null) {
                        System.out.println("页面 " + i + " 的字体 " + fontName + " 缺少 /BaseFont");
                    }
                }
            }
        }

        reader.close();
    }

    // @Test
    public void testCheckMissingReferences() throws Exception {
        String pdfPath = "input.pdf";
        File file = new File(pdfPath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + pdfPath);
        }

        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath, null, true);
            Set<Integer> checkedObjects = new HashSet<>();

            int numPages = reader.getNumberOfPages();
            System.out.println("总页数: " + numPages);

            for (int i = 1; i <= numPages; i++) {
                PdfDictionary pageDict = reader.getPageN(i);
                if (pageDict == null) {
                    System.out.println("页面 " + i + " 字典缺失");
                    continue;
                }
                checkDictionaryRaw(pageDict, checkedObjects, reader, "Page " + i);
            }

        } catch (Exception e) {
            System.out.println("加载 PDF 时异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private void checkDictionaryRaw(PdfDictionary dict, Set<Integer> checkedObjects, PdfReader reader, String context) {
        if (dict == null) return;

        for (PdfName key : dict.getKeys()) {
            PdfObject obj = dict.get(key);
            if (obj == null || obj.isNull()) {
                System.out.println("在 [" + context + "] key=" + key + " 的对象是 null");
                continue;
            }

            if (obj instanceof PRIndirectReference) {
                PRIndirectReference ref = (PRIndirectReference) obj;
                if (ref == null) {
                    System.out.println("在 [" + context + "] key=" + key + " 的 PRIndirectReference 是 null");
                } else {
                    int objNum = ref.getNumber();
                    if (!checkedObjects.contains(objNum)) {
                        checkedObjects.add(objNum);
                        PdfObject resolved = reader.getPdfObject(objNum);
                        if (resolved == null) {
                            System.out.println("缺失对象: objNum=" + objNum + " 在 [" + context + "] key=" + key);
                        } else {
                            System.out.println("找到对象: objNum=" + objNum + " type=" + resolved.getClass().getSimpleName() + " [" + context + "] key=" + key);
                            if (resolved instanceof PdfDictionary) {
                                checkDictionaryRaw((PdfDictionary) resolved, checkedObjects, reader, context + " -> obj " + objNum);
                            }
                        }
                    }
                }
            } else if (obj instanceof PdfDictionary) {
                checkDictionaryRaw((PdfDictionary) obj, checkedObjects, reader, context + " -> dict key=" + key);
            } else if (obj instanceof PdfArray) {
                checkArrayRaw((PdfArray) obj, checkedObjects, reader, context + " -> array key=" + key);
            }
        }
    }

    private void checkArrayRaw(PdfArray array, Set<Integer> checkedObjects, PdfReader reader, String context) {
        for (int i = 0; i < array.size(); i++) {
            PdfObject obj = array.getPdfObject(i);
            if (obj instanceof PRIndirectReference) {
                PRIndirectReference ref = (PRIndirectReference) obj;
                int objNum = ref.getNumber();
                if (!checkedObjects.contains(objNum)) {
                    checkedObjects.add(objNum);
                    PdfObject resolved = reader.getPdfObject(objNum);
                    if (resolved == null) {
                        System.out.println("缺失对象: objNum=" + objNum + " 在 [" + context + "] array idx=" + i);
                    } else {
                        System.out.println("找到对象: objNum=" + objNum + " type=" + resolved.getClass().getSimpleName() + " [" + context + "] array idx=" + i);
                        if (resolved instanceof PdfDictionary) {
                            checkDictionaryRaw((PdfDictionary) resolved, checkedObjects, reader, context + " -> obj " + objNum);
                        } else if (resolved instanceof PdfArray) {
                            checkArrayRaw((PdfArray) resolved, checkedObjects, reader, context + " -> obj " + objNum);
                        }
                    }
                }
            } else if (obj instanceof PdfDictionary) {
                checkDictionaryRaw((PdfDictionary) obj, checkedObjects, reader, context + " -> array dict idx=" + i);
            } else if (obj instanceof PdfArray) {
                checkArrayRaw((PdfArray) obj, checkedObjects, reader, context + " -> array idx=" + i);
            }
        }
    }

    // @Test
    public void fontTest() throws Exception {
        PdfReader reader = new PdfReader("input.pdf");
        PdfDictionary pageDict = reader.getPageN(1);
        PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
        PdfDictionary fontDict = resources.getAsDict(PdfName.FONT);

        for (PdfName fontKey : fontDict.getKeys()) {
            PdfObject fontObj = fontDict.get(fontKey);
            if (fontObj.isIndirect()) {
                PRIndirectReference fontRef = (PRIndirectReference) fontObj;
                PdfDictionary font = (PdfDictionary) PdfReader.getPdfObject(fontRef);

                PdfName baseFontName = font.getAsName(PdfName.BASEFONT);
                String baseFont = (baseFontName != null) ? baseFontName.toString() : "N/A";

                PdfDictionary fontDescriptor = font.getAsDict(PdfName.FONTDESCRIPTOR);
                boolean isEmbedded = (fontDescriptor != null);

                System.out.println("字体键: " + fontKey +
                        " | 名称: " + baseFont +
                        " | 是否嵌入: " + isEmbedded);
            }
        }
        reader.close();
    }

}
