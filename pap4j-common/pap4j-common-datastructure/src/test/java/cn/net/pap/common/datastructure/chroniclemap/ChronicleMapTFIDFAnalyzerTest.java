package cn.net.pap.common.datastructure.chroniclemap;

import cn.net.pap.common.datastructure.chroniclemap.util.ChronicleMapTFIDFAnalyzer;
import cn.net.pap.common.datastructure.chroniclemap.util.RandomWordSelector;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 添加 JVM
 * --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
 * --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
 * --add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
 * --add-exports jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
 * --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
 * --add-opens java.base/java.lang=ALL-UNNAMED
 * --add-opens java.base/java.lang.reflect=ALL-UNNAMED
 * --add-opens java.base/java.io=ALL-UNNAMED
 * --add-opens java.base/java.util=ALL-UNNAMED
 * --add-opens java.base/java.nio=ALL-UNNAMED
 * --add-opens java.base/sun.nio.ch=ALL-UNNAMED
 */
public class ChronicleMapTFIDFAnalyzerTest {

    @TempDir
    Path tempDir;

    /**
     * TF = 词语在整个文档出现次数 / 整个文档的词语数
     *
     * IDF = LN(词语出现的次数K / 文档的数量)
     *
     * @throws IOException
     */
    // @Test
    void testTfIdfProcessing1() throws IOException {
        String path = tempDir.resolve("tfidf_data").toAbsolutePath().toString();

        try (ChronicleMapTFIDFAnalyzer analyzer = new ChronicleMapTFIDFAnalyzer(path)) {
            // 模拟输入文档
            analyzer.processDocument(Arrays.asList("架构", "高性能", "计算", "架构", "设计"));
            analyzer.processDocument(Arrays.asList("架构", "实现", "分布式", "系统", "架构"));
            analyzer.processDocument(Arrays.asList("量子", "计算", "前沿", "科技"));
            analyzer.processDocument(Arrays.asList("的", "架构", "的", "数据", "的"));
            analyzer.processDocument(Arrays.asList("数据", "计算"));

            analyzer.printTopResults(100);
        }
    }

    // @Test
    @org.junit.jupiter.api.Disabled("Requires local environment/dataset")
    void testTfIdfProcessing2() throws IOException {
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("Chinese_Names", ".txt");
        try {
            RandomWordSelector.init(tempFile.toAbsolutePath().toString());
    
            String path = tempDir.resolve("tfidf_data").toAbsolutePath().toString();
    
            try (ChronicleMapTFIDFAnalyzer analyzer = new ChronicleMapTFIDFAnalyzer(path)) {
                for(int i = 0; i < 10000000; i++) {
                    List<String> randomWords = RandomWordSelector.getRandomWords(50);
                    analyzer.processDocument(randomWords);
                    if(i % 10000 == 0) {
                        System.out.println(i);
                    }
                }
                analyzer.printTopResults(100);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

}