package cn.net.pap.common.datastructure.file;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * <h1>文件操作工具类 (File Operations Utility)</h1>
 * <p>提供针对文件及文件夹生命周期的便捷静态方法。例如基于 NIO.2 快速、递归安全地删除带有子文件的复杂目录。</p>
 *
 * @author alexgaoyh
 */
public class FileOperUtils {

    /**
     * <p>递归删除指定的文件夹及其包含的所有内部文件与子文件夹。</p>
     * <p>如果传入的路径不存在，方法将安全地直接返回而不会引发异常。利用了 {@link Files#walkFileTree} 进行底层的后序遍历删除。</p>
     *
     * @param directoryPath 要删除的文件夹绝对或相对路径字符串
     * @throws IOException 如果在此递归删除过程中发生底层 IO 权限或占用等异常
     */
    public static void deleteDirectory(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);

        if (!Files.exists(path)) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            // 先删除文件
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            // 再删除文件夹（此时文件夹已为空）
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
