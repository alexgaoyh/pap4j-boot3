package cn.net.pap.gstore;

import jgsc.GstoreConnector;

public class GStoreTest {

    public static final String ip = "192.168.1.115";

    public static final Integer port = 9001;

    public static final String dbName = "kuangbiao";
    public static final String username = "root";
    public static final String password = "123456";

    // @Test
    public void queryAll() {
        GstoreConnector gc = new GstoreConnector(ip, port);

        gc.load(dbName, username, password);
        String res = gc.query(username, password, dbName, "SELECT * WHERE { ?s ?p ?o }");
        gc.unload(dbName, username, password);

        System.out.println(res);
    }
}
