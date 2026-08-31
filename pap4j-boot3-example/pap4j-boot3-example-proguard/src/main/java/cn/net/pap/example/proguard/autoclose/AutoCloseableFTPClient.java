package cn.net.pap.example.proguard.autoclose;

import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * 可自动关闭的FTP客户端
 * 继承FTPClient并实现AutoCloseable接口
 * 使用方式：try (AutoCloseableFTPClient ftp = new AutoCloseableFTPClient()) { ... }
 *
 * <p>可选开关：{@code new AutoCloseableFTPClient(true)} 开启 RST 关闭（SO_LINGER=true, 0）：
 * 控制连接与「服务端发送数据」的数据连接（RETR/LIST/NLST/MLSD 下载/列目录）close() 时发 RST 而非 FIN，
 * 发起方与接收方均不进入 TIME_WAIT，消除高频「新建连接 → 下载 → 关闭」时客户端的 TIME_WAIT 堆积。
 * <b>注意：上传（STOR）的数据连接不会被 RST</b>——客户端必须用 FIN/EOF 告知服务端传输完成，
 * RST 会导致服务端 426「Connection closed; aborted transfer」中断上传。
 * 默认关闭、与原有行为完全一致。
 * <b>实测边界（见 {@code FtpTimeWaitLoopDownloadTest}）</b>：RST 只能消除 TIME_WAIT，不能解除
 * FileZilla 服务端对单 IP 高频建连的吞吐墙——RST 开启后 TIME_WAIT 虽降至 ~0，仍会在 ~2k 次下载处
 * 出现 Read timed out（服务端 accept backlog 溢出），高并发下载应以「复用单条控制连接」为主形态。</p>
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
