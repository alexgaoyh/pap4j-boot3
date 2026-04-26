package cn.net.pap.common.webdav;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.HttpMkcol;
import org.apache.jackrabbit.webdav.client.methods.HttpPropfind;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;

public class WebDavUtil {

    private static final Logger log = LoggerFactory.getLogger(WebDavUtil.class);

    private String username, password;
    private URI uri;
    private String root;
    private HttpClient client;
    private HttpClientContext context;

    public WebDavUtil(String baseUri, String userName, String passWord) {
        this.uri = URI.create(baseUri);
        this.root = this.uri.toASCIIString();
        if (!this.root.endsWith("/")) {
            this.root += "/";
        }
        this.username = userName;
        this.password = passWord;

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        HttpHost targetHost = new HttpHost(uri.getHost(), uri.getPort());

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        UsernamePasswordCredentials upc = new UsernamePasswordCredentials(this.username, this.password);
        credsProvider.setCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()), upc);

        AuthCache authCache = new BasicAuthCache();
        // Generate BASIC scheme object and add it to the local auth cache
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(targetHost, basicAuth);

        // Add AuthCache to the execution context
        this.context = HttpClientContext.create();
        this.context.setCredentialsProvider(credsProvider);
        this.context.setAuthCache(authCache);
        this.client = HttpClients.custom().setConnectionManager(cm).build();

    }

    public void delete(String uri) throws IOException {
        HttpDelete delete = new HttpDelete(uri);
        int status = this.client.execute(delete, this.context).getStatusLine().getStatusCode();
        System.out.println("Delete " + uri + " status is :" + status);
    }

    /**
     * 上传文件前，自动检测并创建不存在的父目录
     */
    public void upload(String uri, FileInputStream fis) throws IOException {
        // 1. 尝试截取文件的父目录并递归创建
        int lastSlashIndex = uri.lastIndexOf("/");
        // 避免截断 "http://" 并确保不在根目录下操作
        if (lastSlashIndex > 8) {
            String parentUri = uri.substring(0, lastSlashIndex);
            if (!parentUri.equals(this.root)) {
                mkdirs(parentUri);
            }
        }

        // 2. 执行原本的上传逻辑
        HttpPut put = new HttpPut(uri);
        InputStreamEntity requestEntity = new InputStreamEntity(fis);
        put.setEntity(requestEntity);
        HttpResponse execute = this.client.execute(put, this.context);
        StatusLine statusLine = execute.getStatusLine();
        int status = statusLine.getStatusCode();
        System.out.println("Upload " + uri + " status is :" + status);
    }

    /**
     *  递归创建多级目录 (类似 Java 的 File.mkdirs)
     */
    public void mkdirs(String uri) throws IOException {
        if (uri == null || uri.equals(this.root) || uri.length() <= this.root.length()) {
            return;
        }

        HttpMkcol mkcol = new HttpMkcol(uri);
        HttpResponse response = this.client.execute(mkcol, this.context);
        int status = response.getStatusLine().getStatusCode();

        // 201 Created 代表创建成功
        // 405 Method Not Allowed 代表该目录已经存在，无需操作
        if (status == 201 || status == 405) {
            return;
        }

        // 409 Conflict 代表父目录不存在，需要先递归创建父目录
        if (status == 409) {
            int lastSlashIndex = uri.lastIndexOf("/");
            if (lastSlashIndex > 8) {
                String parentUri = uri.substring(0, lastSlashIndex);
                // 递归往上层创建
                mkdirs(parentUri);

                // 父目录创建完毕后，重新尝试创建当前目录
                HttpMkcol retryMkcol = new HttpMkcol(uri);
                this.client.execute(retryMkcol, this.context);
            }
        }
    }

    public void mkdir(String uri) throws IOException {
        HttpMkcol mkcol = new HttpMkcol(uri);
        int status = this.client.execute(mkcol, this.context).getStatusLine().getStatusCode();
        System.out.println("Create folder " + uri + " status is :" + status);
    }

    public void download(String uri, String fileName, String downloadPath) throws IOException {
        HttpGet get = new HttpGet(uri);
        HttpResponse execRel = this.client.execute(get, this.context);
        StatusLine status = execRel.getStatusLine();
        HttpEntity resp = execRel.getEntity();
        transStream2File(resp.getContent(), downloadPath + fileName);
        System.out.println("Download " + uri + " status is :" + status);
    }

    /**
     * 【改造点】增加 HTTP 状态码校验，防止 404 解析 XML 报错及 NPE 空指针异常
     */
    public MultiStatusResponse[] propfind(String testuri) throws IOException {
        try {
            DavPropertyNameSet names = new DavPropertyNameSet();
            names.add(DeltaVConstants.COMMENT);
            // DavConstants.DEPTH_1
            HttpPropfind propfind = new HttpPropfind(testuri, DavConstants.PROPFIND_ALL_PROP_INCLUDE, names, DavConstants.DEPTH_1);
            HttpResponse resp = this.client.execute(propfind, this.context);
            int status = resp.getStatusLine().getStatusCode();
            System.out.println("List file " + testuri + " status is :" + status);

            // 关键拦截：只有状态码为 207 (Multi-Status) 时，服务器回传的才是正确的 XML 文件属性信息
            if (status == 207) {
                MultiStatus multistatus = propfind.getResponseBodyAsMultiStatus(resp);
                return multistatus.getResponses();
            } else {
                // 如果是 404 或者其他错误，不强行解析，直接返回空数组
                log.warn("Target URI status is {}, returning empty array to avoid NPE.", status);
                return new MultiStatusResponse[0];
            }
        } catch (DavException e) {
            log.error("propfind error", e);
            // 发生异常时也返回空数组，确保外部的 for 循环读取 propfind.length 时不会报 NullPointerException
            return new MultiStatusResponse[0];
        }
    }

    public void transStream2File(InputStream is, String fileName) throws IOException {
        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        in = new BufferedInputStream(is);
        out = new BufferedOutputStream(new FileOutputStream(fileName));
        int len = -1;
        byte[] b = new byte[1024];
        while ((len = in.read(b)) != -1) {
            out.write(b, 0, len);
        }
        in.close();
        out.close();
    }
}
