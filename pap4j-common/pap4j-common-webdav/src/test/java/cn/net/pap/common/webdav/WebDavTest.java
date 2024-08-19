package cn.net.pap.common.webdav;

import org.apache.jackrabbit.webdav.MultiStatusResponse;

import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;

import org.junit.jupiter.api.Test;

public class WebDavTest {

    /**
     * server download in https://github.com/hacdias/webdav/releases
     * @throws Exception
     */
    // @Test
    public void uploadAndDownloadTest() throws Exception {
        String url = "http://127.0.0.1:6065/test.txt";
        String userName = "basic";
        String password = "basic";
        WebDavUtil webDavUtil = new WebDavUtil(url, userName, password);
        webDavUtil.upload("http://127.0.0.1:6065/test2.txt", new FileInputStream(new File("C:\\Users\\86181\\Desktop\\test2.txt")));

        MultiStatusResponse[] propfind = webDavUtil.propfind(url);
        for (int i = 0; i < propfind.length; i++) {
            String href = propfind[i].getHref();
            String path = URLDecoder.decode(href, "UTF-8");
            webDavUtil.download("http://127.0.0.1:6065/" + href, path, "C:\\Users\\86181\\Desktop\\");
        }
    }

}
