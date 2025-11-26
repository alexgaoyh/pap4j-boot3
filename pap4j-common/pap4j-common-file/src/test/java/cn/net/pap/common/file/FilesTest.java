package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FilesTest {

    // @Test
    public void walkFileTreeTest() throws IOException {
        String folderPath = "D:\\knowledge";
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
                //System.out.println("进入目录: " + dir.toAbsolutePath());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                //System.err.println("访问文件失败: " + file.toAbsolutePath() + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        Collections.sort(fileNames);

        Files.write( Paths.get("C:\\Users\\86181\\Desktop\\file_names_out.txt"), fileNames, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

    // @Test
    public void readTest1() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "1.txt"));
        for(int idx = 1; idx < lines.size(); idx++) {
            String before = lines.get(idx - 1);
            String current = lines.get(idx);
            String[] beforeSplit = before.split(":");
            String[] currentSplit = current.split(":");
            System.out.println(Long.parseLong(currentSplit[2].trim()) - Long.parseLong(beforeSplit[2].trim()));
        }
    }

    // @Test
    public void sortFileLinesWithComparatorTest() throws IOException {
        sortFileLinesWithComparator("C:\\Users\\86181\\Desktop\\sort.txt",
                "C:\\Users\\86181\\Desktop\\sort_out.txt",
                Comparator.naturalOrder());
    }

    // @Test
    public void convertLineEndingsTest() throws IOException {
        Path startDir = Paths.get("C:\\Users\\86181\\Desktop");

        try (Stream<Path> paths = Files.walk(startDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        convertLineEndings(path.toAbsolutePath().toString());
                        System.out.println("已处理: " + path);
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

}
