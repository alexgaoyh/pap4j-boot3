package cn.net.pap.example.proguard.autoclose;

import org.apache.commons.net.ftp.FTPClient;

import java.io.IOException;
import java.io.InputStream;

/**
 * 可自动关闭的FTP客户端
 * 继承FTPClient并实现AutoCloseable接口
 * 使用方式：try (AutoCloseableFTPClient ftp = new AutoCloseableFTPClient()) { ... }
 */
public class AutoCloseableFTPClient extends FTPClient implements AutoCloseable {

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
            // todo 忽略登出异常
        } finally {
            try {
                disconnect();
            } catch (IOException e) {
            }
        }
    }
}
