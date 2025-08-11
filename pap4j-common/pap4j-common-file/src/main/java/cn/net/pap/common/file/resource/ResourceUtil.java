package cn.net.pap.common.file.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

/**
 * 资源操作工具类
 */
public class ResourceUtil {

    /**
     * 复制 resources/native/{osType} 下的所有文件到目标目录，保持路径结构
     */
    public static void copyNativeResources(String targetDir) throws IOException {
        String osFolder = detectOSFolder();
        String resourceBase = "native/" + osFolder;

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL resourceUrl = cl.getResource(resourceBase);
        if (resourceUrl == null) {
            throw new IOException("未找到资源目录: " + resourceBase);
        }

        Path outputPath = Paths.get(targetDir);
        Files.createDirectories(outputPath);

        if ("file".equals(resourceUrl.getProtocol())) {
            // 本地运行（IDE 下）
            try {
                Path sourcePath = Paths.get(resourceUrl.toURI()); // 修复 /D:/ 问题
                Files.walk(sourcePath).forEach(src -> copyOne(src, sourcePath, outputPath));
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        } else if ("jar".equals(resourceUrl.getProtocol())) {
            // jar 内部
            String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));
            try (FileSystem fs = FileSystems.newFileSystem(Paths.get(jarPath), (ClassLoader) null)) {
                Path sourcePath = fs.getPath("/" + resourceBase);
                Files.walk(sourcePath).forEach(src -> copyOne(src, sourcePath, outputPath));
            }
        } else {
            throw new IOException("不支持的协议: " + resourceUrl.getProtocol());
        }
    }

    /**
     * 检测当前系统对应的资源子目录
     */
    private static String detectOSFolder() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "win";
        if (os.contains("linux")) return "linux";
        if (os.contains("mac")) return "mac";
        throw new UnsupportedOperationException("不支持的操作系统: " + os);
    }

    /**
     * 单文件复制，保持相对路径
     */
    private static void copyOne(Path src, Path base, Path outputBase) {
        try {
            Path relative = base.relativize(src);
            Path dest = outputBase.resolve(relative.toString());
            if (Files.isDirectory(src)) {
                Files.createDirectories(dest);
            } else {
                try (InputStream in = Files.newInputStream(src)) {
                    Files.createDirectories(dest.getParent());
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);

                    // 如果是 Linux/Mac，给文件加执行权限
                    if (!isWindows() && Files.isRegularFile(dest)) {
                        Set<PosixFilePermission> perms = new HashSet<>();
                        perms.add(PosixFilePermission.OWNER_READ);
                        perms.add(PosixFilePermission.OWNER_WRITE);
                        perms.add(PosixFilePermission.OWNER_EXECUTE);
                        perms.add(PosixFilePermission.GROUP_READ);
                        perms.add(PosixFilePermission.GROUP_EXECUTE);
                        perms.add(PosixFilePermission.OTHERS_READ);
                        perms.add(PosixFilePermission.OTHERS_EXECUTE);
                        Files.setPosixFilePermissions(dest, perms);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

}
