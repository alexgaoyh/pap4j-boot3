package cn.net.pap.common.mupdf.loader;

import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
 *
 * <p><b>安全性说明：</b>解压采用 "临时文件 + fsync + 原子替换 + 长度校验" 四步法，
 * 确保多进程/多实例并发启动、或上次解压被强杀时，绝不会把半截的 DLL 装载进进程
 * （装载损坏的原生库会直接导致 JVM 崩溃）。</p>
 */
public class MuPdfNativeLoader {

    private static final Logger log = LoggerFactory.getLogger(MuPdfNativeLoader.class);

    private static final String ENV_MUPDF_DIR_KEY = "PAP_MUPDF_DIR";
    private static final String SYS_PROP_MUPDF_DIR_KEY = "pap.mupdf.dir";

    private static volatile boolean loaded = false;

    private MuPdfNativeLoader() {
    }

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
     * 确保 mupdf_java 动态库已被加载到当前 JVM 进程（幂等、线程安全）。
     *
     * @return true 表示动态库可用
     * @throws UnsatisfiedLinkError 当所有加载途径均失败时抛出，便于启动期快速失败
     */
    public static boolean ensureLoaded() {
        if (loaded) {
            return true;
        }
        synchronized (MuPdfNativeLoader.class) {
            if (loaded) {
                return true;
            }

            String platform = detectPlatform();
            String libFileName = getNativeLibraryFileName();
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

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
                    return true;
                } catch (UnsatisfiedLinkError e) {
                    if (isAlreadyLoadedError(e)) {
                        loaded = true;
                        log.info("[MuPDF-Loader] 动态库已被其它 ClassLoader 加载，直接复用进程内实例: {}", name);
                        return true;
                    }
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
                try {
                    java.net.URL resource = MuPdfNativeLoader.class.getResource(resPath);
                    if (resource == null) {
                        continue;
                    }
                    if (isWindows) {
                        String[] companions = {"libwinpthread-1.dll", "libgcc_s_seh-1.dll"};
                        for (String comp : companions) {
                            java.net.URL compUrl = MuPdfNativeLoader.class.getResource("/native/" + platform + "/" + comp);
                            if (compUrl != null) {
                                try {
                                    extractNativeResource(compUrl, platform, comp);
                                } catch (Throwable ignored) {
                                    // 附属运行时库缺失不阻塞主库加载，System.load 时会给出明确错误
                                }
                            }
                        }
                    }

                    File extractedFile = extractNativeResource(resource, platform, libFileName);
                    if (extractedFile != null && extractedFile.isFile() && extractedFile.length() > 0) {
                        loadWithDllDirectory(extractedFile, isWindows);
                        loaded = true;
                        log.info("[MuPDF-Loader] 成功从 Classpath 资源解压并加载动态库: {} -> {}", resPath, extractedFile.getAbsolutePath());
                        return true;
                    }
                    log.warn("[MuPDF-Loader] Classpath 资源解压结果不可用，继续尝试备用路径: {}", resPath);
                } catch (UnsatisfiedLinkError e) {
                    if (isAlreadyLoadedError(e)) {
                        loaded = true;
                        log.info("[MuPDF-Loader] 动态库已被其它 ClassLoader 加载，直接复用进程内实例: {}", resPath);
                        return true;
                    }
                    lastError = e;
                    log.warn("[MuPDF-Loader] 尝试解压并加载 Classpath 动态库失败: {}", resPath, e);
                } catch (Throwable t) {
                    log.warn("[MuPDF-Loader] 尝试解压 Classpath 动态库失败: {}", resPath, t);
                }
            }

            // 3. 第三轮：扫描外部备用目录 (环境变量 PAP_MUPDF_DIR 或 ~/.pap-mupdf/bin)
            File dir = resolveExternalLibraryDir();
            if (dir != null) {
                File[] matchedFiles = dir.listFiles((d, name) -> {
                    String lower = name.toLowerCase();
                    return lower.contains("mupdf_java") && (lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib"));
                });

                if (matchedFiles != null) {
                    for (File libFile : matchedFiles) {
                        try {
                            loadWithDllDirectory(libFile, isWindows);
                            loaded = true;
                            log.info("[MuPDF-Loader] 成功从备用文件绝对路径加载动态库: {}", libFile.getAbsolutePath());
                            return true;
                        } catch (UnsatisfiedLinkError e) {
                            if (isAlreadyLoadedError(e)) {
                                loaded = true;
                                log.info("[MuPDF-Loader] 动态库已被其它 ClassLoader 加载，直接复用进程内实例: {}", libFile.getAbsolutePath());
                                return true;
                            }
                            lastError = e;
                            log.warn("[MuPDF-Loader] 尝试加载备用文件失败: {}", libFile.getAbsolutePath(), e);
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

    private static File resolveExternalLibraryDir() {
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
        if (targetDir == null) {
            return null;
        }
        File dir = new File(targetDir);
        return dir.isDirectory() ? dir : null;
    }

    /**
     * 把 Classpath 中的原生库落地到本地并返回落地产物。
     * <p>采用临时文件 + fsync + 原子替换，并在替换后校验长度，避免装载到残缺二进制文件。</p>
     *
     * @return 落地文件；失败返回 null（调用方继续尝试其它来源）
     */
    private static File extractNativeResource(java.net.URL resource, String platform, String fileName) {
        // 资源真实长度：已知时可直接复用已落地且长度一致的文件，避免每次启动都完整拷贝几十 MB 的二进制
        long expectedSize = resourceSize(resource);
        for (File baseDir : resolveExtractDirs(platform)) {
            if (expectedSize > 0) {
                File existing = new File(baseDir, fileName);
                if (existing.isFile() && existing.length() == expectedSize && hasValidNativeMagic(existing)) {
                    return existing;
                }
            }
            // 每次尝试都重新打开流，避免第一次拷贝失败后流已耗尽
            try (InputStream in = resource.openStream()) {
                File extracted = extractNativeResourceTo(in, baseDir, fileName, expectedSize);
                if (extracted != null) {
                    return extracted;
                }
            } catch (Throwable t) {
                log.warn("[MuPDF-Loader] 读取 Classpath 资源失败: {}", resource, t);
            }
        }
        log.error("[MuPDF-Loader] 解压 native 动态库至本地失败: {}", fileName);
        return null;
    }

    /**
     * 解析 Classpath 资源的真实长度（jar 内条目直接读取 Central Directory 中的大小）。
     *
     * @return 长度；无法确定时返回 -1
     */
    private static long resourceSize(java.net.URL resource) {
        if (resource == null) {
            return -1L;
        }
        try {
            String protocol = resource.getProtocol();
            if ("file".equals(protocol)) {
                return new File(resource.toURI()).length();
            }
            if ("jar".equals(protocol)) {
                java.net.URLConnection connection = resource.openConnection();
                if (connection instanceof java.net.JarURLConnection) {
                    java.util.jar.JarEntry entry = ((java.net.JarURLConnection) connection).getJarEntry();
                    if (entry != null && entry.getSize() > 0) {
                        return entry.getSize();
                    }
                }
            }
        } catch (Throwable ignored) {
            // 无法确定长度时退化为"完整拷贝后再校验"的安全路径
        }
        return -1L;
    }

    private static List<File> resolveExtractDirs(String platform) {
        List<File> dirs = new ArrayList<>(2);
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isEmpty()) {
            dirs.add(new File(userHome, ".pap-mupdf" + File.separator + "bin" + File.separator + platform));
        }
        dirs.add(new File(System.getProperty("java.io.tmpdir"), "pap-mupdf-native" + File.separator + platform));
        return dirs;
    }

    private static File extractNativeResourceTo(InputStream in, File baseDir, String fileName, long expectedSize) {
        File tempFile = null;
        try {
            if (!baseDir.isDirectory() && !baseDir.mkdirs() && !baseDir.isDirectory()) {
                return null;
            }
            File targetFile = new File(baseDir, fileName);

            tempFile = File.createTempFile(fileName + ".", ".tmp", baseDir);
            long written = copyToFile(in, tempFile);
            if (written <= 0) {
                deleteQuietly(tempFile);
                log.warn("[MuPDF-Loader] 解压得到的原生库长度为 0，放弃该落地目录: {}", baseDir.getAbsolutePath());
                return null;
            }
            if (!hasValidNativeMagic(tempFile)) {
                deleteQuietly(tempFile);
                log.warn("[MuPDF-Loader] 解压产物不是合法的原生库（魔数校验失败）: {}", tempFile.getAbsolutePath());
                return null;
            }

            // 已存在且长度一致：直接复用（多进程并发/重启场景下避免重复写入）
            if (targetFile.isFile() && targetFile.length() == written) {
                deleteQuietly(tempFile);
                return targetFile;
            }
            if (expectedSize > 0 && written != expectedSize) {
                log.warn("[MuPDF-Loader] 资源长度校验不一致: {} (实际 {} 字节, 期望 {} 字节)", fileName, written, expectedSize);
            }

            try {
                Files.move(tempFile.toPath(), targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile = null;

            // 落地后必须校验长度，否则说明写入被截断（磁盘满 / 进程被强杀）
            if (!targetFile.isFile() || targetFile.length() != written) {
                log.warn("[MuPDF-Loader] 原生库落地后长度校验失败: {} (期望 {} 字节)", targetFile.getAbsolutePath(), written);
                deleteQuietly(targetFile);
                return null;
            }
            return targetFile;
        } catch (Throwable t) {
            log.warn("[MuPDF-Loader] 解压 native 动态库至 {} 失败: {}", baseDir.getAbsolutePath(), t.getMessage());
            return null;
        } finally {
            if (tempFile != null) {
                deleteQuietly(tempFile);
            }
        }
    }

    /**
     * 原生库文件头魔数校验：防止把残缺 / 错乱的二进制（例如进程被强杀时留下的半截文件）装载进 JVM。
     * <p>把一个损坏的 DLL 映射进进程会直接导致 JVM 崩溃，因此这一步必须在 System.load 之前完成。</p>
     */
    private static boolean hasValidNativeMagic(File file) {
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            int read = 0;
            while (read < header.length) {
                int n = in.read(header, read, header.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            if (read < 2) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return header[0] == 'M' && header[1] == 'Z';
        }
        if (os.contains("mac") || os.contains("darwin")) {
            int magic = ((header[0] & 0xFF) << 24) | ((header[1] & 0xFF) << 16)
                        | ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
            return magic == 0xFEEDFACE || magic == 0xFEEDFACF || magic == 0xCEFAEDFE
                   || magic == 0xCFFAEDFE || magic == 0xCAFEBABE;
        }
        return header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
    }

    private static long copyToFile(InputStream in, File target) throws IOException {
        long written = 0L;
        try (FileChannel channel = FileChannel.open(target.toPath(),
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            OutputStream out = Channels.newOutputStream(channel);
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                written += read;
            }
            out.flush();
            // 强制刷盘：确保后续原子替换读到的内容一定完整
            channel.force(true);
        }
        return written;
    }

    private static void loadWithDllDirectory(File libFile, boolean isWindows) {
        String parentDir = libFile.getParentFile().getAbsolutePath();
        if (isWindows) {
            try {
                WinKernel32.INSTANCE.SetDllDirectoryW(parentDir);
                File companionPthread = new File(parentDir, "libwinpthread-1.dll");
                if (companionPthread.isFile()) {
                    loadQuietly(companionPthread);
                }
                File companionGcc = new File(parentDir, "libgcc_s_seh-1.dll");
                if (companionGcc.isFile()) {
                    loadQuietly(companionGcc);
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
                } catch (Throwable ignored) {
                    // 恢复默认搜索路径失败不影响主流程
                }
            }
        }
    }

    private static void loadQuietly(File lib) {
        try {
            System.load(lib.getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            if (!isAlreadyLoadedError(e)) {
                log.debug("[MuPDF-Loader] 预加载附属运行库失败（主库加载时会再次尝试）: {}", lib.getName(), e);
            }
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Throwable ignored) {
                file.deleteOnExit();
            }
        }
    }

    /**
     * 同一个 JVM 内动态库已经被其它 ClassLoader 装载时，System.load 会抛 "already loaded"，
     * 这种情况应当视为可用，而不是加载失败。
     */
    private static boolean isAlreadyLoadedError(UnsatisfiedLinkError e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return (lower.contains("already loaded") || lower.contains("already been loaded"))
               && (lower.contains("classloader") || lower.contains("another"));
    }

    private interface WinKernel32 extends com.sun.jna.win32.StdCallLibrary {
        WinKernel32 INSTANCE = Native.load("kernel32", WinKernel32.class);

        boolean SetDllDirectoryW(String lpPathName);
    }
}
