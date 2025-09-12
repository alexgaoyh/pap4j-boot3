package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

}
