package cn.net.pap.example.devtools;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DiagnosticsExtractor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiagnosticsExtractor.class);
    private static final Path ROOT_DIR = getProjectRootDir();
    private static final Path OUTPUT_FILE = ROOT_DIR.resolve(".ai/diagnostics/test_failures.md");

    private static Path getProjectRootDir() {
        Path currentDir = Paths.get("").toAbsolutePath();
        while (currentDir != null) {
            Path agentDir = currentDir.resolve(".agent");
            Path aiDir = currentDir.resolve(".ai");
            if (Files.isDirectory(agentDir) && Files.isDirectory(aiDir)) {
                return currentDir;
            }
            currentDir = currentDir.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }

    @Test
    public void testExtractFailures() throws Exception {
        System.out.println("Starting Test Failure Diagnostics Extraction (Project JDK)...");
        List<Path> reportFiles = findSurefireReports(ROOT_DIR);
        System.out.println("Found " + reportFiles.size() + " surefire reports to inspect.");

        List<Failure> failures = new ArrayList<>();
        for (Path report : reportFiles) {
            parseReport(report, failures);
        }

        writeReport(failures);
        System.out.println("Extraction completed. Failures found: " + failures.size());
    }

    private static List<Path> findSurefireReports(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .filter(p -> {
                        String pathStr = p.toString().replace('\\', '/');
                        return pathStr.contains("/target/surefire-reports/");
                    })
                    .collect(Collectors.toList());
        }
    }

    private static void parseReport(Path reportFile, List<Failure> failures) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(reportFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            log.error("Failed to read report: " + reportFile, e);
            return;
        }

        boolean hasFailure = false;
        for (String line : lines) {
            if (line.contains("<<< FAILURE!") || line.contains("<<< ERROR!")) {
                hasFailure = true;
                break;
            }
        }

        if (!hasFailure) return;

        String currentTestSet = reportFile.getFileName().toString().replace(".txt", "");
        Failure currentFailure = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.contains("<<< FAILURE!") || trimmed.contains("<<< ERROR!")) {
                if (trimmed.contains("(")) {
                    if (currentFailure != null) {
                        failures.add(currentFailure);
                    }
                    currentFailure = new Failure();
                    currentFailure.testClass = currentTestSet;

                    int parenIdx = trimmed.indexOf('(');
                    currentFailure.methodName = trimmed.substring(0, parenIdx).trim();

                    List<String> rawStackTrace = new ArrayList<>();
                    for (int j = i + 1; j < lines.size(); j++) {
                        String nextLine = lines.get(j);
                        if (nextLine.contains("<<< FAILURE!") || nextLine.contains("<<< ERROR!") || nextLine.startsWith("------") || nextLine.startsWith("Test set:")) {
                            break;
                        }
                        rawStackTrace.add(nextLine);
                    }
                    currentFailure.rawStackTrace = rawStackTrace;
                }
            }
        }

        if (currentFailure != null) {
            failures.add(currentFailure);
        }
    }

    private static void writeReport(List<Failure> failures) throws IOException {
        Files.createDirectories(OUTPUT_FILE.getParent());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(OUTPUT_FILE, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.println("# 单元测试失败精炼报告 (Test Failure Diagnostics)");
            writer.println();
            writer.println("> 本文件由 [DiagnosticsExtractor](" + ROOT_DIR.toUri().toString() + "pap4j-boot3-example/pap4j-boot3-example-devtools/src/test/java/cn/net/pap/example/devtools/DiagnosticsExtractor.java) 自动提取生成。");
            writer.println("> 报告已自动过滤 Spring, JUnit 等第三方框架日志，仅保留与项目业务代码相关的堆栈细节。");
            writer.println();

            if (failures.isEmpty()) {
                writer.println("## 🎉 所有的单元测试全部通过！没有检测到失败。");
                return;
            }

            for (Failure f : failures) {
                writer.printf("## ❌ 失败测试: `%s.%s`%n", f.testClass, f.methodName);
                writer.println();
                writer.println("### 🔍 异常与精炼堆栈 (Filtered Exception & Stack Trace)");
                writer.println("```text");

                int printedLines = 0;
                for (String line : f.rawStackTrace) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    if (trimmed.startsWith("at ")) {
                        if (trimmed.contains("cn.net.pap.")) {
                            writer.println(line);
                            printedLines++;
                        }
                    } else {
                        writer.println(line);
                        printedLines++;
                    }
                }

                if (printedLines == 0) {
                    for (String line : f.rawStackTrace) {
                        writer.println(line);
                    }
                }

                writer.println("```");
                writer.println();
                writer.println("---");
                writer.println();
            }
        }
    }

    private static class Failure {
        String testClass;
        String methodName;
        List<String> rawStackTrace = new ArrayList<>();
    }
}
