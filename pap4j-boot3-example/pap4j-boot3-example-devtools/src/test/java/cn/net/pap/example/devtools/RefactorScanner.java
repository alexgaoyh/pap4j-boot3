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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RefactorScanner {

    private static final Path ROOT_DIR = getProjectRootDir();
    private static final Path OUTPUT_FILE = ROOT_DIR.resolve(".ai/diagnostics/refactor_todo.md");

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
    public void testScanCodebase() throws Exception {
        System.out.println("Starting Codebase Refactoring Scan (Project JDK)...");
        List<Path> javaFiles = findJavaFiles(ROOT_DIR);
        System.out.println("Found " + javaFiles.size() + " Java files to scan.");

        List<Violation> violations = new ArrayList<>();
        for (Path file : javaFiles) {
            scanFile(file, violations);
        }

        writeReport(violations);
        System.out.println("Scan completed successfully. Scanned files: " + javaFiles.size() + ". Violations found: " + violations.size());
    }

    private static List<Path> findJavaFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String pathStr = p.toString().replace('\\', '/');
                        return !pathStr.contains("/target/") &&
                               !pathStr.contains("/.git/") &&
                               !pathStr.contains("/.ai/") &&
                               !pathStr.contains("/.agent/") &&
                               !pathStr.contains("/.idea/") &&
                               !pathStr.endsWith("/RefactorScanner.java") &&
                               !pathStr.endsWith("/DiagnosticsExtractor.java");
                    })
                    .collect(Collectors.toList());
        }
    }

    private static void scanFile(Path file, List<Violation> violations) {
        String relativePath = ROOT_DIR.relativize(file).toString().replace('\\', '/');
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Failed to read file: " + file + ", error: " + e.getMessage());
            return;
        }

        boolean isController = false;
        boolean isRepository = false;
        boolean isService = false;
        boolean hasClassTransactional = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains(" class ") || trimmed.contains(" interface ") || 
                trimmed.contains(" enum ") || trimmed.contains(" record ") ||
                trimmed.startsWith("public class ") || trimmed.startsWith("class ") ||
                trimmed.startsWith("public interface ") || trimmed.startsWith("interface ")) {
                break;
            }
            if (trimmed.contains("@RestController") || trimmed.contains("@Controller")) {
                isController = true;
            }
            if (trimmed.contains("@Repository") || (trimmed.contains("interface ") && file.getFileName().toString().endsWith("Repository.java"))) {
                isRepository = true;
            }
            if (trimmed.contains("@Service")) {
                isService = true;
            }
            if (trimmed.startsWith("@Transactional")) {
                hasClassTransactional = true;
            }
        }

        if (isController && hasClassTransactional) {
            violations.add(new Violation(relativePath, 1, "Transactional Annotation",
                    "Controller class should not be annotated with @Transactional (Transaction boundary should be in Service layer)."));
        }
        if (isRepository && hasClassTransactional) {
            violations.add(new Violation(relativePath, 1, "Transactional Annotation",
                    "Repository class should not be annotated with @Transactional (Transaction boundary should be in Service layer)."));
        }
        if (isService && hasClassTransactional) {
            violations.add(new Violation(relativePath, 1, "Transactional Annotation",
                    "Service class has @Transactional at class-level. It should be defined at the method-level instead."));
        }

        scanLineByLine(relativePath, lines, violations);
        scanMethodLengths(relativePath, lines, violations);
    }

    private static void scanLineByLine(String relativePath, List<String> lines, List<Violation> violations) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;
            String trimmed = line.trim();

            if (trimmed.contains("@Autowired")) {
                boolean isField = true;
                for (int j = i + 1; j < Math.min(lines.size(), i + 5); j++) {
                    String nextLine = lines.get(j).trim();
                    if (nextLine.isEmpty()) continue;
                    if (nextLine.contains("(")) {
                        isField = false;
                        break;
                    }
                    if (nextLine.endsWith(";")) {
                        break;
                    }
                }
                if (isField) {
                    violations.add(new Violation(relativePath, lineNum, "Field Injection",
                            "Avoid field injection (@Autowired). Use constructor injection instead."));
                }
            }

            if (trimmed.startsWith("@OneToMany") || trimmed.startsWith("@ManyToOne") ||
                trimmed.startsWith("@ManyToMany") || trimmed.startsWith("@OneToOne")) {
                violations.add(new Violation(relativePath, lineNum, "JPA Association Annotation",
                        "JPA association mapping annotation (" + trimmed.split("\\(|\\s")[0] + ") is forbidden. Fetch associations in Service layer instead."));
            }

            if (trimmed.startsWith("@Transactional")) {
                boolean hasRollbackFor = trimmed.contains("rollbackFor") || trimmed.contains("rollbackForClassName");
                boolean isReadOnly = trimmed.contains("readOnly = true") || trimmed.contains("readOnly=true");
                if (!hasRollbackFor && !isReadOnly) {
                    violations.add(new Violation(relativePath, lineNum, "Transactional Annotation",
                            "@Transactional write method is missing 'rollbackFor = Exception.class'."));
                }
            }
        }
    }

    private static void scanMethodLengths(String relativePath, List<String> lines, List<Violation> violations) {
        int braceDepth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean inBlockComment = false;
        boolean inLineComment = false;

        int methodStartLine = -1;
        String methodSignature = "";

        List<Integer> startLines = new ArrayList<>();
        List<String> signatures = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;

            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);

                if (inLineComment) {
                    break;
                }
                if (inBlockComment) {
                    if (ch == '*' && c + 1 < line.length() && line.charAt(c + 1) == '/') {
                        inBlockComment = false;
                        c++;
                    }
                    continue;
                }
                if (inString) {
                    if (ch == '\\') {
                        c++;
                    } else if (ch == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (inChar) {
                    if (ch == '\\') {
                        c++;
                    } else if (ch == '\'') {
                        inChar = false;
                    }
                    continue;
                }

                if (ch == '/' && c + 1 < line.length()) {
                    if (line.charAt(c + 1) == '/') {
                        inLineComment = true;
                        break;
                    } else if (line.charAt(c + 1) == '*') {
                        inBlockComment = true;
                        c++;
                        continue;
                    }
                }

                if (ch == '"') {
                    inString = true;
                    continue;
                }
                if (ch == '\'') {
                    inChar = true;
                    continue;
                }

                if (ch == '{') {
                    braceDepth++;
                    if (braceDepth == 2) {
                        methodStartLine = lineNum;
                        methodSignature = getSignature(lines, i);

                        boolean isNotMethod = methodSignature.contains(" class ") ||
                                              methodSignature.contains(" interface ") ||
                                              methodSignature.contains(" enum ") ||
                                              methodSignature.contains(" record ") ||
                                              methodSignature.contains("static {") ||
                                              methodSignature.trim().equals("{");

                        if (isNotMethod) {
                            startLines.add(-1);
                            signatures.add("");
                        } else {
                            startLines.add(methodStartLine);
                            signatures.add(methodSignature);
                        }
                    }
                } else if (ch == '}') {
                    if (braceDepth == 2 && !startLines.isEmpty()) {
                        int start = startLines.remove(startLines.size() - 1);
                        String sig = signatures.remove(signatures.size() - 1);
                        if (start != -1) {
                            int length = lineNum - start + 1;
                            if (length > 50) {
                                String methodName = extractMethodName(sig);
                                violations.add(new Violation(relativePath, start, "Method Length",
                                        "Method '" + methodName + "' exceeds 50 lines (Length: " + length + " lines)."));
                            }
                        }
                    } else if (braceDepth > 2) {
                        if (!startLines.isEmpty() && braceDepth - 1 == startLines.size()) {
                            startLines.remove(startLines.size() - 1);
                            signatures.remove(signatures.size() - 1);
                        }
                    }
                    braceDepth--;
                }
            }
            inLineComment = false;
        }
    }

    private static String getSignature(List<String> lines, int currentLineIdx) {
        StringBuilder sb = new StringBuilder();
        int startIdx = Math.max(0, currentLineIdx - 3);
        for (int i = startIdx; i <= currentLineIdx; i++) {
            sb.append(lines.get(i).trim()).append(" ");
        }
        return sb.toString().trim();
    }

    private static String extractMethodName(String signature) {
        int parenIdx = signature.indexOf('(');
        if (parenIdx == -1) return signature;
        String beforeParen = signature.substring(0, parenIdx).trim();
        String[] parts = beforeParen.split("\\s+");
        return parts[parts.length - 1] + "(...)";
    }

    private static void writeReport(List<Violation> violations) throws IOException {
        Files.createDirectories(OUTPUT_FILE.getParent());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(OUTPUT_FILE, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.println("# 待重构代码清单 (Refactor TODOs)");
            writer.println();
            writer.println("> 本文件由 [RefactorScanner](" + ROOT_DIR.toUri().toString() + "pap4j-boot3-example/pap4j-boot3-example-devtools/src/test/java/cn/net/pap/example/devtools/RefactorScanner.java) 自动扫描生成。请勾选已修复的内容。");
            writer.println();

            Map<String, List<Violation>> grouped = violations.stream()
                    .collect(Collectors.groupingBy(Violation::getType));

            String[] categories = {"Field Injection", "JPA Association Annotation", "Transactional Annotation", "Method Length"};
            String[] headers = {
                    "🚨 字段注入违规 (@Autowired)",
                    "🚨 JPA 级联关联注解违规 (禁止 OneToMany/ManyToOne 等)",
                    "🚨 事务注解规范违规 (@Transactional)",
                    "📏 超长方法违规 (超过 50 行)"
            };

            for (int k = 0; k < categories.length; k++) {
                String cat = categories[k];
                writer.println("## " + headers[k]);
                writer.println();
                List<Violation> list = grouped.getOrDefault(cat, Collections.emptyList());
                if (list.isEmpty()) {
                    writer.println("- 🎉 无违规项！");
                } else {
                    for (Violation v : list) {
                        writer.printf("- [ ] [%s:L%d](%s%s#L%d) - %s%n",
                                v.getFilenameOnly(), v.lineNum, ROOT_DIR.toUri().toString(), v.file, v.lineNum, v.message);
                    }
                }
                writer.println();
            }
        }
    }

    private static class Violation {
        String file;
        int lineNum;
        String type;
        String message;

        public Violation(String file, int lineNum, String type, String message) {
            this.file = file;
            this.lineNum = lineNum;
            this.type = type;
            this.message = message;
        }

        public String getType() {
            return type;
        }

        public String getFilenameOnly() {
            int lastSlash = file.lastIndexOf('/');
            return lastSlash == -1 ? file : file.substring(lastSlash + 1);
        }
    }
}
