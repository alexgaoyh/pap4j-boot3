package cn.net.pap.example.admin.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 用于对应用运行环境中的 ClassPath 资源（JAR、内嵌依赖 JAR、class 文件）进行完整性校验，防止代码被篡改。
 */
public class IntegrityVerifierUtil {

    /**
     * 统一入口
     *
     * @throws Exception
     */
    public void verify() throws Exception {
        String classPath = System.getProperty("java.class.path");
        String separator = System.getProperty("path.separator");

        Set<String> verified = new HashSet<>();

        for (String entry : classPath.split(separator)) {
            File file = new File(entry);
            if (!file.exists()) {
                continue;
            }

            if (file.isFile() && file.getName().endsWith(".jar")) {
                verifyJar(file, verified);
            } else if (file.isDirectory()) {
                verifyDirectory(file, verified);
            }
        }
    }

    /**
     * 校验 jar（普通 jar + fat jar）
     *
     * @param jarFile
     * @param verified
     * @throws Exception
     */
    private void verifyJar(File jarFile, Set<String> verified) throws Exception {
        String canonicalPath = jarFile.getCanonicalPath();
        if (!verified.add(canonicalPath)) {
            return;
        }

        // 校验 jar 本身
        try (InputStream is = new FileInputStream(jarFile)) {
            String hash = DigestUtils.calculateMD5(is);
            System.out.println("[JAR] " + canonicalPath + " -> " + hash);
        }

        // 尝试解析 fat jar（Spring Boot / 自定义）
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (isEmbeddedJar(entry)) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        String hash = DigestUtils.calculateMD5(is);
                        System.out.println("[EMBEDDED-JAR] " + entry.getName() + " -> " + hash);
                    }
                }
            }
        }
    }

    /**
     * 校验 exploded 目录（IDE / unpacked）
     *
     * @param dir
     * @param verified
     * @throws Exception
     */
    private void verifyDirectory(File dir, Set<String> verified) throws Exception {
        Path root = dir.toPath().toRealPath();
        if (!verified.add(root.toString())) {
            return;
        }

        Files.walk(root).filter(p -> p.toString().endsWith(".class")).forEach(p -> {
            try (InputStream is = Files.newInputStream(p)) {
                String hash = DigestUtils.calculateMD5(is);
                System.out.println("[CLASS] " + p + " -> " + hash);
            } catch (Exception e) {
                throw new RuntimeException("校验失败: " + p, e);
            }
        });
    }

    /**
     * embedded jar 识别（兼容 Spring Boot / 自定义 fat jar）
     *
     * @param entry
     * @return
     */
    private boolean isEmbeddedJar(JarEntry entry) {
        if (entry.isDirectory()) {
            return false;
        }
        if (!entry.getName().endsWith(".jar")) {
            return false;
        }
        return entry.getName().startsWith("BOOT-INF/lib/") || entry.getName().startsWith("lib/");
    }

}
