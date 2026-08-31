package cn.net.pap.example.proguard.autoclose;

import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * 可自动关闭的 FTP 客户端。
 * 继承 {@link FTPClient} 并实现 {@link AutoCloseable}，配合 try-with-resources 统一收尾：
 * <pre>{@code try (AutoCloseableFTPClient ftp = new AutoCloseableFTPClient()) { ... }}</pre>
 *
 * <h2>RST 关闭开关（默认关闭，纯 opt-in）</h2>
 * <p>{@code new AutoCloseableFTPClient(true)} 开启 RST 关闭（close 时对 socket 设置 SO_LINGER=true, 0）：
 * 控制连接与「服务端→客户端」的数据连接（RETR/LIST/NLST/MLSD/SITE_IMGSEND 下载/列目录）关闭时发 RST 而非 FIN，
 * 主动关闭方不再进入 TIME_WAIT、临时端口即用即放。</p>
 * <p><b>注意：上传（STOR/STOU/APPE）的数据连接不会被 RST</b>——客户端必须用 FIN/EOF 告知服务端传输完成，
 * RST 会导致服务端 426「Connection closed; aborted transfer」中断上传。</p>
 *
 * <h2>实测结论：TIME_WAIT 从来不是高频 FTP 下载的瓶颈（红鲱鱼）</h2>
 * <ul>
 *   <li>铁证一：FileZilla Server 0.9.60 上开启 RST、TIME_WAIT 归零后，Read timed out 依旧在 ~2k 次下载处出现
 *       ——TIME_WAIT 并非失败原因。</li>
 *   <li>铁证二：升级到 FileZilla Server 1.x（RST 关闭）后，TIME_WAIT 依旧大量堆积
 *       （结束快照 1.3w~1.7w 条，绝大多数落在服务端），但 60000 次循环 0 失败——TIME_WAIT 大量存在 ≠ 失败。</li>
 *   <li>真正的高频下载墙是 FileZilla Server 的「连接停滞」竞态（控制通道在快速数据连接下不响应），
 *       已由 FileZilla Server 1.9.0 修复（changelog：Fixed a race condition resulting in stalled connections）。
 *       升级到 1.x 后，复用单条控制连接可稳定跑满 60000 次无超时。</li>
 * </ul>
 *
 * <p><b>因此本开关不是高频下载的解法</b>，仅保留两类用途：
 * <ol>
 *   <li><b>诊断开关</b>：开启 RST 做对照，可区分「客户端端口耗尽（SocketException: Cannot assign requested address）」
 *       与「服务端停滞（Read timed out）」两类失败。</li>
 *   <li><b>保险</b>：TIME_WAIT 由「先发 FIN 的一方」承担（RFC 793）。FileZilla 通常先关连接 → TIME_WAIT 落在服务端、
 *       客户端端口即用即放；但客户端一旦抢到先发 FIN（实测在 0.9.60 调大线程数时发生过），自身也会积累 TIME_WAIT，
 *       高速建连下可能把客户端动态端口池打满。此时开启本开关，客户端 close 发 RST 而非 FIN，不进 TIME_WAIT、
 *       不锁端口，可避免端口耗尽。</li>
 * </ol></p>
 *
 * <p><b>高频下载建议形态</b>：FileZilla Server 升级到 1.9+ + 复用单条控制连接（默认）+ 超时三件套；
 * 本开关保持默认关闭即可。</p>
 *
 * @see cn.net.pap.example.proguard.FtpTimeWaitLoopDownloadTest
 */
public class AutoCloseableFTPClient extends FTPClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AutoCloseableFTPClient.class);

    private final boolean closeWithRst;

    public AutoCloseableFTPClient() {
        this(false);
    }

    /**
     * @param closeWithRst true 时控制连接与下载（服务端→客户端）数据连接 close() 发 RST，双方不产生 TIME_WAIT；
     *                     上传（客户端→服务端）数据连接保持 FIN。false 为默认全部 FIN 关闭。
     */
    public AutoCloseableFTPClient(boolean closeWithRst) {
        super();
        this.closeWithRst = closeWithRst;
    }

    /**
     * @return 是否开启 close 时发 RST（SO_LINGER=0）关闭
     */
    public boolean isCloseWithRst() {
        return closeWithRst;
    }

    public InputStream retrieveImgSendFileStream(String remote) throws IOException {
        return this._retrieveFileStream("SITE_IMGSEND", remote);
    }

    /**
     * 实现AutoCloseable接口，自动关闭连接 在try-with-resources结束时自动调用
     */
    @Override
    public void close() {
        try {
            if (super.isConnected() && super.isAvailable()) {
                logout();
            }
        } catch (IOException e) {
            // 登出异常不阻断关闭，但需记录 e 便于排查（AI.md：有 catch 必须有 e 信息）
            log.warn("[AutoCloseableFTPClient-Close] logout 异常(忽略继续断开): ", e);
        } finally {
            try {
                if (closeWithRst && _socket_ != null && _socket_.isConnected()) {
                    // close() 时发 RST 而非 FIN：服务端 :21 控制 socket 不会进入 TIME_WAIT
                    _socket_.setSoLinger(true, 0);
                }
                disconnect();
            } catch (IOException e) {
                log.warn("[AutoCloseableFTPClient-Close] disconnect 异常(关闭已尽力): ", e);
            }
        }
    }

    /**
     * 仅对「服务端是数据发送方」的命令（下载/列目录）在数据 socket 上开启 SO_LINGER=0：
     * 服务端先发 FIN、客户端读到 EOF 后 close() 发 RST，服务端 PASV 临时端口不会进 TIME_WAIT，
     * 而这正是共享动态端口池的主要消耗者。上传（STOR/STOU/APPE）数据连接保持 FIN，
     * 否则 RST 会让服务端 426 中断传输。
     */
    @Override
    protected Socket _openDataConnection_(String command, String arg) throws IOException {
        Socket s = super._openDataConnection_(command, arg);
        if (s != null && closeWithRst && isServerToClientTransfer(command)) {
            s.setSoLinger(true, 0);
        }
        return s;
    }

    /**
     * @param command FTP 命令名（如 "RETR"、"STOR"）
     * @return 该命令是否为服务端→客户端的数据传输（下载/列目录，可安全 RST）
     */
    private static boolean isServerToClientTransfer(String command) {
        if (command == null) {
            return false;
        }
        String upper = command.toUpperCase();
        return "RETR".equals(upper)
                || "LIST".equals(upper)
                || "NLST".equals(upper)
                || "MLSD".equals(upper)
                || "SITE_IMGSEND".equals(upper);
    }
}
