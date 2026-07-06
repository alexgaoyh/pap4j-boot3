package cn.net.pap.common.datastructure.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <h1>文件操作工具类 (File Operations Utility)</h1>
 * <p>提供针对文件及文件夹生命周期的便捷静态方法。本工具类主要解决以下核心技术问题：</p>
 * <ul>
 *   <li><b>递归目录安全删除：</b> {@link #deleteDirectory(String)} 基于 NIO.2 {@link Files#walkFileTree} 采用后序遍历，快速且线程安全地回收带有子文件与深层嵌套的复杂目录。</li>
 *   <li><b>高并发原子写入（防画面撕裂）：</b> {@link #writeAtomically(InputStream, Path)} 采用“同分区临时文件写入 + {@link StandardCopyOption#ATOMIC_MOVE} 原子移动重命名”策略，彻底解决高并发读写下的锁冲突，并避免写入中断产生不完整或损坏的文件。</li>
 *   <li><b>跨平台文件锁绕过与版本控制：</b> 在 Windows 等操作系统中，被占用或被映射（如 libvips 图像渲染挂载）的文件无法直接被覆盖写入。
 *       通过 {@link #generateVersionedPath(Path, String, String)} 动态生成追加版本后缀（如时间戳）的新物理文件，从而绕过文件锁定。</li>
 *   <li><b>无损历史版本回收：</b> {@link #cleanObsoleteVersions(Path, String, long)} 自动扫描指定逻辑文件的历史版本，通过获取文件的修改时间（mtime）进行排序，对超过优雅下线宽限期的老旧版本进行物理清除。</li>
 *   <li><b>防前缀包含误删：</b> 算法内部扫描匹配时，通过从右向左寻找最后一个下划线分割符进行精确名称比对，确保如 <code>logo_footer_2026.png</code> 绝不会被当成 <code>logo_2026.png</code> 的历史版本而被误删，保障平铺目录下的数据安全。</li>
 * </ul>
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

    /**
     * 将流数据以原子方式安全地写入到目标路径（通过临时文件+原子移动实现）。
     * 这可以防范高并发下的读写冲突，并防止生成部分损坏的文件。
     *
     * @param in         输入流
     * @param targetPath 目标物理文件路径
     * @return 最终写入的目标路径
     * @throws IOException 如果写入或原子重命名失败
     */
    public static Path writeAtomically(InputStream in, Path targetPath) throws IOException {
        if (in == null || targetPath == null) {
            throw new IllegalArgumentException("输入流和目标路径不能为空");
        }

        Path parentDir = targetPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // 在相同目录下生成一个临时的唯一文件，以确保 Files.move (ATOMIC_MOVE) 发生在同一个文件系统分区上
        String fileName = targetPath.getFileName().toString();
        Path tempPath = parentDir == null ?
                Path.of(fileName + "." + UUID.randomUUID().toString() + ".tmp") :
                parentDir.resolve(fileName + "." + UUID.randomUUID().toString() + ".tmp");

        try {
            // 1. 将数据安全写入临时文件
            Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);

            // 2. 原子化地移动/重命名临时文件覆盖至目标位置
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return targetPath;
        } catch (IOException e) {
            // 若异常发生，尝试删除临时文件防止产生孤立的垃圾文件
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    /**
     * 根据版本号生成版本化的文件路径，并进行安全校验防止目录穿越。
     *
     * @param parentDir  父目录路径
     * @param fileName   原始文件名（如 "input.png"）
     * @param version    版本标识符（通常为毫秒时间戳或递增序列，支持任意格式的字符串）
     * @return 版本化后的安全文件路径
     */
    public static Path generateVersionedPath(Path parentDir, String fileName, String version) {
        if (parentDir == null || fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("父目录和文件名不能为空");
        }
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("版本号不能为空");
        }

        // 防止目录穿越防御
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")
                || version.contains("..") || version.contains("/") || version.contains("\\")) {
            throw new IllegalArgumentException("非法的文件名或版本号，禁止包含路径分隔符或目录穿越字符");
        }

        // 解析文件名与扩展名，例如 "input.png" -> 基础名为 "input"，扩展名为 ".png"
        String baseName = fileName;
        String extension = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = fileName.substring(0, dotIdx);
            extension = fileName.substring(dotIdx);
        }

        // 拼接成 "baseName_Version.extension" 格式
        String versionedFileName = baseName + "_" + version + extension;
        Path resolvedPath = parentDir.resolve(versionedFileName).normalize();

        // 强校验：解析后的路径必须在 parentDir 目录下，防止潜在的安全穿越漏洞
        if (!resolvedPath.startsWith(parentDir.normalize())) {
            throw new IllegalArgumentException("生成的路径越界，不在指定的父目录下");
        }

        return resolvedPath;
    }

    /**
     * 自动扫描并回收同一个逻辑文件名的历史老版本文件。
     * 无条件保留最新版本，只对满足优雅下线宽限时间（Grace Period）的历史版本进行物理删除。
     *
     * @param parentDir        文件存储根目录
     * @param originalFileName 原始逻辑文件名（如 "input.png"）
     * @param keepGraceMillis  旧版本优雅下线宽限期（毫秒），例如 24 小时
     * @return 成功清理的历史版本文件路径列表
     * @throws IOException 如果扫描或删除文件失败
     */
    public static List<Path> cleanObsoleteVersions(Path parentDir, String originalFileName, long keepGraceMillis) throws IOException {
        if (parentDir == null || originalFileName == null || originalFileName.isEmpty()) {
            throw new IllegalArgumentException("参数不能为空");
        }
        if (!Files.exists(parentDir) || !Files.isDirectory(parentDir)) {
            return Collections.emptyList();
        }

        String baseName = originalFileName;
        String extension = "";
        int dotIdx = originalFileName.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = originalFileName.substring(0, dotIdx);
            extension = originalFileName.substring(dotIdx);
        }

        List<VersionedFile> files = scanVersionedFiles(parentDir, baseName, extension);
        if (files.size() <= 1) {
            return Collections.emptyList();
        }

        // 依据修改时间由大到小（最新到最老）进行排序
        files.sort((o1, o2) -> Long.compare(o2.lastModified, o1.lastModified));

        List<Path> deletedPaths = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (int i = 1; i < files.size(); i++) {
            VersionedFile oldFile = files.get(i);
            if (now - oldFile.lastModified > keepGraceMillis && Files.deleteIfExists(oldFile.path)) {
                deletedPaths.add(oldFile.path);
            }
        }
        return deletedPaths;
    }

    private static List<VersionedFile> scanVersionedFiles(Path parentDir, String baseName, String extension) throws IOException {
        List<VersionedFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(parentDir)) {
            List<Path> matchingPaths = stream.filter(path -> {
                if (Files.isDirectory(path)) {
                    return false;
                }
                String name = path.getFileName().toString();
                // 必须以 extension 结尾，并且长度大于 baseName.length() + 1 + extension.length() ("_" 占 1 字符)
                if (!name.endsWith(extension) || name.length() <= baseName.length() + 1 + extension.length()) {
                    return false;
                }
                // 去除尾部的 extension
                String withoutExtension = name.substring(0, name.length() - extension.length());
                // 从右向左寻找最后一个 "_"
                int lastUnderscore = withoutExtension.lastIndexOf('_');
                if (lastUnderscore < 0) {
                    return false;
                }
                String parsedBaseName = withoutExtension.substring(0, lastUnderscore);
                return parsedBaseName.equals(baseName);
            }).collect(Collectors.toList());

            for (Path path : matchingPaths) {
                try {
                    long lastModified = Files.getLastModifiedTime(path).toMillis();
                    files.add(new VersionedFile(path, lastModified));
                } catch (IOException ignored) {
                    // 忽略在此期间可能已被删除或无法访问的文件
                }
            }
        }
        return files;
    }

    private static class VersionedFile {
        final Path path;
        final long lastModified;

        VersionedFile(Path path, long lastModified) {
            this.path = path;
            this.lastModified = lastModified;
        }
    }

}
