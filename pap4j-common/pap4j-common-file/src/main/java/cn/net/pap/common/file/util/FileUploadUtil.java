package cn.net.pap.common.file.util;

import cn.net.pap.common.core.util.RetryUtil;
import cn.net.pap.common.file.dto.FileDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件上传工具：并返回可直接入库的 {@link FileDTO}。
 *
 * <p>生成的 filePath 形如 {@code fs1:8e/fc/8efc1f3c9a5b4d2e8f60a8b1c3d5e7f9}：</p>
 * <ul>
 *   <li>{@code fs1}：存储实例名，按它分派后端；</li>
 *   <li>{@code 8e/fc}：令牌前四位十六进制，作为两级分片目录；</li>
 *   <li>{@code 8efc1f3c...}：128 位密码学随机令牌（UUID v4 形状）的完整 32 位十六进制，
 *       分片目录就是它的前四位，因此整个 filePath 长度为 4 + 38 = 42 个字符。</li>
 * </ul>
 *
 * <p><b>流程：</b>生成令牌 → 目标路径或暂存文件已存在就重新生成令牌 → 数据写入同目录的 {@code .part}
 * 并刷盘 → 原子改名到最终路径 → fsync 父目录 → {@code size} 取自写入过程中累计的字节数。</p>
 *
 * <p><b>为什么不预先创建目标文件：</b>{@code .part} + 原子改名的意义就是“写完之前对外不可见”。
 * 若写数据前就在最终路径创建 0 字节文件，外部扫描会读到空文件；进程崩溃后还会留下一个与
 * “合法的空文件上传”无法区分的 0 字节残留。因此这里只做原子发布，也不加
 * {@link StandardCopyOption#REPLACE_EXISTING}——目标在发布前必然不存在，而
 * {@code ATOMIC_MOVE} 下其它选项本就由实现自行决定。</p>
 *
 * <p><b>注意：</b>filePath 不包含物理根目录，{@code fs1:ab/cd/<token>} 到真实文件的映射由存储侧维护</p>
 */
public final class FileUploadUtil {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(FileUploadUtil.class);

    /**
     * 密码学随机源，进程内共享（{@link SecureRandom} 自身线程安全）。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 本地落点根目录：系统临时目录下的固定子目录。
     */
    private static final Path LOCAL_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "pap-file");

    /**
     * 目录 fsync 失败是否已告警过，避免在不支持该操作的平台上每次上传都刷日志。
     */
    private static final AtomicBoolean DIR_FSYNC_WARNED = new AtomicBoolean();

    /**
     * 是否运行在 Windows：该平台不允许以 {@link FileChannel} 打开目录。
     */
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private FileUploadUtil() {
    }

    /**
     * 把输入流落到磁盘，并返回可直接入库的记录。
     *
     * <p>时间取当前时刻，{@code createdAt} 与 {@code updatedAt} 是同一个时间点，
     * {@code deletedAt} 为 {@code null}。</p>
     *
     * @param in         上传内容，由调用方负责关闭
     * @param instanceId 存储实例名
     * @param id         记录主键
     * @param bizId      业务键
     * @param origName   原始文件名（只记录，不参与路径计算）
     * @return 填充完成的记录，其中 {@code size} 取自实际写入的字节数
     * @throws IOException 写入、刷盘或改名失败
     */
    public static FileDTO upload(InputStream in, String instanceId, long id, String bizId, String origName) throws IOException {
        return switch (instanceId) {
            // 本地磁盘
            default -> uploadLocal(in, instanceId, id, bizId, origName);
        };
    }

    /**
     * 落到本地磁盘。
     *
     * @param in         上传内容
     * @param instanceId 存储实例名
     * @param id         记录主键
     * @param bizId      业务键
     * @param origName   原始文件名
     * @return 填充完成的记录
     * @throws IOException 写入、刷盘或改名失败
     */
    private static FileDTO uploadLocal(InputStream in, String instanceId, long id, String bizId, String origName) throws IOException {
        String rel;
        Path target;
        Path part;
        do {
            String token = newToken();
            rel = token.substring(0, 2) + '/' + token.substring(2, 4) + '/' + token;
            target = LOCAL_ROOT.resolve(rel);
            part = target.resolveSibling(target.getFileName() + ".part");
            // 目标与暂存文件都必须不存在：暂存文件可能是上一次崩溃残留的。
        } while (Files.exists(target) || Files.exists(part));

        Files.createDirectories(target.getParent());

        long size;
        try {
            size = writeAndSync(part, in);
            // 目标路径此时必然不存在，直接原子发布；不需要 REPLACE_EXISTING。
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            // 只清理自己写了一半的 .part。改名成功后不再回删 target：那时数据已完整落盘，
            // 删除只会把可用的数据毁掉。分片目录不清理：两级十六进制目录最多 65536 个，且会被复用。
            log.error("上传失败，清理暂存文件: {}", part, e);
            deleteQuietly(part);
            throw e;
        }
        // 文件内容与 inode 已落盘，但 rename 改的是父目录的目录项，也要单独刷一次，
        // 否则掉电后 filePath 可能指向一个不存在的路径。
        syncDirectory(target.getParent());
        Instant now = Instant.now();
        return new FileDTO(id, bizId, instanceId + ':' + rel, size, origName, now, now, null);
    }

    /**
     * 生成 128 位随机令牌，并置入 UUID v4 的版本位与变体位。
     *
     * @return 32 位小写十六进制令牌
     */
    private static String newToken() {
        byte[] token = new byte[16];
        RANDOM.nextBytes(token);
        token[6] = (byte) ((token[6] & 0x0F) | 0x40);
        token[8] = (byte) ((token[8] & 0x3F) | 0x80);
        return HexFormat.of().formatHex(token);
    }

    /**
     * 把数据写入暂存文件并刷盘。
     *
     * @param part 暂存文件路径
     * @param in   数据来源
     * @return 实际写入的字节数
     * @throws IOException 写入或刷盘失败
     */
    private static long writeAndSync(Path part, InputStream in) throws IOException {
        long size = 0L;
        try (FileChannel channel = FileChannel.open(part, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            int read = in.read(buffer);
            while (read != -1) {
                if (read > 0) {
                    size += read;
                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, read);
                    while (byteBuffer.hasRemaining()) {
                        channel.write(byteBuffer);
                    }
                }
                read = in.read(buffer);
            }
            // 刷数据块与元数据。这是刻意的可靠性取舍：以吞吐换“返回即落盘”。
            channel.force(true);
        }
        return size;
    }

    /**
     * 把父目录的目录项（{@code 文件名 → inode}）刷盘，保证 rename 本身也能在掉电后保留。
     *
     * <p>目录 fsync 不可移植：Windows 不支持以只读方式打开目录（会抛
     * {@link java.nio.file.AccessDeniedException}），部分文件系统也会拒绝。
     * 失败只影响“rename 的持久性”，不影响原子性与可见性，因此降级告警而不阻断上传。</p>
     *
     * @param dir 目录
     */
    private static void syncDirectory(Path dir) {
        if (WINDOWS) {
            // Windows 上必然失败（AccessDeniedException），直接跳过以免产生无意义的告警日志。
            log.debug("Windows 不支持目录 fsync，跳过 rename 持久化: {}", dir);
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | RuntimeException unsupported) {
            if (DIR_FSYNC_WARNED.compareAndSet(false, true)) {
                log.warn("目录 fsync 不可用或失败，rename 的持久性无法保证（后续同类情况静默跳过）: {}", dir, unsupported);
            } else {
                log.debug("目录 fsync 失败: {}", dir, unsupported);
            }
        }
    }

    /**
     * 基于存储定位符打开文件输入流（借助 {@link RetryUtil} 规避跨服务器负缓存时差，由调用方负责关闭）。
     *
     * @param filePath 存储定位符，形如 {@code fs1:8e/fc/8efc1f3c...}
     * @param maxRetries  最大重试次数
     * @param delayMillis 每次重试前的延迟（毫秒）
     * @return 文件的物理输入流
     * @throws IOException 读取失败或重试耗尽后文件仍不存在
     */
    public static InputStream openStream(String filePath, int maxRetries, long delayMillis) throws IOException {
        int colon = filePath != null ? filePath.indexOf(':') : -1;
        if (colon <= 0 || colon == filePath.length() - 1) {
            throw new IllegalArgumentException("非法的 filePath 格式: " + filePath);
        }
        String instanceId = filePath.substring(0, colon);
        String rel = filePath.substring(colon + 1);
        if (rel.contains("..")) {
            throw new SecurityException("检测到非法相对路径穿越: " + rel);
        }
        Path target = switch (instanceId) {
            default -> LOCAL_ROOT.resolve(rel);
        };
        try {
            // 直接尝试打开输入流（触发底层 open 系统调用），杜绝 exists() 负缓存投毒
            return RetryUtil.retryT(maxRetries, delayMillis, () -> Files.newInputStream(target), null);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("读取文件失败: " + filePath, e);
        }
    }

    /**
     * 尽力删除文件，失败只记录日志。
     *
     * @param path 待删除路径
     */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanup) {
            log.error("清理残留文件失败: {}", path, cleanup);
        }
    }
}
