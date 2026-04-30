package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FilesTest {
    private static final Logger log = LoggerFactory.getLogger(FilesTest.class);

    @Test
    public void walkFileTreeTest() throws IOException {
        String folderPath = System.getProperty("user.dir");
        Path start = Paths.get(folderPath);

        List<String> fileNames = new ArrayList<>();

        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                fileNames.add(file.getFileName().toString());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                //log.info("进入目录: " + dir.toAbsolutePath());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                //System.err.println("访问文件失败: " + file.toAbsolutePath() + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        Collections.sort(fileNames);

        String destFilePath = Files.createTempFile("walkFileTreeTest", ".txt").toAbsolutePath().toString();
        try {
            Files.write( Paths.get(destFilePath), fileNames, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } finally {
            File file = new File(destFilePath);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    /**
     * 读取文件并按行排序（自定义排序器）
     * @param inputFilePath 输入文件路径
     * @param outputFilePath 输出文件路径
     * @param comparator 自定义比较器 不确定可传递默认： Comparator.naturalOrder()
     * @throws IOException 如果文件读写失败
     */
    public static void sortFileLinesWithComparator(String inputFilePath, String outputFilePath,
                                                   java.util.Comparator<String> comparator) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(inputFilePath));
        lines.sort(comparator);
        Files.write(Paths.get(outputFilePath), lines);
    }

    @Test
    public void readTest1() throws Exception {
        List<String> lines = Arrays.asList(
                "NodeA:Status1:1000",
                "NodeB:Status2:1500",
                "NodeC:Status3:2300"
        );
        for(int idx = 1; idx < lines.size(); idx++) {
            String before = lines.get(idx - 1);
            String current = lines.get(idx);
            String[] beforeSplit = before.split(":");
            String[] currentSplit = current.split(":");
            log.info("{}", Long.parseLong(currentSplit[2].trim()) - Long.parseLong(beforeSplit[2].trim()));
        }
    }

    @Test
    public void sortFileLinesWithComparatorTest() throws IOException {
        List<String> rawData = Arrays.asList("Banana", "Apple", "Cherry", "Date");
        String inputPath = Files.createTempFile("sort-input", ".txt").toAbsolutePath().toString();
        String outputPath = Files.createTempFile("sort-output", ".txt").toAbsolutePath().toString();
        try {
            Files.write(Paths.get(inputPath), rawData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            sortFileLinesWithComparator(inputPath, outputPath, Comparator.naturalOrder());

            List<String> sortedResult = Files.readAllLines(Paths.get(outputPath));
            sortedResult.equals(Arrays.asList("Apple", "Banana", "Cherry", "Date"));

        } finally {
            File inputFile = new File(inputPath);
            if (inputFile.exists()) {
                inputFile.delete();
            }
            File outputFile = new File(outputPath);
            if (outputFile.exists()) {
                outputFile.delete();
            }
        }

    }

    @Test
    public void convertLineEndingsTest() throws IOException {
        String folderPath = System.getProperty("user.dir");
        Path startDir = Paths.get(folderPath);

        try (Stream<Path> paths = Files.walk(startDir)) {
            paths.filter(path -> path.toString().endsWith(".java") && path.toString().contains("PageDTO"))
                    .forEach(path -> {
                        convertLineEndings(path.toAbsolutePath().toString());
                        log.info("{}", "已处理: " + path);
                    });
        } catch (IOException e) {
            System.err.println("遍历目录时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean convertLineEndings(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String content = Files.readString(path);
            String convertedContent = content.replace("\r\n", "\n");
            if (!content.equals(convertedContent)) {
                Files.writeString(path, convertedContent);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Test
    public void copyFileTest() throws Exception {
        String srcPath = TestResourceUtil.getFile("PageDTO.xml").getAbsolutePath().toString();
        for(int i = 2; i < 200; i++) {
            String destFilePath = Files.createTempFile("copyFileTest", ".xml").toAbsolutePath().toString();
            try {
                copyFile(srcPath, destFilePath);
            } finally {
                File file = new File(destFilePath);
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }

    public static void copyFile(String srcPath, String destPath) throws IOException {
        Path source = Paths.get(srcPath);
        Path target = Paths.get(destPath);

        // 确保目标目录存在
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

}
