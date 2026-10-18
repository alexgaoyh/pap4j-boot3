package cn.net.pap.common.mupdf.loader;

import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * MuPDF 原生动态链接库加载器：
 * <ul>
 *   <li>1. 优先尝试从系统原生库路径加载 (PATH / LD_LIBRARY_PATH)</li>
 *   <li>2. 自动检测操作系统与 CPU 架构，扫描 classpath 下的 <code>/native/{platform}/</code> 资源并解压加载</li>
 *   <li>3. 扫描外部备用目录 (环境变量 <code>PAP_MUPDF_DIR</code> 或 <code>~/.pap-mupdf/bin</code>)</li>
 *   <li>4. Windows 环境下通过 <code>Kernel32.SetDllDirectoryW</code> 维持进程环境闭环隔离</li>
 * </ul>
 */
public class MuPdfNativeLoader {

    private static final Logger log = LoggerFactory.getLogger(MuPdfNativeLoader.class);

    private static final String ENV_MUPDF_DIR_KEY = "PAP_MUPDF_DIR";
    private static final String SYS_PROP_MUPDF_DIR_KEY = "pap.mupdf.dir";

    private static volatile boolean loaded = false;

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 检测当前操作系统与 CPU 架构标识。
     *
     * @return 例如 win-amd64, linux-x86_64, linux-arm64, darwin-arm64 等
     */
    public static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean isArm = arch.contains("arm") || arch.contains("aarch64");

        if (os.contains("win")) {
            return isArm ? "win-arm64" : "win-amd64";
        } else if (os.contains("linux")) {
            return isArm ? "linux-arm64" : "linux-x86_64";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return isArm ? "darwin-arm64" : "darwin-x86_64";
        }
        return (os.replaceAll("[^a-zA-Z0-9]", "")) + "-" + arch;
    }

    /**
     * 获取当前平台原生动态库的默认文件名。
     */
    public static String getNativeLibraryFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "mupdf_java.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "libmupdf_java.dylib";
        } else {
            return "libmupdf_java.so";
        }
    }

    /**
     * 确保 mupdf_java 动态库已被加载到当前 JVM 进程。
     */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (MuPdfNativeLoader.class) {
            if (loaded) {
                return;
            }

            String platform = detectPlatform();
            String libFileName = getNativeLibraryFileName();
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            String[] candidateNames = {"mupdf_java", "mupdf_java64", "libmupdf_java"};
            if (isWindows) {
                candidateNames = new String[]{"mupdf_java", "mupdf_java64"};
            }

            UnsatisfiedLinkError lastError = null;

            // 1. 首轮：尝试直接从系统标准库路径加载 (PATH / LD_LIBRARY_PATH / java.library.path)
            for (String name : candidateNames) {
                try {
                    System.loadLibrary(name);
                    loaded = true;
                    log.info("[MuPDF-Loader] 成功从系统库路径加载动态库: {}", name);
                    return;
                } catch (UnsatisfiedLinkError e) {
                    lastError = e;
                }
            }

            // 2. 次轮：尝试从 Classpath 的 /native/{platform}/ 中寻找预置二进制库并解压
            List<String> resourcePaths = new ArrayList<>();
            resourcePaths.add("/native/" + platform + "/" + libFileName);
            // 兼容别名路径 (如 windows-x86_64)
            if ("win-amd64".equals(platform)) {
                resourcePaths.add("/native/windows-x86_64/" + libFileName);
            } else if ("linux-x86_64".equals(platform)) {
                resourcePaths.add("/native/linux-amd64/" + libFileName);
            }

            for (String resPath : resourcePaths) {
                try (InputStream in = MuPdfNativeLoader.class.getResourceAsStream(resPath)) {
                    if (in != null) {
                        if (isWindows) {
                            String[] companions = {"libwinpthread-1.dll", "libgcc_s_seh-1.dll"};
                            for (String comp : companions) {
                                try (InputStream compIn = MuPdfNativeLoader.class.getResourceAsStream("/native/" + platform + "/" + comp)) {
                                    if (compIn != null) {
                                        extractNativeResource(compIn, platform, comp);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }

                        File extractedFile = extractNativeResource(in, platform, libFileName);
                        if (extractedFile != null && extractedFile.exists()) {
                            loadWithDllDirectory(extractedFile, isWindows);
                            loaded = true;
                            log.info("[MuPDF-Loader] 成功从 Classpath 资源解压并加载动态库: {} -> {}", resPath, extractedFile.getAbsolutePath());
                            return;
                        }
                    }
                } catch (Throwable t) {
                    log.warn("[MuPDF-Loader] 尝试解压 Classpath 动态库失败: {}", resPath, t);
                    if (t instanceof UnsatisfiedLinkError) {
                        lastError = (UnsatisfiedLinkError) t;
                    }
                }
            }

            // 3. 第三轮：扫描外部备用目录 (环境变量 PAP_MUPDF_DIR 或 ~/.pap-mupdf/bin)
            String targetDir = System.getProperty(SYS_PROP_MUPDF_DIR_KEY);
            if (targetDir == null || targetDir.trim().isEmpty()) {
                targetDir = System.getenv(ENV_MUPDF_DIR_KEY);
            }
            if (targetDir == null || targetDir.trim().isEmpty()) {
                String userHome = System.getProperty("user.home");
                if (userHome != null && !userHome.isEmpty()) {
                    targetDir = userHome + File.separator + ".pap-mupdf" + File.separator + "bin";
                }
            }

            if (targetDir != null) {
                File dir = new File(targetDir);
                if (dir.exists() && dir.isDirectory()) {
                    File[] matchedFiles = dir.listFiles((d, name) -> {
                        String lower = name.toLowerCase();
                        return lower.contains("mupdf_java") && (lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib"));
                    });

                    if (matchedFiles != null && matchedFiles.length > 0) {
                        for (File libFile : matchedFiles) {
                            try {
                                loadWithDllDirectory(libFile, isWindows);
                                loaded = true;
                                log.info("[MuPDF-Loader] 成功从备用文件绝对路径加载动态库: {}", libFile.getAbsolutePath());
                                return;
                            } catch (UnsatisfiedLinkError e) {
                                log.warn("[MuPDF-Loader] 尝试加载备用文件失败: {}", libFile.getAbsolutePath(), e);
                                lastError = e;
                            }
                        }
                    }
                }
            }

            throw new UnsatisfiedLinkError("无法加载 mupdf_java 本地动态链接库。当前检测平台: " + platform
                    + "。请在 resources/native/" + platform + "/ 放置 " + libFileName
                    + "，或配置系统 PATH、或放置于 ~/.pap-mupdf/bin 目录下。详情: "
                    + (lastError != null ? lastError.getMessage() : "未知错误"));
        }
    }

    private static File extractNativeResource(InputStream in, String platform, String fileName) {
        try {
            // 优先解压到用户目录 ~/.pap-mupdf/bin/{platform}/，避免多进程重复解压产生大量垃圾文件
            String userHome = System.getProperty("user.home");
            File baseDir;
            if (userHome != null && !userHome.isEmpty()) {
                baseDir = new File(userHome, ".pap-mupdf" + File.separator + "bin" + File.separator + platform);
            } else {
                baseDir = new File(System.getProperty("java.io.tmpdir"), "pap-mupdf-native" + File.separator + platform);
            }

            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }

            File targetFile = new File(baseDir, fileName);
            // 若文件已存在且大小一致，则复用现有文件
            if (!targetFile.exists() || targetFile.length() <= 0) {
                File tempFile = new File(baseDir, fileName + ".tmp." + System.currentTimeMillis());
                try (OutputStream out = Files.newOutputStream(tempFile.toPath())) {
                    in.transferTo(out);
                }
                // 原子重命名
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                tempFile.renameTo(targetFile);
            }

            return targetFile;
        } catch (Throwable t) {
            log.error("[MuPDF-Loader] 解压 native 动态库至本地失败", t);
            return null;
        }
    }

    private static void loadWithDllDirectory(File libFile, boolean isWindows) {
        String parentDir = libFile.getParentFile().getAbsolutePath();
        if (isWindows) {
            try {
                WinKernel32.INSTANCE.SetDllDirectoryW(parentDir);
                File companionPthread = new File(parentDir, "libwinpthread-1.dll");
                if (companionPthread.exists()) {
                    try {
                        System.load(companionPthread.getAbsolutePath());
                    } catch (Throwable ignored) {}
                }
                File companionGcc = new File(parentDir, "libgcc_s_seh-1.dll");
                if (companionGcc.exists()) {
                    try {
                        System.load(companionGcc.getAbsolutePath());
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                log.warn("[MuPDF-Loader] Windows SetDllDirectoryW 临时注入失败: ", t);
            }
        }
        try {
            System.load(libFile.getAbsolutePath());
        } finally {
            if (isWindows) {
                try {
                    WinKernel32.INSTANCE.SetDllDirectoryW(null);
                } catch (Throwable ignored) {}
            }
        }
    }

    private interface WinKernel32 extends com.sun.jna.win32.StdCallLibrary {
        WinKernel32 INSTANCE = Native.load("kernel32", WinKernel32.class);

        boolean SetDllDirectoryW(String lpPathName);
    }
}
