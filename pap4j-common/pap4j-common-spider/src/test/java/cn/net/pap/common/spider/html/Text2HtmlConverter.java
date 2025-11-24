package cn.net.pap.common.spider.html;

import com.ibm.icu.text.BreakIterator;
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
        String input = "👨‍👩‍👧‍👦𠀀此处为文字[=地图]😎\uD83D\uDD2E\n第二行（包含[=模糊]内容）";
        String html = Text2HtmlConverter.convertTextToHtml("IMG123", input, "color:red;");
        System.out.println(html);
    }

    @Test
    void test2() {
        String input = "👨‍👩‍👧‍👦𠀀表格[=表格]残缺[=残缺]模糊[=模糊]😎\uD83D\uDD2E";
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

            IcuBreakIteratorReader reader = new IcuBreakIteratorReader(line);

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
                String c = reader.currentGrapheme();
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

        public static ParseResult parse(String bussId, IcuBreakIteratorReader r, int baseOffset) {
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
    // CSS解析器
    // ================================
    private static class AnnotationParser {

        public static ParseResult parse(String bussId, IcuBreakIteratorReader r, String zhuShuCSS, int baseOffset) {

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

            IcuBreakIteratorReader innerReader = r.subReader(markStart, endPos);

            while (!innerReader.end()) {
                int cp = innerReader.peek();

                if (cp == '[' && innerReader.peekNext() == '=') {
                    ParseResult sp = SpecialParser.parse(bussId, innerReader, baseOffset + startPos + 1);
                    if (sp != null) {
                        ann.addElement(sp.element);
                        continue;
                    }
                }

                String c = innerReader.currentGrapheme();
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
    // IcuBreakIteratorReader：使用 ICU4J 的 BreakIterator
    // ================================
    private static class IcuBreakIteratorReader {
        private final String text;
        private final BreakIterator breakIterator;
        private int currentBreak;
        private int nextBreak;
        private int codePointPosition;

        public IcuBreakIteratorReader(String text) {
            this.text = text;
            this.breakIterator = BreakIterator.getCharacterInstance();
            this.breakIterator.setText(text);
            this.currentBreak = breakIterator.first();
            this.nextBreak = breakIterator.next();
            this.codePointPosition = 0;
        }

        private IcuBreakIteratorReader(String text, boolean isSubReader) {
            // 用于 subReader 的私有构造函数
            this.text = text;
            this.breakIterator = BreakIterator.getCharacterInstance();
            this.breakIterator.setText(text);
            this.currentBreak = breakIterator.first();
            this.nextBreak = breakIterator.next();
            this.codePointPosition = 0;
        }

        public boolean end() {
            return currentBreak == BreakIterator.DONE;
        }

        public int peek() {
            if (end() || currentBreak >= text.length()) return -1;
            return text.codePointAt(currentBreak);
        }

        public int peekNext() {
            if (nextBreak == BreakIterator.DONE || nextBreak >= text.length()) return -1;
            return text.codePointAt(nextBreak);
        }

        public int next() {
            if (end()) return -1;

            int currentCodePoint = peek();
            currentBreak = nextBreak;
            nextBreak = (nextBreak == BreakIterator.DONE) ? BreakIterator.DONE : breakIterator.next();
            codePointPosition++;

            return currentCodePoint;
        }

        public int position() {
            return codePointPosition;
        }

        public void setPosition(int newPos) {
            // 简单实现：重置并移动到指定位置
            breakIterator.first();
            currentBreak = breakIterator.current();
            nextBreak = breakIterator.next();
            codePointPosition = 0;

            for (int i = 0; i < newPos && !end(); i++) {
                next();
            }
        }

        public int indexOf(int targetCodePoint) {
            int savedPosition = codePointPosition;
            int savedCurrentBreak = currentBreak;
            int savedNextBreak = nextBreak;

            int foundPosition = -1;
            while (!end()) {
                if (peek() == targetCodePoint) {
                    foundPosition = codePointPosition;
                    break;
                }
                next();
            }

            // 恢复状态
            setPosition(savedPosition);

            return foundPosition;
        }

        public String substring(int start, int end) {
            // 使用简单的字符位置计算
            IcuBreakIteratorReader tempReader = new IcuBreakIteratorReader(text, true);
            tempReader.setPosition(start);

            int startCharIndex = tempReader.currentBreak;
            tempReader.setPosition(end);
            int endCharIndex = tempReader.currentBreak;

            if (startCharIndex < 0 || endCharIndex < 0 || startCharIndex > endCharIndex) {
                return "";
            }

            return text.substring(startCharIndex, endCharIndex);
        }

        public IcuBreakIteratorReader subReader(int start, int end) {
            String subStr = substring(start, end);
            return new IcuBreakIteratorReader(subStr, true);
        }

        public String currentGrapheme() {
            if (end()) return "";

            if (nextBreak == BreakIterator.DONE) {
                // 最后一个字符
                return text.substring(currentBreak);
            } else {
                return text.substring(currentBreak, nextBreak);
            }
        }

        // 辅助方法：获取当前字符索引
        public int getCurrentCharIndex() {
            return currentBreak;
        }
    }

    public static String toMapSign2(String fileId, int idx) {
        return fileId + "_SIGN_" + idx;
    }

}
