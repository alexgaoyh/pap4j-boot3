package cn.net.pap.common.spider.html;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Text2HtmlConverter {

    @Test
    void test1() {
        String input = "此处为文字[=地图]😎\uD83D\uDD2E\n第二行（包含[=模糊]内容）";
        String html = Text2HtmlConverter.convertTextToHtml("IMG123", input, "color:red;");
        System.out.println(html);
    }

    @Test
    void test2() {
        String input = "表格[=表格]残缺[=残缺]模糊[=模糊]😎\uD83D\uDD2E";
        String html = Text2HtmlConverter.convertTextToHtml("IMG456", input, "font-style:italic;");
        System.out.println(html);
    }

    // ================================
    // 主入口
    // ================================
    public static String convertTextToHtml(String bussId, String text, String css) {
        Document doc = Jsoup.parse("<html></html>");
        if (StringUtils.isBlank(text)) return "";

        String[] lines = text.trim().split("\\n");
        if (lines.length == 1 && "[=空白页=]".equals(lines[0].trim())) {
            return "<p><span data-type=\"空白页\" data-sign=\"" + toMapSign2(bussId, 1) + "\"></span></p>";
        }

        int offset = 0;
        for (String line : lines) {
            Element p = doc.createElement("p");
            doc.body().appendChild(p);

            List<TextElement> elements = LineProcessor.processLine(bussId, line, css, offset);
            for (TextElement el : elements) {
                p.append(el.toHtml());
            }

            // offset 依旧按原逻辑：使用 UTF-16 length，很安全
            offset += line.length();
        }

        return doc.body().html();
    }

    // ================================
    // 抽象基类
    // ================================
    private static abstract class TextElement {
        protected final String bussId;
        protected final String content;
        protected final int position;

        protected TextElement(String bussId, String content, int position) {
            this.bussId = bussId;
            this.content = content;
            this.position = position;
        }

        public abstract String toHtml();
    }

    // ================================
    // 普通字符元素
    // ================================
    private static class CharElement extends TextElement {
        private final String type;

        public CharElement(String bussId, String content, int position, String type) {
            super(bussId, content, position);
            this.type = type;
        }

        @Override
        public String toHtml() {
            return String.format("<span class=\"chars\" data-type=\"%s\" data-sign=\"%s\">%s</span>", type, toMapSign2(bussId, position), content);
        }
    }

    // ================================
    // 特殊标记元素
    // ================================
    private static class SpecialElement extends TextElement {
        private final String specialType;
        private final String displayChar;

        public SpecialElement(String bussId, String content, int position, String specialType, String displayChar) {
            super(bussId, content, position);
            this.specialType = specialType;
            this.displayChar = displayChar;
        }

        @Override
        public String toHtml() {
            return String.format("<span class=\"chars\" data-type=\"%s\" data-sign=\"%s\">%s</span>", specialType, toMapSign2(bussId, position), StringUtils.defaultString(displayChar, content));
        }
    }

    // ================================
    // CSS元素
    // ================================
    private static class AnnotationElement extends TextElement {
        private final String zhuShuCSS;
        private final List<TextElement> innerElements = new ArrayList<>();

        public AnnotationElement(String bussId, String zhuShuCSS, int position) {
            super(bussId, "", position);
            this.zhuShuCSS = zhuShuCSS;
        }

        public void addElement(TextElement e) {
            innerElements.add(e);
        }

        @Override
        public String toHtml() {
            String innerHtml = innerElements.stream().map(TextElement::toHtml).collect(Collectors.joining());
            return String.format("<span style=\"%s\" data-structure-type=\"zhuShu\" data-id=\"%s\">%s</span>", StringUtils.defaultString(zhuShuCSS), bussId + "|" + UUID.randomUUID(), innerHtml);
        }
    }

    // ================================
    // 行处理器
    // ================================
    private static class LineProcessor {

        public static List<TextElement> processLine(String bussId, String line, String zhuShuCSS, int baseOffset) {
            List<TextElement> result = new ArrayList<>();
            if (StringUtils.isBlank(line)) return result;

            CodePointReader reader = new CodePointReader(line);

            while (!reader.end()) {
                int cp = reader.peek();

                // [= 特殊标记检测
                if (cp == '[' && reader.peekNext() == '=') {
                    ParseResult sp = SpecialParser.parse(bussId, reader, baseOffset);
                    if (sp != null) {
                        result.add(sp.element);
                        continue;
                    }
                }

                // 注疏 （ ... ） 处理
                if (cp == '（') {
                    ParseResult ap = AnnotationParser.parse(bussId, reader, zhuShuCSS, baseOffset);
                    if (ap != null) {
                        result.add(ap.element);
                        continue;
                    }
                }

                // 普通字符
                String c = new String(Character.toChars(cp));
                if ("〓".equals(c)) {
                    result.add(new CharElement(bussId, c, baseOffset + reader.position() + 1, "集外字"));
                } else {
                    result.add(new CharElement(bussId, c, baseOffset + reader.position() + 1, "字符"));
                }

                reader.next();
            }

            return result;
        }
    }

    // ================================
    // SpecialParser
    // ================================
    private static class SpecialParser {

        public static ParseResult parse(String bussId, CodePointReader r, int baseOffset) {
            int startPos = r.position();

            // 找到 ']'
            int endPos = r.indexOf(']');
            if (endPos == -1) return null;

            // 内容 substring
            String inner = r.substring(startPos + 2, endPos).trim();

            String type = detectType(inner);
            String displayChar = detectDisplayChar(type);

            // 移动 reader
            r.setPosition(endPos + 1);

            SpecialElement element = new SpecialElement(bussId, inner, baseOffset + startPos + 1, type, displayChar);

            return new ParseResult(element);
        }

        private static String detectType(String inner) {
            if (inner.contains("地图")) return "地图";
            if (inner.contains("表格")) return "表格";
            if (inner.contains("模糊")) return "模糊";
            if (inner.contains("残缺")) return "残缺";
            return "未知";
        }

        private static String detectDisplayChar(String type) {
            return switch (type) {
                case "模糊" -> "■";
                case "残缺" -> "□";
                case "地图" -> "■";
                case "表格" -> "□";
                default -> "";
            };
        }
    }

    // ================================
    // CSS解析器（code point 版本）
    // ================================
    private static class AnnotationParser {

        public static ParseResult parse(String bussId, CodePointReader r, String zhuShuCSS, int baseOffset) {

            int startPos = r.position();
            r.next(); // 跳过（

            int open = 1;
            int markStart = r.position();

            while (!r.end() && open > 0) {
                int cp = r.next();
                if (cp == '（') open++;
                else if (cp == '）') open--;
            }

            if (open != 0) return null;

            int endPos = r.position() - 1;

            AnnotationElement ann = new AnnotationElement(bussId, zhuShuCSS, baseOffset + startPos);

            CodePointReader innerReader = r.subReader(markStart, endPos);

            while (!innerReader.end()) {
                int cp = innerReader.peek();

                if (cp == '[' && innerReader.peekNext() == '=') {
                    ParseResult sp = SpecialParser.parse(bussId, innerReader, baseOffset + startPos + 1);
                    if (sp != null) {
                        ann.addElement(sp.element);
                        continue;
                    }
                }

                String c = new String(Character.toChars(cp));
                ann.addElement(new CharElement(bussId, c, baseOffset + startPos + innerReader.position() + 1, "字符"));

                innerReader.next();
            }

            return new ParseResult(ann);
        }
    }

    // ================================
    // 解析返回值
    // ================================
    private static class ParseResult {
        private final TextElement element;

        public ParseResult(TextElement element) {
            this.element = element;
        }
    }

    // ================================
    // CodePointReader：关键部分 按 code point 安全遍历
    // ================================
    private static class CodePointReader {

        private final String str;
        private final int[] cps;
        private int idx = 0;

        public CodePointReader(String str) {
            this.str = str;
            cps = str.codePoints().toArray();
        }

        public boolean end() {
            return idx >= cps.length;
        }

        public int peek() {
            return end() ? -1 : cps[idx];
        }

        public int peekNext() {
            return idx + 1 < cps.length ? cps[idx + 1] : -1;
        }

        public int next() {
            return cps[idx++];
        }

        public int position() {
            return idx;
        }

        public void setPosition(int newPos) {
            idx = Math.min(newPos, cps.length);
        }

        public int indexOf(int cp) {
            for (int i = idx; i < cps.length; i++) {
                if (cps[i] == cp) return i;
            }
            return -1;
        }

        public String substring(int start, int end) {
            return new String(cps, start, end - start);
        }

        public CodePointReader subReader(int start, int end) {
            return new CodePointReader(new String(cps, start, end - start));
        }
    }

    public static String toMapSign2(String fileId, int idx) {
        return fileId + "_SIGN_" + idx;
    }

}
