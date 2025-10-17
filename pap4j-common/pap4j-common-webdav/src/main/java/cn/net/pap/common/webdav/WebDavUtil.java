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

    public void upload(String uri, FileInputStream fis) throws IOException {
        HttpPut put = new HttpPut(uri);
        InputStreamEntity requestEntity = new InputStreamEntity(fis);
        put.setEntity(requestEntity);
        HttpResponse execute = this.client.execute(put, this.context);
        StatusLine statusLine = execute.getStatusLine();
        int status = statusLine.getStatusCode();
        System.out.println("Upload " + uri + " status is :" + status);
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

    public MultiStatusResponse[] propfind(String testuri) throws IOException {
        MultiStatusResponse[] responses = null;
        try {
            DavPropertyNameSet names = new DavPropertyNameSet();
            names.add(DeltaVConstants.COMMENT);
            // DavConstants.DEPTH_1
            HttpPropfind propfind = new HttpPropfind(testuri, DavConstants.PROPFIND_ALL_PROP_INCLUDE, names, DavConstants.DEPTH_1);
            HttpResponse resp = this.client.execute(propfind, this.context);
            int status = resp.getStatusLine().getStatusCode();
            System.out.println("List file " + uri + " status is :" + status);
            // assertEquals(207, status);
            MultiStatus multistatus;
            multistatus = propfind.getResponseBodyAsMultiStatus(resp);
            responses = multistatus.getResponses();
        } catch (DavException e) {
            // TODO Auto-generated catch block
            log.error("propfind", e);
        }
        return responses;
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
