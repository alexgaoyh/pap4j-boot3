package cn.net.pap.common.pdf;

import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfObjectCheckTest {

    private static final Logger log = LoggerFactory.getLogger(PdfObjectCheckTest.class);

    // @Test
    public void refTest() throws Exception {
        String file = "input.pdf";
        PdfReader reader = new PdfReader(file);
        deepScan(reader);
        reader.close();
    }

    private void deepScan(PdfReader reader) {
        log.info("\n=== 对象扫描 ===");

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
                    log.error("对象 #{} [{}] 缺失 | 被引用方式: {}", num, objectTypes.getOrDefault(num, "未知类型"), refType);
                    continue;
                }

                if (obj.isIndirect()) {
                    PRIndirectReference ref = (PRIndirectReference) obj;
                    if (ref.getNumber() < 0) {
                        log.error("异常引用: 对象 #{} [{}] 有无效的引用编号", num, objectTypes.getOrDefault(num, "未知类型"));
                    }
                }
            } catch (Exception e) {
                log.error("解析对象 #{} 时崩溃: {}", num, e.getClass().getSimpleName());
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
            log.info("\n=== 检查 Page {} ===", i);

            PdfDictionary pageDict = reader.getPageN(i);
            PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
            PdfDictionary fontDict = resources != null ? resources.getAsDict(PdfName.FONT) : null;

            Set<String> declaredFonts = new HashSet<>();
            if (fontDict != null) {
                for (PdfName key : fontDict.getKeys()) {
                    declaredFonts.add(key.toString());
                }
                log.info("声明的字体名: {}", declaredFonts);
            } else {
                log.info("此页缺少 Font 字典");
            }

            byte[] contentBytes = reader.getPageContent(i);
            String contentStr = new String(contentBytes, "ISO-8859-1");
            Matcher matcher = tfPattern.matcher(contentStr);

            Set<String> usedFonts = new HashSet<>();
            while (matcher.find()) {
                String fontName = "/" + matcher.group(1);
                usedFonts.add(fontName);
            }

            log.info("内容流中用到的字体名: {}", usedFonts);

            for (String used : usedFonts) {
                if (!declaredFonts.contains(used)) {
                    log.info("警告: 内容流用到了未声明的字体: {}", used);
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
            log.info("\n=== 页面 {} 字体资源检查 ===", i);
            checkPageFontResources(reader, i, checkedObjects);

            log.info("\n=== 页面 {} 尝试提取文本 ===", i);
            try {
                String text = PdfTextExtractor.getTextFromPage(reader, i);
                log.info("提取文本成功，前100字符: {}", (text.length() > 100 ? text.substring(0, 100) + "..." : text));
            } catch (Exception e) {
                log.error("提取文本时报错: ", e);
                log.info("再次打印页面字体资源详细信息，协助定位问题：");
                checkPageFontResourcesDetailed(reader, i);
            }
        }

        reader.close();
    }

    private void checkPageFontResources(PdfReader reader, int pageNum, Set<Integer> checkedObjects) {
        PdfDictionary pageDict = reader.getPageN(pageNum);
        if (pageDict == null) {
            log.info("页面字典缺失");
            return;
        }
        PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
        if (resources == null) {
            log.info("页面缺少 Resources 字典");
            return;
        }
        PdfDictionary fontDict = resources.getAsDict(PdfName.FONT);
        if (fontDict == null) {
            log.info("页面缺少 Font 字典");
            return;
        }
        for (PdfName fontName : fontDict.getKeys()) {
            PdfObject fontObj = fontDict.get(fontName);
            if (fontObj == null) {
                log.info("字体 {} 是 null", fontName);
                continue;
            }
            if (fontObj.isNull()) {
                log.info("字体 {} 是 PdfNull", fontName);
                continue;
            }
            if (!(fontObj instanceof PRIndirectReference)) {
                log.info("字体 {} 不是间接引用，而是 {}", fontName, fontObj.getClass().getSimpleName());
                continue;
            }
            PRIndirectReference ref = (PRIndirectReference) fontObj;
            int objNum = ref.getNumber();
            if (checkedObjects.contains(objNum)) continue;
            checkedObjects.add(objNum);
            PdfObject resolved = reader.getPdfObject(objNum);
            if (resolved == null) {
                log.info("字体 {} 引用对象缺失: objNum={}", fontName, objNum);
            } else if (!(resolved instanceof PdfDictionary)) {
                log.info("字体 {} 对象不是字典: objNum={}, 类型={}", fontName, objNum, resolved.getClass().getSimpleName());
            } else {
                PdfDictionary fontRes = (PdfDictionary) resolved;
                PdfName subtype = fontRes.getAsName(PdfName.SUBTYPE);
                PdfName baseFont = fontRes.getAsName(PdfName.BASEFONT);
                log.info("字体 {} objNum={}, /Subtype={}, /BaseFont={}", fontName, objNum, (subtype != null ? subtype : "缺失"), (baseFont != null ? baseFont : "缺失"));
            }
        }
    }

    private void checkPageFontResourcesDetailed(PdfReader reader, int pageNum) {
        PdfDictionary pageDict = reader.getPageN(pageNum);
        if (pageDict == null) {
            log.info("页面字典缺失");
            return;
        }
        PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
        if (resources == null) {
            log.info("页面缺少 Resources 字典");
            return;
        }
        PdfDictionary fontDict = resources.getAsDict(PdfName.FONT);
        if (fontDict == null) {
            log.info("页面缺少 Font 字典");
            return;
        }
        log.info("页面 {} /Font 字典详细内容:", pageNum);
        for (PdfName key : fontDict.getKeys()) {
            PdfObject value = fontDict.get(key);
            log.info("字体名：{}, 类型：{}, 是否为 PdfNull：{}", key, (value == null ? "null" : value.getClass().getSimpleName()), (value != null && value.isNull()));
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
        log.info("总页数: {}", numPages);

        for (int i = 1; i <= numPages; i++) {
            PdfDictionary pageDict = reader.getPageN(i);
            if (pageDict == null) {
                log.info("页面 {} 字典缺失", i);
                continue;
            }
            PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
            if (resources == null) {
                log.info("页面 {} 缺少 Resources 字典", i);
                continue;
            }

            PdfDictionary fontDict = resources.getAsDict(PdfName.FONT);
            if (fontDict == null) {
                log.info("页面 {} 缺少 Font 字典", i);
                continue;
            }

            for (PdfName fontName : fontDict.getKeys()) {
                PdfObject fontObj = fontDict.get(fontName);
                if (fontObj == null || fontObj.isNull()) {
                    log.info("页面 {} 的字体 {} 引用为 null", i, fontName);
                    continue;
                }

                if (!(fontObj instanceof PRIndirectReference)) {
                    log.info("页面 {} 的字体 {} 不是间接引用，而是: {}", i, fontName, fontObj.getClass().getSimpleName());
                    continue;
                }

                PRIndirectReference ref = (PRIndirectReference) fontObj;
                int objNum = ref.getNumber();

                if (checkedObjects.contains(objNum)) continue;

                checkedObjects.add(objNum);
                PdfObject resolved = reader.getPdfObject(objNum);

                if (resolved == null) {
                    log.info("页面 {} 的字体 {} 引用对象缺失: objNum={}", i, fontName, objNum);
                } else if (!(resolved instanceof PdfDictionary)) {
                    log.info("页面 {} 的字体 {} 对象不是字典: objNum={}, type={}", i, fontName, objNum, resolved.getClass().getSimpleName());
                } else {
                    PdfDictionary fontRes = (PdfDictionary) resolved;
                    PdfName subtype = fontRes.getAsName(PdfName.SUBTYPE);
                    PdfName baseFont = fontRes.getAsName(PdfName.BASEFONT);
                    log.info("页面 {} 字体 {} objNum={}, /Subtype={}, /BaseFont={}", i, fontName, objNum, (subtype != null ? subtype : "缺失"), (baseFont != null ? baseFont : "缺失"));

                    if (subtype == null) {
                        log.info("页面 {} 的字体 {} 缺少 /Subtype", i, fontName);
                    }
                    if (baseFont == null) {
                        log.info("页面 {} 的字体 {} 缺少 /BaseFont", i, fontName);
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
            log.info("总页数: {}", numPages);

            for (int i = 1; i <= numPages; i++) {
                PdfDictionary pageDict = reader.getPageN(i);
                if (pageDict == null) {
                    log.info("页面 {} 字典缺失", i);
                    continue;
                }
                checkDictionaryRaw(pageDict, checkedObjects, reader, "Page " + i);
            }

        } catch (Exception e) {
            log.error("加载 PDF 时异常: ", e);
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
                log.info("在 [{}] key={} 的对象是 null", context, key);
                continue;
            }

            if (obj instanceof PRIndirectReference) {
                PRIndirectReference ref = (PRIndirectReference) obj;
                if (ref == null) {
                    log.info("在 [{}] key={} 的 PRIndirectReference 是 null", context, key);
                } else {
                    int objNum = ref.getNumber();
                    if (!checkedObjects.contains(objNum)) {
                        checkedObjects.add(objNum);
                        PdfObject resolved = reader.getPdfObject(objNum);
                        if (resolved == null) {
                            log.info("缺失对象: objNum={} 在 [{}] key={}", objNum, context, key);
                        } else {
                            log.info("找到对象: objNum={} type={} [{}] key={}", objNum, resolved.getClass().getSimpleName(), context, key);
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
                        log.info("缺失对象: objNum={} 在 [{}] array idx={}", objNum, context, i);
                    } else {
                        log.info("找到对象: objNum={} type={} [{}] array idx={}", objNum, resolved.getClass().getSimpleName(), context, i);
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

                log.info("字体键: {} | 名称: {} | 是否嵌入: {}", fontKey, baseFont, isEmbedded);
            }
        }
        reader.close();
    }

}
