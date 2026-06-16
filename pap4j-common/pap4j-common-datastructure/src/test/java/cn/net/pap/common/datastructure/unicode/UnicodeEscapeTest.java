package cn.net.pap.common.datastructure.unicode;

import com.ibm.icu.text.BreakIterator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnicodeEscapeTest {

    private static final Logger log = LoggerFactory.getLogger(UnicodeEscapeTest.class);
    // 究极复杂的 Unicode 测试文本
    private static final String TEST_TEXT = "你好👨‍👩‍👧‍👦世界。【卷之壹】📜「𪜀」字考：𝕿𝖍𝖎𝖘 𝖎𝖘 測試！《漢書·卷九十九》云：『王莽字巨君〇』〻。拼音：wáng mǎng。注音：ㄨㄤˊ ㄇㄤˇ。OCR異狀：１２３４ ＡＢＣ，𠀀𢀖𣢾（Ext B-C）。学者批注：此處繁簡混雜（如汉漢同現），且有龍 🐉 躍于淵。Page Ⅻ。";

    @Test
    @DisplayName("测试传统 String.length() 统计底层 char 数量")
    void traditionalStringLengthTest() {
        log.info("=== 开始执行 testTraditionalStringLength ===");

        int length = TEST_TEXT.length();
        log.info("测试文本: {}", TEST_TEXT);
        log.info("底层 char 的数量: {}", length);

        // 这里的 length 会非常大，因为包含了大量占 2 个 char 的生僻字和占 11 个 char 的复杂 Emoji
        assertTrue(length > 0, "字符串长度应大于 0");

        log.info("=== testTraditionalStringLength 执行完毕 ===\n");
    }

    @Test
    @DisplayName("测试使用 \\X 正则匹配真实视觉字符数量")
    void graphemeClusterMatchingTest() {
        log.info("=== 开始执行 testGraphemeClusterMatching ===");

        Pattern pattern = Pattern.compile("\\X");
        Matcher matcher = pattern.matcher(TEST_TEXT);

        int visualCharCount = 0;
        StringBuilder parsedResult = new StringBuilder();

        while (matcher.find()) {
            String visualUnit = matcher.group();
            parsedResult.append("[").append(visualUnit).append("] ");
            visualCharCount++;
        }

        log.info("视觉单元切分结果: \n{}", parsedResult.toString());
        log.info("底层 char 数量: {}", TEST_TEXT.length());
        log.info("真实视觉字符数: {}", visualCharCount);

        // 断言：证明 \X 切分出来的视觉字符总数，远远小于底层的 char 数量
        assertTrue(visualCharCount < TEST_TEXT.length(), "由于存在大量 Emoji 和 Surrogate Pairs，视觉字符数必然小于底层 char 数量");

        log.info("=== testGraphemeClusterMatching 执行完毕 ===");
    }

    @Test
    @DisplayName("测试扩展汉字的匹配与识别")
    public void testHanCharacterDifference() {
        // 扩展区 E 的字符：𫠠 (U+2E820)
        // 在 Java 中，增补平面字符需要用代理对表示，或直接用字符串
        String extEChar = "\uD87A\uDC20";

        // 1. 传统硬编码正则 (只覆盖基础块)
        String legacyRegex = "[\\u4e00-\\u9fa5]";
        assertFalse(Pattern.compile(legacyRegex).matcher(extEChar).find(),
                "传统正则在任何版本都匹配不到扩展区字符");

        // 2. Unicode 属性正则 (依赖 JDK 内置 Unicode 版本)
        // 注意：Java 中匹配汉字属性通常用 \p{IsHan} 或 \p{script=Han}
        String unicodeRegex = "\\p{IsHan}";

        boolean isMatch = Pattern.compile(unicodeRegex).matcher(extEChar).find();

        log.info("当前 JDK 版本: {}", System.getProperty("java.version"));
        log.info("字符 {} 是否被识别为汉字: {}", extEChar, isMatch);

        // 结果预测：
        // JDK 8: isMatch 为 false (Unicode 6.2 不含扩展区 E)
        // JDK 17: isMatch 为 true (Unicode 13.0 包含扩展区 E)
    }

    @Test
    @DisplayName("生成包含 IVS 变体字的 HTML 文件")
    public void generateGeHtml() throws IOException {
        // 需使用额外的库，然后可以看到 IVS 变体 的效果。
        String htmlContent = """
                    <!DOCTYPE html>
                    <html lang="zh-CN">
                    <head>
                        <meta charset="UTF-8">
                        <style>
                            /* 1. 核心字体配置：直接指向你的本地服务 */
                            @font-face {
                                font-family: 'B2Hana';
                                src: url('http://127.0.0.1:5555/B2Hana-Regular.woff2') format('woff2');
                            }
                            .ivs-box {
                                /* 2. 核心样式：字体 + 大尺寸 */
                                font-family: 'B2Hana', serif;
                                font-size: 120px;
                                display: inline-block;
                                margin: 20px;
                                padding: 20px;
                                border: 1px solid #ccc;
                            }
                            p { font-size: 20px; margin-left: 20px; color: #666; }
                        </style>
                    </head>
                    <body>
                        <p>古籍数字化：IVS 变体纯净版测试</p>
                        <!-- 标准字 -->
                        <div class="ivs-box">葛</div>
                        <!-- 葛 + VS17 (U+E0100) -->
                        <div class="ivs-box">&#x845B;&#xE0100;</div>
                        <!-- 葛 + VS18 (U+E0101) -->
                        <div class="ivs-box">&#x845B;&#xE0101;</div>
                        <p>说明：如果上面三个字长得完全一样，请检查本地 5555 端口的 woff2 文件是否加载成功。</p>
                    </body>
                    </html>
                """;

    }

    @Test
    @DisplayName("测试包含变体选择符的字符串长度")
    public void geTest() {
        // "葛" + VS17 (U+E0100 在 UTF-16 中表现为 \uDB40\uDD00)
        String ivsStr = "葛\uDB40\uDD00";
        // 错误：底层 char 数量，输出 3
        log.info("底层 char 长度: {}", ivsStr.length());
        // 部分正确：代码点数量 (基字 + 选择符)，输出 2
        log.info("代码点长度: {}", ivsStr.codePointCount(0, ivsStr.length()));
        // 完全正确：视觉上的字符数 (Grapheme)，输出 1
        log.info("视觉字符数: {}", countGraphemes(ivsStr));
    }

    // 计算实际的视觉字符数量（基于 JDK 内置的 BreakIterator）
    private static int countGraphemes(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(text);
        int count = 0;
        while (iterator.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }

    /**
     * https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip
     */
    @Test
    @DisplayName("读取并解析 Unihan 异体字数据库")
    public void unihanVariantsTest() {
        try (java.io.InputStream is = getClass().getResourceAsStream("/Unihan_Variants.txt")) {
            if (is == null) {
                log.warn("Unihan_Variants.txt not found in resources");
                return;
            }
            // 存储解析结果: Key 为标准字, Value 为该字的异体字集合
            Map<String, Set<String>> variantMap = new HashMap<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.trim().isEmpty()) {
                        continue;
                    }
                    // 按照 Tab 键分割行数据 格式示例: U+56DE    kZVariant    U+56D8 U+56EC
                    String[] parts = line.split("\t");
                    if (parts.length < 3) {
                        continue;
                    }
                    String baseUnicodeStr = parts[0];
                    String variantType = parts[1];
                    String variantUnicodeStr = parts[2];
                    // 如果你只需要最纯粹的异体字，可以过滤 kZVariant。 如果你想包含语义异体字、简繁体等，可以把这行注释掉。
                    if (!"kZVariant".equals(variantType) && !"kSemanticVariant".equals(variantType)) {
                        continue;
                    }
                    // 解析基础字
                    String baseChar = decodeUPlus(baseUnicodeStr);
                    // 解析异体字列表 (可能包含多个，用空格分割)
                    String[] variantTokens = variantUnicodeStr.split(" ");
                    Set<String> variantSet = variantMap.computeIfAbsent(baseChar, k -> new HashSet<>());
                    for (String token : variantTokens) {
                        variantSet.add(decodeUPlus(token));
                    }
                }
            }
            log.info("解析完成！共加载了 {} 个拥有异体字的汉字。", variantMap.size());
            variantMap.entrySet().stream().forEach(entry -> log.info("标准字: {} -> 异体字: {}", entry.getKey(), entry.getValue()));
        } catch (IOException e) {
            log.error("Failed to read Unihan_Variants.txt", e);
        }
    }

    /**
     * 将形如 "U+56DE" 或 "U+4E18<kMatthews" 的字符串转换为 Java 实际字符
     */
    private static String decodeUPlus(String uPlusToken) {
        // Unihan 数据库中，有时候字后面会跟上来源标记，例如 "<kMatthews" 或者 ":" 我们只需要保留 U+XXXX 的部分
        int tagIndex = uPlusToken.indexOf('<');
        if (tagIndex == -1) {
            tagIndex = uPlusToken.indexOf(':');
        }
        String cleanUPlus = (tagIndex != -1) ? uPlusToken.substring(0, tagIndex) : uPlusToken;
        if (cleanUPlus.startsWith("U+")) {
            // 提取 16 进制部分，并将其解析为 Unicode 代码点 (Code Point)
            int codePoint = Integer.parseInt(cleanUPlus.substring(2), 16);
            // Character.toString 完美支持 JDK 17，且能自动处理超过 0xFFFF 的补充平面代理对 (Surrogate Pairs)
            return Character.toString(codePoint);
        }
        return uPlusToken;
    }

    /**
     * https://www.unicode.org/ivd/data/latest/IVD_Sequences.txt
     */
    @Test
    @DisplayName("读取并解析 IVD 序列文件")
    public void ivdSequencesTest() {
        try (java.io.InputStream is = getClass().getResourceAsStream("/IVD_Sequences.txt")) {
            if (is == null) {
                log.warn("IVD_Sequences.txt not found in resources");
                return;
            }
            // 存储解析结果: Key 为标准字 (如 "葛"), Value 为包含变体选择符的序列集合 (如 ["葛+VS17", "葛+VS18"])
            Map<String, Set<String>> ivdMap = new HashMap<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 跳过注释和空行
                    if (line.startsWith("#") || line.trim().isEmpty()) {
                        continue;
                    }
                    // 按照分号分割字段 格式示例: 845B E0100; Hanyo-Denshu; FT2076
                    String[] parts = line.split(";");
                    if (parts.length < 3) {
                        continue;
                    }

                    // 取出第一部分（十六进制代码序列），并用空格分割 hexCodesStr = "845B E0100"
                    String hexCodesStr = parts[0].trim();
                    String[] hexCodes = hexCodesStr.split(" ");

                    // 正常的 IVS 序列一定是由 2 个代码点组成的：基础字 + 选择符
                    if (hexCodes.length != 2) {
                        continue;
                    }

                    // 解析十六进制代码为 Code Point
                    int baseCodePoint = Integer.parseInt(hexCodes[0], 16);
                    int vsCodePoint = Integer.parseInt(hexCodes[1], 16);

                    // 转换为 Java String (支持补充平面的 Surrogate Pairs)
                    String baseChar = Character.toString(baseCodePoint);

                    // 将变体选择符 (VS) 拼接在基础字后面，形成完整的视觉异体字
                    String ivsChar = baseChar + Character.toString(vsCodePoint);

                    // 存入 Map
                    ivdMap.computeIfAbsent(baseChar, k -> new HashSet<>()).add(ivsChar);
                }
            }

            log.info("IVS 解析完成！共加载了 {} 个拥有 IVS 异体字的汉字。", ivdMap.size());
            // 验证我们之前讨论的“葛”字
            String targetChar = "葛";
            Set<String> geVariants = ivdMap.get(targetChar);
            if (geVariants != null) {
                log.info("【{}】 的 IVS 异体字共有 {} 种写法。", targetChar, geVariants.size());
                for (String variant : geVariants) {
                    // 打印出异体字，并同时打印其底层的 char 长度验证我们的理论 (应该是 3)
                    log.info(" -> 异体字渲染: {} (底层 char 长度: {})", variant, variant.length());
                }
            } else {
                log.info("未找到【{}】的 IVS 异体字。", targetChar);
            }
        } catch (IOException e) {
            log.error("Failed to read IVD_Sequences.txt", e);
        }
    }

    /**
     * https://www.unicode.org/Public/emoji/13.1/emoji-test.txt
     */
    @Test
    @DisplayName("读取并解析本地 Emoji 13.1 序列文件")
    public void emojiSequencesTest() {
        // 采用你代码中的本地 Classpath 资源流式读取架构
        try (java.io.InputStream is = getClass().getResourceAsStream("/emoji-test.txt")) {
            if (is == null) {
                log.warn("emoji-test.txt not found in resources");
                return;
            }

            // 存储解析结果: Key 为生成的完整 Emoji 字符串, Value 为官方英文含义
            Map<String, String> emojiMap = new HashMap<>();

            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 1. 跳过纯注释和空行（emoji-test.txt 的数据行以十六进制码点开头，不以 # 开头）
                    if (line.startsWith("#") || line.trim().isEmpty()) {
                        continue;
                    }

                    // 2. 仅筛选完全限定（fully-qualified）的标准表情，排除残缺变体
                    if (!line.contains("; fully-qualified")) {
                        continue;
                    }

                    // 3. 按照分号分割字段
                    String[] parts = line.split(";");
                    if (parts.length < 2) {
                        continue;
                    }

                    // 4. 取出第一部分（十六进制代码序列），并用空格分割
                    String hexCodesStr = parts[0].trim();
                    String[] hexCodes = hexCodesStr.split(" ");

                    // 5. 将十六进制代码还原为 Java String (完美支持多码点、ZWJ 零宽连字)
                    StringBuilder emojiBuilder = new StringBuilder();
                    for (String hex : hexCodes) {
                        if (!hex.isBlank()) {
                            int cp = Integer.parseInt(hex, 16);
                            emojiBuilder.appendCodePoint(cp);
                        }
                    }
                    String emojiChar = emojiBuilder.toString();

                    // 6. 解析第二部分，从右侧的注释中提取纯粹的官方英文含义
                    // parts[1] 格式示例: " fully-qualified     # 😀 E1.0 grinning face"
                    String[] commentParts = parts[1].split("#");
                    if (commentParts.length < 2) {
                        continue;
                    }
                    String comment = commentParts[1].trim(); // 得到 "😀 E1.0 grinning face"

                    // 剥离前面的 "😀 E1.0 " 标记，提取最终含义
                    String meaning = comment.replaceAll("^\\S+\\s+E\\d+\\.\\d+\\s+", "");

                    // 7. 存入 Map
                    emojiMap.put(emojiChar, meaning);
                }
            }

            log.info("Emoji 解析完成！共从本地资源加载了 {} 个完全限定的标准表情。", emojiMap.size());

            // 8. 验证特定表情（100% 像素级对齐你代码中验证“葛”字异体字的底层逻辑）
            // 验证一个 Emoji 13.1 极其经典的 ZWJ 组合表情：“叹气脸”
            String targetEmoji = "😮‍💨";
            String meaning = emojiMap.get(targetEmoji);

            if (meaning != null) {
                log.info("【{}】 的官方含义为: {}", targetEmoji, meaning);
                // 打印出字符，并同时验证其底层的 char 长度
                log.info(" -> 字符渲染: {} (底层 char 长度: {})", targetEmoji, targetEmoji.length());
            } else {
                log.info("未找到【{}】的 Emoji 含义。", targetEmoji);
            }

            // 再验证一个最基础的单码位表情
            String simpleEmoji = "😂";
            String simpleMeaning = emojiMap.get(simpleEmoji);
            if (simpleMeaning != null) {
                log.info("【{}】 的官方含义为: {}", simpleEmoji, simpleMeaning);
                log.info(" -> 字符渲染: {} (底层 char 长度: {})", simpleEmoji, simpleEmoji.length());
            }

        } catch (IOException e) {
            log.error("Failed to read emoji-test.txt", e);
        }
    }

    // todo https://github.com/unicode-org/cldr/blob/main/common/annotations/zh.xml
    // todo https://github.com/unicode-org/cldr/blob/main/common/annotationsDerived/zh.xml
    // Unicode 官方在 GitHub 上维护了完整的 CLDR 仓库。针对中文（zh），你需要关注两个文件（基础表情与组合表情是分开的）：
    // 如果你不想在 Java 里写繁琐的 XML 解析代码，Unicode 官方还专门提供了一个 cldr-json 仓库，把上面的 XML 提前转成了 JSON，这简直是后端工程师的福音：

}
