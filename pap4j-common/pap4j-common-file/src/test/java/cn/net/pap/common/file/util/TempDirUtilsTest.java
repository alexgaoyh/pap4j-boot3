package cn.net.pap.common.file.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TempDirUtilsTest {

    @BeforeEach
    void setUp() {
        System.setProperty("temp.dir.utils.dev", "false");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("temp.dir.utils.dev");
    }

    // @Test
    public void test1() throws IOException {
        TempDirUtils.withTempFile("myapp-", tempFile -> {
            try {
                Files.writeString(tempFile, "Hello, World!");
                String content = Files.readString(tempFile);
                System.out.println("文件内容: " + content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void test2() throws Exception {
        Path customDir = Files.createTempDirectory("test2-");

        String returnStr = TempDirUtils.withTempFile(customDir, "data-", tempFile -> {
            try {
                Files.writeString(tempFile, "{\"id\": 123, \"name\": \"test\"}");
                return "success";
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(returnStr.equals("success"));
        new File(customDir.toAbsolutePath().toString()).deleteOnExit();
    }

    // @Test
    public void test3() throws IOException {
        TempDirUtils.withTempDir("process-", tempDir -> {
            try {
                Path inputFile = tempDir.resolve("input.txt");
                Path outputFile = tempDir.resolve("output.txt");
                Path logFile = tempDir.resolve("logs/process.log");

                Files.createDirectories(logFile.getParent());

                Files.writeString(inputFile, "原始数据");
                Files.writeString(outputFile, "处理后的数据");
                Files.writeString(logFile, "处理日志");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // @Test
    public void test4() throws Exception {
        Path workspace = Files.createTempDirectory("test2-");

        List<String> returnList = TempDirUtils.withTempDir(workspace, "batch-", tempDir -> {
            try {
                Path inputDir = tempDir.resolve("input");
                Path outputDir = tempDir.resolve("output");
                Path tempDir2 = tempDir.resolve("temp");

                Files.createDirectories(inputDir);
                Files.createDirectories(outputDir);
                Files.createDirectories(tempDir2);

                // 模拟创建多个输入文件
                for (int i = 0; i < 3; i++) {
                    Path file = inputDir.resolve("file" + i + ".txt");
                    Files.writeString(file, "内容 " + i);
                }

                List<String> returnListInner = new ArrayList<>();
                returnListInner.add("success");
                return returnListInner;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(returnList.contains("success"));
        new File(workspace.toAbsolutePath().toString()).deleteOnExit();
    }

    // @Test
    public void test5() throws IOException {
        try {
            TempDirUtils.withTempDir("error-test-", tempDir -> {
                try {
                    int i = 1/0;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("test6")
    public void test6() throws IOException {
        try {
            TempDirUtils.withTempFile("myapp-", tempFile -> {
                try {
                    Files.writeString(tempFile, "Hello, World!");
                    String content = Files.readString(tempFile);
                    System.out.println("文件内容: " + content);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }


}
